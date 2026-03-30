#pragma once

#include <vector>
#include <stdexcept>
#include <optional>
#include <cstdint>
#include <cassert>

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
    // ========================================================================
    template <typename T>
    class RingBuffer final : private std::vector<T> {
    private:
        using BaseClass = std::vector<T>;

        size_t m_head = 0;   // Write index
        size_t m_tail = 0;   // Read index
        size_t m_count = 0;  // Amount of currently valid items

    public:
        // Expose strict base capabilities
        using BaseClass::capacity;

        // Constructor fundamentally dictates the unchangeable capacity
        explicit RingBuffer(size_t fixed_capacity) {
            if (fixed_capacity == 0) {
                throw std::invalid_argument("RingBuffer must have a capacity > 0");
            }
            BaseClass::resize(fixed_capacity); // Pre-alloctate and default construct T
        }

        // --- Core Size API ---
        size_t size() const noexcept { return m_count; }
        bool empty() const noexcept { return m_count == 0; }
        bool full() const noexcept { return m_count == BaseClass::capacity(); }

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
        bool push(U&& item, bool force_overwrite = false) noexcept {
            if (full()) {
                if (!force_overwrite) return false; // Reject

                // Overwrite behavior: We push new data causing the oldest data 
                // to be lost. Thus, the tail (read-pointer) must be pushed forward.
                m_tail = (m_tail + 1) % BaseClass::capacity();
            } else {
                m_count++; // We only grow in size if we weren't full already
            }

            BaseClass::operator[](m_head) = std::forward<U>(item);
            m_head = (m_head + 1) % BaseClass::capacity();

            return true;
        }

        template <typename... Args>
        bool emplace(bool force_overwrite, Args&&... args) noexcept {
            if (full()) {
                if (!force_overwrite) return false;
                m_tail = (m_tail + 1) % BaseClass::capacity();
            } else {
                m_count++;
            }

            // In-place reassignment
            BaseClass::operator[](m_head) = T(std::forward<Args>(args)...);
            m_head = (m_head + 1) % BaseClass::capacity();

            return true;
        }

        // ====================================================================
        // Pop (O(1))
        // Extracts the oldest element (at the tail) and removes it from the queue.
        // Returns std::nullopt if the buffer is empty.
        // ====================================================================
        std::optional<T> pop() noexcept {
            if (empty()) return std::nullopt;

            // Move the value out of the array. The slot remains physically 
            // initialized with a "moved-from" state of T until overwritten.
            T extracted = std::move(BaseClass::operator[](m_tail));

            m_tail = (m_tail + 1) % BaseClass::capacity();
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
