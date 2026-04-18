#pragma once

#define NOMINMAX

#include <memory>
#include <type_traits>
#include <cstdint>
#include <algorithm>
#include <windows.h>

class SimpleArena {
private:
    // Embedded node to keep track of destructors inside the arena memory itself.
    using DestructorFunc = void(*)(void*);
    struct CleanupNode {
        void* ptr;
        DestructorFunc dtor;
        CleanupNode* next;
    };

    // 64 Bytes is the standard size of an L1 CPU cache line. 
    // Aligning safety markers to this boundary guarantees that no future SIMD/AVX 
    // allocation will ever crash after a rollback, and it fully prevents false-sharing.
    static constexpr size_t UNIVERSAL_MAX_ALIGN = 64;

public:
    // Represents an exact state/snapshot of the arena at a specific point in time
    struct Marker {
        size_t saved_offset;
        void* saved_cleanup_head; 
    };

    static constexpr size_t PAGE_SIZE = 4096;
    static constexpr size_t INITIAL_COMMIT = 64 * 1024;

    ~SimpleArena() {
        release();
        if (base_ptr) {
            VirtualFree(base_ptr, 0, MEM_RELEASE);
        }
    }

    static std::unique_ptr<SimpleArena> create(size_t reserve_size) {
        void* base = VirtualAlloc(nullptr, reserve_size, MEM_RESERVE, PAGE_NOACCESS);
        if (!base) return nullptr;

        size_t initial_commit = std::min(reserve_size, INITIAL_COMMIT);
        if (!VirtualAlloc(base, initial_commit, MEM_COMMIT, PAGE_READWRITE)) {
            VirtualFree(base, 0, MEM_RELEASE);
            return nullptr;
        }

        return std::unique_ptr<SimpleArena>(new SimpleArena(base, reserve_size, initial_commit));
    }

    void* allocate_raw_aligned(size_t size, size_t alignment = UNIVERSAL_MAX_ALIGN) {
        size_t current_addr = reinterpret_cast<size_t>(base_ptr) + offset;
        size_t padding = (alignment - (current_addr & (alignment - 1))) & (alignment - 1);
        size_t required_size = offset + padding + size;

        if (required_size > reserved_size) {
            return nullptr;
        }

        if (required_size > committed_size) {
            size_t needed = required_size - committed_size;
            size_t chunk_size = std::max(INITIAL_COMMIT, PAGE_SIZE);
            size_t commit_step = ((needed + chunk_size - 1) / chunk_size) * chunk_size;

            if (committed_size + commit_step > reserved_size) {
                commit_step = reserved_size - committed_size;
            }

            void* commit_addr = static_cast<char*>(base_ptr) + committed_size;
            if (!VirtualAlloc(commit_addr, commit_step, MEM_COMMIT, PAGE_READWRITE)) {
                return nullptr;
            }
            
            committed_size += commit_step;
        }

        void* ptr = static_cast<char*>(base_ptr) + offset + padding;
        offset = required_size;

        return ptr;
    }

    template <typename T, typename... Args>
    T* construct(Args&&... args) {
        void* node_ptr = nullptr;
        
        if constexpr (!std::is_trivially_destructible_v<T>) {
            node_ptr = allocate_raw_aligned(sizeof(CleanupNode), alignof(CleanupNode));
            if (!node_ptr) return nullptr;
        }

        void* ptr = allocate_raw_aligned(sizeof(T), alignof(T));
        if (!ptr) {
            return nullptr;
        }

        T* obj = new (ptr) T(std::forward<Args>(args)...);

        if constexpr (!std::is_trivially_destructible_v<T>) {
            CleanupNode* node = new (node_ptr) CleanupNode{
                ptr,
                [](void* p) { static_cast<T*>(p)->~T(); },
                cleanup_head
            };
            cleanup_head = node;
        }

        return obj;
    }

    Marker get_marker() const noexcept {
        // Rollbacks MUST snap to a universally safe boundary (Cache-Line size).
        // This makes it completely immune to extreme AVX/SIMD instructions.
        size_t current_addr = reinterpret_cast<size_t>(base_ptr) + offset;
        size_t alignment = UNIVERSAL_MAX_ALIGN;
        size_t padding = (alignment - (current_addr & (alignment - 1))) & (alignment - 1);
        
        return { offset + padding, cleanup_head };
    }

    void rollback(Marker target_marker) {
        CleanupNode* current = cleanup_head;
        CleanupNode* target = static_cast<CleanupNode*>(target_marker.saved_cleanup_head);

        while (current != target && current != nullptr) {
            current->dtor(current->ptr);
            current = current->next;
        }
        
        cleanup_head = target;
        offset = target_marker.saved_offset;
    }

    void release() {
        CleanupNode* current = cleanup_head;
        while (current) {
            current->dtor(current->ptr);
            current = current->next;
        }
        cleanup_head = nullptr;

        if (committed_size > INITIAL_COMMIT) {
            void* decommit_addr = static_cast<char*>(base_ptr) + INITIAL_COMMIT;
            size_t decommit_size = committed_size - INITIAL_COMMIT;
            
#pragma warning(suppress: 6250)
            VirtualFree(decommit_addr, decommit_size, MEM_DECOMMIT);
            committed_size = INITIAL_COMMIT;
        }

        offset = 0;
        ++current_epoch;
    }

    size_t get_epoch() const noexcept {
        return current_epoch;
    }

private:
    void* base_ptr;
    size_t reserved_size;
    size_t committed_size;
    size_t offset = 0;
    size_t current_epoch = 0;
    CleanupNode* cleanup_head = nullptr;

    SimpleArena(void* base, size_t reserve, size_t commit)
        : base_ptr(base), reserved_size(reserve), committed_size(commit) {}
};
