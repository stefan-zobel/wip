#pragma once

#include <vector>
#include <stdexcept>
#include <optional>
#include <cstdint>
#include <cassert>
#include <type_traits>
#include <utility>

namespace fk {

    // ========================================================================
    // RingBuffer (Circular Queue)
    // A fixed-capacity, pre-allocated FIFO (First-In-First-Out) queue.
    // It utilizes Private Inheritance over std::vector to guarantee 100% 
    // contiguous cache-locality without ANY dynamic memory allocations 
    // during push() or pop() operations.
    //
    // Behavior on Full: The user can choose to either OVERWRITE old data 
    // or REJECT the new data (via the 'push_overwrite' boolean flag).
    //
    // Exceptions: push/emplace/pop are noexcept only if the operations on T they use are. If T
    // throws, the cursors are unchanged (the element is assigned or moved before they move).
    // ========================================================================
    template <typename T>
    class RingBuffer final : private std::vector<T> {
    private:
        using BaseClass = std::vector<T>;

        size_t m_head = 0;   // Write index
        size_t m_tail = 0;   // Read index
        size_t m_count = 0;  // Amount of currently valid items
        size_t m_capacity = 0;  // The requested capacity (std::vector::capacity() may be larger)

    public:
        size_t capacity() const noexcept { return m_capacity; }

        // Constructor fundamentally dictates the unchangeable capacity
        explicit RingBuffer(size_t fixed_capacity) {
            if (fixed_capacity == 0) {
                throw std::invalid_argument("RingBuffer must have a capacity > 0");
            }
            BaseClass::resize(fixed_capacity); // Pre-alloctate and default construct T
            m_capacity = fixed_capacity;
        }

        // --- Core Size API ---
        size_t size() const noexcept { return m_count; }
        bool empty() const noexcept { return m_count == 0; }
        bool full() const noexcept { return m_count == m_capacity; }

        void clear() noexcept {
            // We do NOT clear the underlying vector (as that shrinks/destroys nodes).
            // We just reset the read/write cursors to effectively drop all references.
            m_head = 0;
            m_tail = 0;
            m_count = 0;
        }

        // ====================================================================
        // Push (O(1))
        // Inserts an element at the head. 
        // If 'force_overwrite' is true and the buffer is full, it silently 
        // overrides the oldest unread element (Tail moves forward). 
        // If false, it simply rejects the insert and returns false.
        // ====================================================================
        template <typename U>
        bool push(U&& item, bool force_overwrite = false) noexcept(std::is_nothrow_assignable_v<T&, U&&>) {
            const bool was_full = full();
            if (was_full && !force_overwrite) return false; // Reject

            // Assign first: if the assignment throws, the cursors are unchanged
            BaseClass::operator[](m_head) = std::forward<U>(item);
            m_head = (m_head + 1) % m_capacity;

            if (was_full) {
                // Overwrite behavior: We push new data causing the oldest data 
                // to be lost. Thus, the tail (read-pointer) must be pushed forward.
                m_tail = (m_tail + 1) % m_capacity;
            } else {
                m_count++; // We only grow in size if we weren't full already
            }

            return true;
        }

        template <typename... Args>
        bool emplace(bool force_overwrite, Args&&... args)
            noexcept(std::is_nothrow_constructible_v<T, Args&&...> && std::is_nothrow_move_assignable_v<T>) {
            if (full() && !force_overwrite) return false;

            // Construct first, then move-assign into the slot (see push)
            return push(T(std::forward<Args>(args)...), force_overwrite);
        }

        // ====================================================================
        // Pop (O(1))
        // Extracts the oldest element (at the tail) and removes it from the queue.
        // Returns std::nullopt if the buffer is empty.
        // ====================================================================
        std::optional<T> pop() noexcept(std::is_nothrow_move_constructible_v<T>) {
            if (empty()) return std::nullopt;

            // Move the value out of the array. The slot remains physically 
            // initialized with a "moved-from" state of T until overwritten.
            // If the move throws, the element stays in the buffer.
            std::optional<T> extracted(std::in_place, std::move(BaseClass::operator[](m_tail)));

            m_tail = (m_tail + 1) % m_capacity;
            m_count--;

            return extracted;
        }

        // ====================================================================
        // Peek (O(1))
        // Looks at the oldest element without removing it.
        // ====================================================================
        T* peek() noexcept {
            if (empty()) return nullptr;
            return &BaseClass::operator[](m_tail);
        }

        const T* peek() const noexcept {
            if (empty()) return nullptr;
            return &BaseClass::operator[](m_tail);
        }
    };

} // namespace fk
