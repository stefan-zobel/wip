#pragma once

#include "SimpleArena.h"
#include <utility>
#include <algorithm>

template <typename T>
class BlockPool {
    // Embedded singly linked list node to store free blocks
    // This reuses the memory of the destructed object allowing zero-overhead recycling
    struct Node {
        Node* next;
    };

public:
    // Bind the pool to a specific arena instance and snapshot its initial epoch
    explicit BlockPool(SimpleArena& arena) 
        : source_arena(arena), known_epoch(arena.get_epoch()) {}

    // Constructs an object of type T using perfect forwarding
    template <typename... Args>
    T* construct(Args&&... args) {
        T* ptr = allocate_block();
        if (!ptr) {
            return nullptr; // Underlying arena is completely out of memory
        }

        // Execute placement new to construct the object in the reserved memory
        return new (ptr) T(std::forward<Args>(args)...);
    }

    // Destroys the object and recycles its memory into the free list
    void destruct(T* ptr) {
        if (!ptr) return;

        // 1. Explicitly call the destructor
        ptr->~T();

        // 2. Clear the memory and morph it into a free list node
        Node* new_node = reinterpret_cast<Node*>(ptr);
        new_node->next = free_list;
        free_list = new_node;
    }

private:
    // Fetches memory either from recycling or the underlying arena
    T* allocate_block() {
        // Automatic sync:
        // If the arena has been released (reset) since the last allocation, 
        // our free list contains dangling pointers. Discard them safely.
        if (known_epoch != source_arena.get_epoch()) {
            free_list = nullptr;
            known_epoch = source_arena.get_epoch();
        }

        // Fast path: Reuse a previously destructed block
        if (free_list) {
            void* ptr = free_list;
            free_list = free_list->next;
            return static_cast<T*>(ptr);
        }

        // Calculate the minimum size and alignment required.
        // The block must be large enough to hold either the object T or a Node pointer.
        constexpr size_t size = std::max(sizeof(T), sizeof(Node));
        constexpr size_t alignment = std::max(alignof(T), alignof(Node));

        // Slow path: Ask the arena for a fresh block of formatted memory.
        // We use allocate_raw_aligned so the arena doesn't track its destructor,
        // because BlockPool manually handles the lifecycles.
        return static_cast<T*>(source_arena.allocate_raw_aligned(size, alignment));
    }

    SimpleArena& source_arena;
    Node* free_list = nullptr;
    size_t known_epoch = 0; 
};
