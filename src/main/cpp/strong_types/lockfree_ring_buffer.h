#pragma once

#include <atomic>
#include <memory>
#include <optional>
#include <cstdint>
#include <cstddef>
#include <stdexcept>
#include <type_traits>

namespace fk {

    // ========================================================================
    // LockFreeRingBuffer (MPMC Bounded Queue)
    // A 100% lock-free, thread-safe Circular Buffer based on Dmitry Vyukov's
    // famous MPMC algorithm. Multiple threads can safely push and pop 
    // simultaneously without ANY mutexes or OS kernel locks.
    // 
    // IMPORTANT: The capacity MUST be a strictly fixed power of 2!
    // ========================================================================
    template <typename T>
    class LockFreeRingBuffer final {
        
        // ====================================================================
        // CRITICAL SAFETY GATES (Deadlock Prevention)
        // ====================================================================
        // If extracting or destroying an item throws an exception inside pop(), 
        // the atomic sequence counter would never advance, permanently chaining
        // and deadlocking that specific hardware queue slot forever.
        // Lock-free data structures absolutely demand nothrow compliance!
        static_assert(std::is_nothrow_move_constructible_v<T>, "T must be strictly nothrow move constructible!");
        static_assert(std::is_nothrow_destructible_v<T>, "T must be strictly nothrow destructible!");

    private:
        // Hardware cache line size (Universally 64 bytes on x86_64 / ARM64).
        static constexpr size_t CACHE_LINE = 64;

        // Represents the exact mathematical distance between sequence numbers
        using diff_t = std::make_signed_t<size_t>;

        // ====================================================================
        // THE CELL PADDING (Exterminates False-Sharing)
        // ====================================================================
        // By aligning EACH INDIVIDUAL CELL to the exact CPU cache line boundary,
        // we guarantee that adjacent Cells sit on completely different hardware 
        // cache lines. C++ standard [expr.sizeof] guarantees that the array element 
        // size will rigidly be padded to a perfect multiple of CACHE_LINE.
        // This physically eradicates "False Sharing" when Producer A writes to 
        // Cell 0, while Producer B simultaneously writes to Cell 1.
        struct alignas(CACHE_LINE) Cell {
            std::atomic<size_t> sequence;
            std::optional<T> data;
        };

        // Mathematical validation of the C++ compiler's layout algorithm
        static_assert(sizeof(Cell) % CACHE_LINE == 0, "Compiler failed to pad Cell to cache boundary!");

        // --- GROUP 1: Read-Only Data (Shared safely among all cores) ---
        // unique_ptr automatically sizes and aligns the raw contiguous C-array 
        // strictly according to the alignas() rules of the Cell struct.
        std::unique_ptr<Cell[]> m_buffer;
        size_t m_buffer_mask;

        // --- GROUP 2: Producer State ---
        // Isolated, heavily written index pointer to prevent cross-core invalidations.
        alignas(CACHE_LINE) std::atomic<size_t> m_head; 

        // --- GROUP 3: Consumer State ---
        // Fully isolated from Producer state physically in the L1-D / L2 Cache!
        alignas(CACHE_LINE) std::atomic<size_t> m_tail; 

    public:
        explicit LockFreeRingBuffer(size_t capacity) {
            // Validate Power of 2 requirements for bitwise operations
            if (capacity < 2 || (capacity & (capacity - 1)) != 0) {
                throw std::invalid_argument("Capacity must be a power of 2!");
            }

            m_buffer_mask = capacity - 1;
            
            // C++17/20 automatically calls 'new align_val_t' safely prioritizing the Cell alignment 
            m_buffer = std::make_unique<Cell[]>(capacity);
            
            // Initialize the sequence trackers strictly for every cell.
            for (size_t i = 0; i < capacity; ++i) {
                m_buffer[i].sequence.store(i, std::memory_order_relaxed);
            }

            m_head.store(0, std::memory_order_relaxed);
            m_tail.store(0, std::memory_order_relaxed);
        }

        // Clean-Up is fully automated and fundamentally thread-safe when the owner destroys the queue.
        ~LockFreeRingBuffer() = default; 

        // Strictly disallow copying and moving to uphold structural threading guarantees
        LockFreeRingBuffer(const LockFreeRingBuffer&) = delete;
        LockFreeRingBuffer& operator=(const LockFreeRingBuffer&) = delete;

        // ====================================================================
        // Push (Producer API)
        // Returns false if the queue is completely full and cannot accept data.
        // ====================================================================
        template <typename U>
        bool push(U&& item) noexcept {
            // Assert at compile time if U's conversion to T is prone to throwing exceptions!
            static_assert(std::is_nothrow_constructible_v<T, U&&>, 
                "Pushing logic must be noexcept to protect queue lifecycle consistency.");

            Cell* cell = nullptr;
            size_t pos = m_head.load(std::memory_order_relaxed);

            while (true) {
                cell = &m_buffer[pos & m_buffer_mask];
                
                // Acquire syncs with Release in pop() to ensure we read the utmost latest sequence.
                size_t seq = cell->sequence.load(std::memory_order_acquire);
                
                // FIX: Evaluate subtraction first as unsigned (natural modulo wrap-around), 
                // THEN cast to signed integer diff_t. This is immune to architecture overflows 
                // even if the server runs relentlessly for 500 years.
                diff_t diff = static_cast<diff_t>(seq - pos);

                if (diff == 0) {
                    // Spot Claim: Target is empty and perfectly synchronized with our Ticket.
                    // If CAS fails, 'pos' is naturally overwritten with the aggressive new head by hardware.
                    if (m_head.compare_exchange_weak(pos, pos + 1, std::memory_order_relaxed)) {
                        break; 
                    }
                } 
                else if (diff < 0) {
                    // Sequence lags behind Head = The ring's cycle is full!
                    return false; 
                } 
                else {
                    // Another producer thread overtook us. Sync to reality.
                    pos = m_head.load(std::memory_order_relaxed);
                }
            }
            
            // EXCLUSIVELY OWNING CELL: We now operate fundamentally thread-safe without locks.
            cell->data.emplace(std::forward<U>(item));

            // Publish to consumers. Release order mathematically guarantees our heavy data write 
            // inside 'm_data' completes BEFORE the sequence flag opens for opposing consumer threads.
            cell->sequence.store(pos + 1, std::memory_order_release);
            return true;
        }

        // ====================================================================
        // Pop (Consumer API)
        // Returns an empty std::optional if the queue is fully empty.
        // ====================================================================
        std::optional<T> pop() noexcept {
            Cell* cell = nullptr;
            size_t pos = m_tail.load(std::memory_order_relaxed);

            while (true) {
                cell = &m_buffer[pos & m_buffer_mask];

                // Acquire syncs strictly with Release in push()
                size_t seq = cell->sequence.load(std::memory_order_acquire);
                
                // Consumers rigidly expect the sequence to be exactly (pos + 1)
                diff_t diff = static_cast<diff_t>(seq - (pos + 1));

                if (diff == 0) {
                    // Claim sequence verified!
                    if (m_tail.compare_exchange_weak(pos, pos + 1, std::memory_order_relaxed)) {
                        break; 
                    }
                } 
                else if (diff < 0) {
                    // Sequence lags behind Tail = The queue is dry/empty.
                    return std::nullopt; 
                } 
                else {
                    // Another consumer stripped the targeted slot. Catch-up time!
                    pos = m_tail.load(std::memory_order_relaxed);
                }
            }
            
            // EXCLUSIVELY OWNING CELL
            // Thanks to static_assert, this std::move is strictly guaranteed to never throw.
            T extracted = std::move(*(cell->data));
            cell->data.reset(); // Properly routes destructor implicitly, eliminating memory leaks

            // Publish to producers that this cell is wiped and ready for the next rotation.
            // Pos + Mask + 1 equates precisely to the ticket sequence a producer 
            // naturally aims for when it eventually loops around the ring to this exact slot again.
            cell->sequence.store(pos + m_buffer_mask + 1, std::memory_order_release);
            return std::optional<T>(std::move(extracted));
        }

        // Extremely rough approximation (as active thread-swarms constantly modify head/tail async)
        size_t approximate_size() const noexcept {
            size_t head = m_head.load(std::memory_order_relaxed);
            size_t tail = m_tail.load(std::memory_order_relaxed);
            return (head >= tail) ? (head - tail) : 0;
        }
    };

} // namespace fk
