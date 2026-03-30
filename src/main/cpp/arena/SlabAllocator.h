#pragma once

#include <bit>        // For std::bit_ceil and std::countr_zero in C++20
#include <array>
#include <cassert>
#include <algorithm>
#include <cstddef>    // For std::max_align_t
#include <utility>    // For std::forward
#include "SimpleArena3.h"

// A Slab/Size-Class Allocator built on top of SimpleArena3.
// It manages memory in buckets of powers-of-two (8, 16, 32, ..., up to MAX_SIZE).
// This allows fast O(1) allocations and O(1) recycling for arbitrary sized 
// objects up to MAX_SIZE. Objects larger than MAX_SIZE bypass the pooling and 
// are allocated directly from the Arena.
class SlabAllocator {
    // Intrusive linked list node (re-uses memory of freed blocks)
    struct Node {
        Node* next;
    };

    // Configuration
    static constexpr size_t MIN_SIZE = 8;      // Smallest bin is 8 bytes (needs to fit a Node*)
    static constexpr size_t MAX_SIZE = 4'096;  // Largest pooled bin is 4096 bytes
    // How many bins we have: 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096 (10 bins)
    static constexpr size_t NUM_BINS = std::countr_zero(MAX_SIZE) - std::countr_zero(MIN_SIZE) + 1;

public:
    explicit SlabAllocator(SimpleArena3& arena) 
        : source_arena(arena), known_epoch(arena.get_epoch()) {
        free_lists.fill(nullptr);
    }

    // Allocate memory of a specific size and alignment.
    // Highly optimized for O(1) bucket selection.
    void* allocate(size_t size, size_t alignment = 64) {
        sync_epoch();

        // Ensure we always allocate at least enough to store our intrusive Node
        size_t required_size = std::max(size, MIN_SIZE);

        if (required_size > MAX_SIZE) {
            // Bypass slab cache for huge allocations. Let the Arena handle it directly.
            // Note: These cannot be recycled individually.
            return source_arena.allocate_raw_aligned(required_size, alignment);
        }

        // Fast path: Find the correct power-of-two bin using C++20 <bit>
        // std::bit_ceil rounds up to next power of 2 (e.g. 17 -> 32)
        size_t size_class = std::bit_ceil(required_size);

        // Calculate the array index O(1):
        // e.g., if size_class is 32 (2^5), countr_zero(32) is 5. 
        // 5 - 3 (which is countr_zero(8)) = index 2.
        size_t bin_index = std::countr_zero(size_class) - std::countr_zero(MIN_SIZE);
        
        // Is there a recycled block available?
        if (Node* free_node = free_lists[bin_index]) {
            free_lists[bin_index] = free_node->next; // pop front
            return static_cast<void*>(free_node);
        }

        // If no recycled block is available, carve it freshly from the arena.
        // We strictly use `size_class` as the allocation size, NOT `size`, so that
        // recycling guarantees the required block dimensions
        return source_arena.allocate_raw_aligned(size_class, std::max(alignment, alignof(Node)));
    }

    // Recycle memory. The user MUST pass the original size requested (or object size),
    // otherwise the allocator wouldn't know into which bin to put the memory.
    void deallocate(void* ptr, size_t original_size) noexcept {
        if (!ptr) return;

        // Same calculation as in allocate
        size_t required_size = std::max(original_size, MIN_SIZE);

        if (required_size > MAX_SIZE) {
            // Memory larger than MAX_SIZE cannot be pooled. 
            // We just let it "leak" dynamically until the source_arena is fully released.
            return; 
        }

        size_t size_class = std::bit_ceil(required_size);
        size_t bin_index = std::countr_zero(size_class) - std::countr_zero(MIN_SIZE);

        // Intrusively add to the head of the specific bin's free list
        Node* node = static_cast<Node*>(ptr);
        node->next = free_lists[bin_index];
        free_lists[bin_index] = node;
    }

    // Template helpers for seamless type-safe object creation & destruction
    template <typename T, typename... Args>
    [[nodiscard]] T* construct(Args&&... args) {
        void* ptr = allocate(sizeof(T), alignof(T));
        if (!ptr) return nullptr;

        return new (ptr) T(std::forward<Args>(args)...);
    }

    template <typename T>
    void destruct(T* ptr) noexcept {
        if (!ptr) return;
        ptr->~T();
        deallocate(ptr, sizeof(T));
    }

private:
    void sync_epoch() noexcept {
        if (known_epoch != source_arena.get_epoch()) {
            // Arena was hard-reset. All our free-blocks are now dangling pointers.
            free_lists.fill(nullptr);
            known_epoch = source_arena.get_epoch();
        }
    }

    SimpleArena3& source_arena;
    size_t known_epoch;
    
    // One free list for each size class (8, 16, 32, ..., MAX_SIZE)
    std::array<Node*, NUM_BINS> free_lists;
};
