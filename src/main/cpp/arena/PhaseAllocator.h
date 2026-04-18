#pragma once

#include <array>
#include <memory>
#include <utility>
#include "SimpleArena.h"
    
// A PhaseAllocator (also known as a Frame Allocator or Ping-Pong Allocator).
// It cycles through N underlying arenas (phases). When transitioning to a
// new phase, the oldest phase is completely wiped clean (O(1) memory reset)
// and reused. This is exceptionally fast for request-based architectures,
// game loops, or video/audio processing frame tasks.
template <size_t N = 2>
class PhaseAllocator {
public:
    // Initialize the phase allocator explicitly by generating N underlying arenas
    static std::unique_ptr<PhaseAllocator> create(size_t reserve_per_arena) {
        std::unique_ptr<PhaseAllocator> allocator(new PhaseAllocator());

        for (size_t i = 0; i < N; ++i) {
            allocator->arenas[i] = SimpleArena::create(reserve_per_arena);
            if (!allocator->arenas[i]) return nullptr;
        }

        return allocator;
    }

    // Proxy the raw allocation to the *currently active* arena phase
    void* allocate_raw_aligned(size_t size, size_t alignment = 64) {
        return arenas[current_phase]->allocate_raw_aligned(size, alignment);
    }

    // Proxy the object construction to the *currently active* arena phase
    template <typename T, typename... Args>
    T* construct(Args&&... args) {
        return arenas[current_phase]->construct<T>(std::forward<Args>(args)...);
    }

    // Finalizes the current phase, moves to the next one, and strictly WIPES the 
    // new phase entirely. All destructors of objects in the upcoming phase are executed.
    void next_phase() {
        // Move iterator forward (ping-pong wrapping)
        current_phase = (current_phase + 1) % N;

        // Wipe the newly active arena so it's a completely fresh slate for this phase.
        arenas[current_phase]->release();
    }

    // Expose the current phase index (e.g., to verify ping-ponging in tests)
    size_t get_current_phase() const noexcept {
        return current_phase;
    }

private:
    PhaseAllocator() = default;

    std::array<std::unique_ptr<SimpleArena>, N> arenas;
    size_t current_phase = 0;
};
