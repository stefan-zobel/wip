/*
 * Copyright 2026 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <cstdint>
#include <limits>
#include <cassert>
#include <array>
#include <concepts>

// a "type-wrapper" to return types as values
template<typename T>
struct type_tag { using type = T; };


// constexpr function for the selection of the smallest index type
template<size_t MaxSize>
constexpr auto get_smallest_index_type() {
    // +1 because we are using 0 as the index of the "null" object
    if constexpr (MaxSize + 1 <= std::numeric_limits<uint8_t>::max()) {
        return type_tag<uint8_t>{};
    }
    else if constexpr (MaxSize + 1 <= std::numeric_limits<uint16_t>::max()) {
        return type_tag<uint16_t>{};
    }
    else {
        return type_tag<uint32_t>{};
    }
}

// alias for the deduced smallest possible type
template<size_t MaxSize>
using SmallestIndexType = typename decltype(get_smallest_index_type<MaxSize>())::type;


template<size_t Capacity>
struct Link {
    using Idx = SmallestIndexType<Capacity>;

    static constexpr Idx NilIdx = 0; // std::numeric_limits<Idx>::max();

    Idx next = NilIdx;
    Idx prev = NilIdx;
};


template<typename T, size_t Cap>
struct Mixin : T {
    constexpr Mixin() = default;
    Link<Cap> link{};

    // Forwarding constructor for T
    template<typename... Args>
    constexpr Mixin(Args&&... args) : T{ std::forward<Args>(args)... } {}
};




template<typename T, std::size_t Cap>
class IntrusiveList {
public:
    using NodeType = Mixin<T, Cap>;
    using IndexType = typename Link<Cap>::Idx;
    static constexpr IndexType Nil = Link<Cap>::NilIdx;

private:
    // must value-init for constexpr!
    std::array<NodeType, Cap + 1> storage{};
    IndexType head = Nil;
    IndexType tail = Nil;
    IndexType free_head = Nil; // start of the freelist
    IndexType count = 0;

public:
    constexpr IntrusiveList() {
        clear();
    }

    constexpr IndexType push_front(T item) {
        IndexType idx = allocate_from_free_list();
        if (idx == Nil) return Nil; // List is full

        // Move or copy the item into the slot
        static_cast<T&>(storage[idx]) = std::move(item);

        // Logical insert
        storage[idx].link.next = head;
        storage[idx].link.prev = Nil;

        if (head != Nil) {
            storage[head].link.prev = idx;
        }
        else {
            tail = idx;
        }
        head = idx;

        count++;
        return idx;
    }

    constexpr IndexType push_back(T item) {
        IndexType idx = allocate_from_free_list();
        if (idx == Nil) return Nil; // List is full

        static_cast<T&>(storage[idx]) = std::move(item);

        storage[idx].link.next = Nil;
        storage[idx].link.prev = tail;

        if (tail != Nil) {
            storage[tail].link.next = idx;
        }
        else {
            head = idx;
        }
        tail = idx;

        count++;
        return idx;
    }

    constexpr void remove(IndexType idx) {
        if (idx == Nil || idx > Cap) return;

        // Unlink it from the logical list
        IndexType prev = storage[idx].link.prev;
        IndexType next = storage[idx].link.next;

        if (prev != Nil) {
            storage[prev].link.next = next;
        }
        else {
            head = next;
        }

        if (next != Nil) {
            storage[next].link.prev = prev;
        }
        else {
            tail = prev;
        }

        // we don't want 'zombies' hanging around
        static_cast<T&>(storage[idx]) = T{};

        // Return the slot to the freelist
        release_to_free_list(idx);
        count--;
    }

    // Random access

    // Read access via index (const)
    constexpr const T& operator[](IndexType idx) const {
        return read_access(idx);
    }

    // Write access via index (non-const)
    constexpr T& operator[](IndexType idx) {
        return write_access(idx);
    }

    constexpr std::size_t size() const { return static_cast<std::size_t>(count); }
    constexpr bool isFull() const { return count == Cap; }

    // Remove all elements and rebuild the freelist
    constexpr void clear() {
        // For the case that T has a destructor that is
        // actually doing something (like unique_ptr):
        for (IndexType i = 1; i <= Cap; ++i) {
            // Assign the default state to the item, so
            // that its destructor gets triggered.
            static_cast<T&>(storage[i]) = T{};
        }
        head = Nil;
        tail = Nil;
        free_head = Nil;
        count = 0;
        // loop backwards so that index 1 is the last one that gets
        // pushed on the stack and the first one to be popped off
        for (std::size_t i = Cap; i > 0; --i) {
            storage[i].link.next = free_head;
            free_head = static_cast<IndexType>(i);
        }
    }
    // remove the first element
    constexpr void pop_front() {
        remove(head);
    }

    // remove the last element
    constexpr void pop_back() {
        remove(tail);
    }

    // --- Iterator defintions (only const_iterator for now) ---

    // Common basis for iterator and const_iterator
    template<bool IsConst>
    struct IteratorImpl {
        using iterator_category = std::forward_iterator_tag;
        using value_type = std::conditional_t<IsConst, const T, T>;
        using reference = value_type&;
        using pointer = value_type*;

        using IndexType = typename Link<Cap>::Idx;

        // The return type for structured bindings
        using entry_type = std::pair<IndexType, reference>;

        IndexType current;
        std::conditional_t<IsConst, const IntrusiveList*, IntrusiveList*> list;

        // Important: Return by value since the Pair is a temporary
        constexpr auto operator*() const {
            if constexpr (IsConst) {
                return std::pair<IndexType, value_type&>{
                    current,
                        static_cast<value_type&>(list->read_access(current))
                };
            }
            else {
                return std::pair<IndexType, value_type&>{
                    current,
                        static_cast<value_type&>(list->write_access(current))
                };
            }
        }

        // For algorithms like std::find_if
        // (algorithms mostly expect the pure item, not a std::pair)
        constexpr value_type* operator->() const {
            if constexpr (IsConst) {
                return &static_cast<value_type&>(list->read_access(current));
            }
            else {
                return &static_cast<value_type&>(list->write_access(current));
            }
        }

        constexpr IteratorImpl& operator++() {
            if (current != Nil) current = list->storage[current].link.next;
            return *this;
        }

        constexpr bool operator==(const IteratorImpl& other) const { return current == other.current; }
        constexpr bool operator!=(const IteratorImpl& other) const { return !(*this == other); }
    };

    // --- aliases for the iterator types and begin / end functions ---

    using const_iterator = IteratorImpl<true>;
//    using iterator = IteratorImpl<false>;

//    constexpr iterator begin() { return { head, this }; }
//    constexpr iterator end() { return { Nil, this }; }

    constexpr const_iterator begin() const { return { head, this }; }
    constexpr const_iterator end()   const { return { Nil, this }; }
    constexpr const_iterator cbegin() const { return { head, this }; }
    constexpr const_iterator cend()   const { return { Nil, this }; }

    constexpr IndexType insert(IndexType pos, T item) {
        // sanity check: is the index legal?
        if (!is_valid_internal(pos)) {
            if (std::is_constant_evaluated()) {
                throw "Insert with out-of-bounds index!";
            }
            else {
                // Debug build
                assert(false && "Insert with out-of-bounds index!");
                // Release build (do nothing, just return Nil)
                return Nil;
            }
        }

        // secial cases (Nil is legal here as it means 'end')
        if (pos == head) return push_front(std::move(item));
        if (pos == Nil)  return push_back(std::move(item)); // behavior of std::list at end()

        // the usual case
        IndexType idx = allocate_from_free_list();
        if (idx == Nil) return Nil; // List is full

        static_cast<T&>(storage[idx]) = std::move(item);

        // we now know that pos is valid and != head/Nil,
        // so access to storage[pos] is safe here
        IndexType prevIdx = storage[pos].link.prev;

        storage[idx].link.next = pos;
        storage[idx].link.prev = prevIdx;
        storage[pos].link.prev = idx;

        if (prevIdx != Nil) {
            storage[prevIdx].link.next = idx;
        }
        else {
            head = idx;
        }

        count++;
        return idx;
    }

    constexpr IndexType insert(const_iterator it, T item) {
        return insert(it.current, std::move(item));
    }

    // Access to front and back
    constexpr T& front() { return write_access(head); }
    constexpr T& back() { return write_access(tail); }

    constexpr const T& front() const { return read_access(head); }
    constexpr const T& back() const { return read_access(tail); }

    constexpr IndexType next(IndexType idx) const { return (idx == Nil) ? Nil : storage[idx].link.next; }
    constexpr IndexType prev(IndexType idx) const { return (idx == Nil) ? Nil : storage[idx].link.prev; }

    /**
     * Find the first element that matches the predicate
     */
    constexpr const_iterator find_if(std::predicate<const T&> auto pred) const {
        for (auto it = cbegin(); it != cend(); ++it) {
            if (pred(read_access(it.current))) {
                return it;
            }
        }
        return cend();
    }

    /**
     * Remove all elements that match the predicate
     */
    constexpr std::size_t erase_if(std::predicate<const T&> auto pred) {
        std::size_t removed_count = 0;
        for (auto it = cbegin(); it != cend(); ) {
            if (pred(read_access(it.current))) {
                it = erase(it);
                removed_count++;
            }
            else {
                ++it;
            }
        }
        return removed_count;
    }

    constexpr const_iterator erase(const_iterator it) {
        IndexType idx = it.current;
        if (idx == Nil) return it;

        IndexType nextIdx = storage[idx].link.next;
        remove(idx); // unlinking and freelist handling
        return const_iterator{ nextIdx, this };
    }

private:

    constexpr bool is_valid_internal(IndexType idx) const {
        // Nil is a special case in insert() (it means 'end' there), 
        // so we check only for the upper bound.
        return idx <= Cap;
    }

    constexpr IndexType allocate_from_free_list() {
        if (free_head == Nil) return Nil;
        IndexType idx = free_head;
        free_head = storage[idx].link.next; // choose next free slot
        return idx;
    }

    constexpr void release_to_free_list(IndexType idx) {
        // we "abuse" the next-pointer for the freelist chain
        storage[idx].link.next = free_head;
        storage[idx].link.prev = Nil;
        free_head = idx;
    }

    constexpr T& write_access(IndexType idx) {
        return access(idx, true);
    }

    constexpr const T& read_access(IndexType idx) const {
        return access(idx, false);
    }

    static constexpr IndexType Clean = Nil;
    static constexpr IndexType Dirty = 1;

    // common internal logic for read and write access paths
    constexpr T& access(IndexType idx, bool is_write_intent) const {
        if (idx == Nil || idx > Cap) {
            if (std::is_constant_evaluated()) {
                throw "Critical error: Access with illegal index!";
            }
#ifdef _DEBUG
            throw std::out_of_range("Critical error: Access with illegal index!");
#endif
            auto& sentinel = const_cast<NodeType&>(storage[Nil]);

            if (sentinel.link.prev == Dirty) {
                // The sentinel might have been contaminated
                static_cast<T&>(sentinel) = T{};
                sentinel.link.prev = Clean;
            }

            if (is_write_intent) {
                // The caller could write to it, so
                // we make provisions for that case
                sentinel.link.prev = Dirty;
            }

            return static_cast<T&>(sentinel);
        }
        return static_cast<T&>(const_cast<NodeType&>(storage[idx]));
    }
};

