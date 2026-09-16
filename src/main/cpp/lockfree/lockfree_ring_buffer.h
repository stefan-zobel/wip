#pragma once

#include <atomic>
#include <memory>
#include <optional>
#include <cstdint>
#include <cstddef>
#include <stdexcept>
#include <type_traits>
#include <utility>

namespace fk {

    // ========================================================================
    // LockFreeRingBuffer (MPMC Bounded Queue)
    // A bounded multi-producer/multi-consumer queue based on Dmitry Vyukov's
    // MPMC algorithm. push() and pop() use only atomic operations, no mutexes
    // or system calls.
    //
    // Not strictly lock-free: a producer or consumer that is suspended between
    // its CAS and publishing the cell makes other consumers report "empty"
    // (or producers report "full") until it resumes, although other items may
    // be waiting behind that cell.
    //
    // The capacity must be a power of 2 (at least 2).
    // ========================================================================
    template <typename T>
    class LockFreeRingBuffer final {

        // ====================================================================
        // Nothrow requirements
        // ====================================================================
        // A cell is claimed (CAS) before its data is moved in or out, and published
        // afterwards. If that move or the destructor threw, the cell would stay
        // claimed forever and block the queue at that position.
        static_assert(std::is_nothrow_move_constructible_v<T>, "T must be nothrow move constructible");
        static_assert(std::is_nothrow_destructible_v<T>, "T must be nothrow destructible");

    private:
        // Hardware cache line size. std::hardware_destructive_interference_size is avoided on
        // purpose: g++ warns about it (-Winterference-size) because its value is not ABI-stable.
#if defined(__APPLE__) && defined(__aarch64__)
        static constexpr size_t CACHE_LINE = 128; // Apple Silicon uses 128-byte cache lines
#else
        static constexpr size_t CACHE_LINE = 64;  // x86_64 and most ARM64 cores
#endif

        // An alignas() weaker than the natural alignment of Cell would be ill-formed,
        // so over-aligned element types keep their own (stricter) alignment.
        static constexpr size_t CELL_ALIGN = alignof(T) > CACHE_LINE ? alignof(T) : CACHE_LINE;

        // Signed distance between a cell's sequence number and a position
        using diff_t = std::make_signed_t<size_t>;

        // ====================================================================
        // Cell layout
        // ====================================================================
        // Each cell is aligned to CELL_ALIGN, so the array element size is a multiple
        // of it ([expr.sizeof]) and neighboring cells never share a cache line. This
        // avoids false sharing when two threads work on adjacent cells, at the cost of
        // at least CACHE_LINE bytes per cell.
        struct alignas(CELL_ALIGN) Cell {
            std::atomic<size_t> sequence;
            std::optional<T> data;
        };

        static_assert(sizeof(Cell) % CELL_ALIGN == 0, "Cell size must be a multiple of its alignment");

        // --- Shared, read-only after construction ---
        // make_unique<Cell[]> uses the aligned operator new[] for the over-aligned Cell.
        std::unique_ptr<Cell[]> m_buffer;
        size_t m_buffer_mask;

        // --- Producer position ---
        // On its own cache line, so producer CASes do not invalidate the lines above.
        alignas(CACHE_LINE) std::atomic<size_t> m_head;

        // --- Consumer position ---
        // Separated from m_head, so producers and consumers do not share a cache line.
        alignas(CACHE_LINE) std::atomic<size_t> m_tail;

    public:
        explicit LockFreeRingBuffer(size_t capacity) {
            // Positions are mapped to cells with a bit mask
            if (capacity < 2 || (capacity & (capacity - 1)) != 0) {
                throw std::invalid_argument("Capacity must be a power of 2!");
            }

            m_buffer_mask = capacity - 1;

            m_buffer = std::make_unique<Cell[]>(capacity);

            // Cell i initially waits for the producer at position i.
            for (size_t i = 0; i < capacity; ++i) {
                m_buffer[i].sequence.store(i, std::memory_order_relaxed);
            }

            m_head.store(0, std::memory_order_relaxed);
            m_tail.store(0, std::memory_order_relaxed);
        }

        // Remaining items are destroyed together with the cells.
        // Like any destructor, it must not run while other threads still push or pop.
        ~LockFreeRingBuffer() = default;

        // Not copyable (and therefore not movable either)
        LockFreeRingBuffer(const LockFreeRingBuffer&) = delete;
        LockFreeRingBuffer& operator=(const LockFreeRingBuffer&) = delete;

        // ====================================================================
        // Push (Producer API)
        // Returns false if the queue is full.
        // ====================================================================
        template <typename U>
        bool push(U&& item) noexcept {
            // Constructing T must not throw once the cell is claimed (see the static_asserts above).
            static_assert(std::is_nothrow_constructible_v<T, U&&>,
                "T must be nothrow constructible from the pushed argument");

            Cell* cell = nullptr;
            size_t pos = m_head.load(std::memory_order_relaxed);

            while (true) {
                cell = &m_buffer[pos & m_buffer_mask];

                // Every std::atomic operation is sequentially consistent, whatever memory_order is
                // passed; acquire/release are mere optimization hints without semantics.
                size_t seq = cell->sequence.load(std::memory_order_acquire);

                // Subtract as unsigned (well-defined wrap-around), then convert to signed:
                // the result is correct even after the counters wrap.
                diff_t diff = static_cast<diff_t>(seq - pos);

                if (diff == 0) {
                    // The cell is free for position 'pos': try to claim the position.
                    // On failure, compare_exchange_weak stores the current head in 'pos'.
                    if (m_head.compare_exchange_weak(pos, pos + 1, std::memory_order_relaxed)) {
                        break;
                    }
                }
                else if (diff < 0) {
                    // The cell still holds an item from the previous round: the queue is full.
                    return false;
                }
                else {
                    // Another producer has already taken this position: retry with the current head.
                    pos = m_head.load(std::memory_order_relaxed);
                }
            }

            // The cell is claimed by this thread; no other thread accesses its data now.
            cell->data.emplace(std::forward<U>(item));

            // Publish to consumers. Consumers only claim a cell whose sequence is pos + 1, and the
            // sequence is stored AFTER 'data' has been written, so no consumer can see a half-written
            // cell. (memory_order_release is only a hint and plays no part in this.)
            cell->sequence.store(pos + 1, std::memory_order_release);
            return true;
        }

        // ====================================================================
        // Pop (Consumer API)
        // Returns an empty std::optional if the queue is empty.
        // ====================================================================
        std::optional<T> pop() noexcept {
            // Every path returns this one variable, so compilers apply NRVO (a second
            // return expression such as std::nullopt would disable it and cost a move).
            std::optional<T> extracted;
            Cell* cell = nullptr;
            size_t pos = m_tail.load(std::memory_order_relaxed);

            while (true) {
                cell = &m_buffer[pos & m_buffer_mask];

                // Sequentially consistent load (memory_order_acquire is only a hint, see push())
                size_t seq = cell->sequence.load(std::memory_order_acquire);

                // A published item at position 'pos' has sequence pos + 1
                diff_t diff = static_cast<diff_t>(seq - (pos + 1));

                if (diff == 0) {
                    // The cell holds the item for position 'pos': try to claim the position.
                    if (m_tail.compare_exchange_weak(pos, pos + 1, std::memory_order_relaxed)) {
                        break;
                    }
                }
                else if (diff < 0) {
                    // No published item at this position: the queue is empty.
                    return extracted; // empty
                }
                else {
                    // Another consumer has already taken this position: retry with the current tail.
                    pos = m_tail.load(std::memory_order_relaxed);
                }
            }

            // The cell is claimed by this thread. The move cannot throw (see the static_asserts).
            // The value is moved exactly once; 'extracted' is returned via NRVO.
            extracted.emplace(std::move(*cell->data));
            cell->data.reset(); // Destroys the moved-from value

            // Make the cell free again: pos + capacity is the position at which the
            // producers reach this cell in the next round.
            cell->sequence.store(pos + m_buffer_mask + 1, std::memory_order_release);
            return extracted;
        }

        // Snapshot of head - tail, only approximate while other threads push or pop.
        // Operations in progress (claimed but not yet published) are counted as well.
        size_t approximate_size() const noexcept {
            size_t head = m_head.load(std::memory_order_relaxed);
            size_t tail = m_tail.load(std::memory_order_relaxed);
            return (head >= tail) ? (head - tail) : 0;
        }
    };

} // namespace fk
