#pragma once

#include <utility>
#include "SimpleArena3.h"


// A StackAllocator implemented as a RAII strict scope wrapper around SimpleArena3.
// Creating this object instantly takes a snapshot of the arena. 
// When the StackAllocator goes out of scope (e.g. at the end of a function or a block {} ), 
// it automatically rolls back the arena to exactly the state it was in, triggering 
// destructors only for the newly added objects that were created during this scope.
class StackAllocator {
public:
    // Captures the current state of the backend arena completely automatically
    explicit StackAllocator(SimpleArena3& underlying_arena)
        : source_arena(underlying_arena), 
          marker(underlying_arena.get_marker()) {}

    // Automatically rolls back on scope exit. No manual cleanup needed.
    ~StackAllocator() {
        source_arena.rollback(marker);
    }

    // StackAllocators cannot be copied or moved (they must be strictly tied to lexical scopes)
    StackAllocator(const StackAllocator&) = delete;
    StackAllocator& operator=(const StackAllocator&) = delete;

    // Passthrough allocation logic (defaults to 64-byte Cache-Line Alignment for safety)
    void* allocate_raw_aligned(size_t size, size_t alignment = 64) {
        return source_arena.allocate_raw_aligned(size, alignment);
    }

    // Passthrough object construction logic
    template <typename T, typename... Args>
    T* construct(Args&&... args) {
        return source_arena.construct<T>(std::forward<Args>(args)...);
    }

private:
    SimpleArena3& source_arena;
    SimpleArena3::Marker marker;
};
