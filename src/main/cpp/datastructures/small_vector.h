#pragma once

#include <cstdint>
#include <cstddef>
#include <memory>
#include <stdexcept>
#include <type_traits>
#include <utility>
#include <span>

namespace fk {

    // ========================================================================
    // SmallVector
    // A highly optimized vector that strictly avoids heap allocations by 
    // storing up to <InlineCapacity> elements directly inside its own 
    // class footprint on the stack.
    // 
    // If the capacity is exceeded, it gracefully degrades into a dynamically 
    // allocated heap vector. Ideal for performance critical paths where 
    // 95% of vectors are tiny (like collision results or adjacency lists).
    // ========================================================================
    template <typename T, size_t InlineCapacity = 16>
    class SmallVector final {
    public:
        using value_type = T;
        using size_type = size_t;
        using reference = T&;
        using const_reference = const T&;

        SmallVector() noexcept = default;

        ~SmallVector() {
            destroy_and_free();
        }

        // --- Disable Copy Semantics temporarily for safety ---
        // (One can implement copying later, but usually SmallVectors are moved or passed by ref)
        SmallVector(const SmallVector&) = delete;
        SmallVector& operator=(const SmallVector&) = delete;

        // Move Constructor
        SmallVector(SmallVector&& other) noexcept {
            move_from(std::move(other));
        }

        // Move Assignment
        SmallVector& operator=(SmallVector&& other) noexcept {
            if (this != &other) {
                destroy_and_free();
                move_from(std::move(other));
            }
            return *this;
        }

        // --- Core Size API ---

        size_type size() const noexcept { return m_size; }
        bool empty() const noexcept { return m_size == 0; }
        size_type capacity() const noexcept { return m_capacity; }

        bool is_heap_allocated() const noexcept { return m_capacity > InlineCapacity; }

        // --- Data Access API ---

        T* data() noexcept { return is_heap_allocated() ? m_heap_data : inline_data_ptr(); }
        const T* data() const noexcept { return is_heap_allocated() ? m_heap_data : inline_data_ptr(); }

        reference operator[](size_type index) noexcept { return data()[index]; }
        const_reference operator[](size_type index) const noexcept { return data()[index]; }

        reference at(size_type index) {
            if (index >= m_size) throw std::out_of_range("SmallVector index out of bounds");
            return data()[index];
        }

        const_reference at(size_type index) const {
            if (index >= m_size) throw std::out_of_range("SmallVector index out of bounds");
            return data()[index];
        }

        // --- Spans and Iterators ---

        operator std::span<T>() noexcept { return std::span<T>(data(), size()); }
        operator std::span<const T>() const noexcept { return std::span<const T>(data(), size()); }

        T* begin() noexcept { return data(); }
        T* end() noexcept { return data() + m_size; }
        const T* begin() const noexcept { return data(); }
        const T* end() const noexcept { return data() + m_size; }

        // ====================================================================
        // Push & Emplace
        // ====================================================================

        template <typename... Args>
        reference emplace_back(Args&&... args) {
            if (m_size == m_capacity) {
                return grow_and_emplace_back(std::forward<Args>(args)...);
            }

            T* target_ptr = data() + m_size;

            // Placement new: Construct object directly into raw memory
            new (target_ptr) T(std::forward<Args>(args)...);

            m_size++;
            return *target_ptr;
        }

        void push_back(const T& value) { emplace_back(value); }
        void push_back(T&& value) { emplace_back(std::move(value)); }

        void pop_back() noexcept {
            if (m_size > 0) {
                m_size--;
                // Explicitly call the destructor for the removed element
                data()[m_size].~T();
            }
        }

        void clear() noexcept {
            T* elements = data();
            for (size_type i = 0; i < m_size; ++i) {
                elements[i].~T();
            }
            m_size = 0;
            // Note: clear() emphatically does NOT release the heap allocation!
        }

    private:
        size_type m_size     = 0;
        size_type m_capacity = InlineCapacity;

        // Raw uninitialized memory inside the class footprint
        alignas(T) std::byte m_inline_storage[InlineCapacity * sizeof(T)];

        // Pointer to dynamic memory if the inline storage overflows
        T* m_heap_data = nullptr;

        T* inline_data_ptr() noexcept { 
            return reinterpret_cast<T*>(m_inline_storage); 
        }

        const T* inline_data_ptr() const noexcept { 
            return reinterpret_cast<const T*>(m_inline_storage); 
        }

        // ====================================================================
        // Core Internal Mechanics
        // ====================================================================

        // Grows the buffer and appends a new element, with the strong exception guarantee.
        //
        // The new element is constructed FIRST, while the old elements are still alive:
        // the arguments may refer to an element of this vector (e.g. v.push_back(v[0])).
        // The old elements are only destroyed after everything was relocated successfully,
        // so any exception leaves the vector unchanged.
        template <typename... Args>
        reference grow_and_emplace_back(Args&&... args) {
            const size_type new_capacity = (m_capacity == 0) ? 1 : m_capacity * 2;

            // Standard malloc/new fallback for heap allocation
            T* new_heap_data = static_cast<T*>(::operator new(new_capacity * sizeof(T), std::align_val_t(alignof(T))));

            // 1. Construct the new element at its final position.
            try {
                new (new_heap_data + m_size) T(std::forward<Args>(args)...);
            } catch (...) {
                ::operator delete(new_heap_data, std::align_val_t(alignof(T)));
                throw;
            }

            // 2. Relocate the old elements (move if that cannot throw, copy otherwise).
            T* old_data = data();
            size_type relocated = 0;
            try {
                for (; relocated < m_size; ++relocated) {
                    new (new_heap_data + relocated) T(std::move_if_noexcept(old_data[relocated]));
                }
            } catch (...) {
                for (size_type i = 0; i < relocated; ++i) {
                    new_heap_data[i].~T();
                }
                new_heap_data[m_size].~T();
                ::operator delete(new_heap_data, std::align_val_t(alignof(T)));
                throw;
            }

            // 3. Nothing can throw from here on: destroy the old elements and switch buffers.
            for (size_type i = 0; i < m_size; ++i) {
                old_data[i].~T();
            }

            // Free the old array ONLY if it was previously dynamically allocated
            // (we obviously can't 'delete' the inline storage buffer)
            if (is_heap_allocated()) {
                ::operator delete(m_heap_data, std::align_val_t(alignof(T)));
            }

            m_heap_data = new_heap_data;
            m_capacity = new_capacity;
            return new_heap_data[m_size++];
        }

        void destroy_and_free() noexcept {
            clear(); // Calls destructors on all live objects
            if (is_heap_allocated()) {
                ::operator delete(m_heap_data, std::align_val_t(alignof(T)));
            }
        }

        void move_from(SmallVector&& other) noexcept {
            m_size = other.m_size;
            m_capacity = other.m_capacity;

            if (other.is_heap_allocated()) {
                // Steal the heap pointer
                m_heap_data = other.m_heap_data;
                other.m_heap_data = nullptr;
            } else {
                // If the other vector was inline, we must aggressively MOVE its objects 
                // into OUR inline storage, because we cannot steal pointers to its stack frame!
                T* target_data = inline_data_ptr();
                T* source_data = other.inline_data_ptr();

                for (size_type i = 0; i < m_size; ++i) {
                    new (target_data + i) T(std::move(source_data[i]));
                    source_data[i].~T();
                }
            }

            other.m_size = 0;
            other.m_capacity = InlineCapacity;
        }
    };

} // namespace fk
