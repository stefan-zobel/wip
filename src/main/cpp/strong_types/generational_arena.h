#pragma once

#include <vector>
#include <stdexcept>
#include <cstdint>
#include <optional>
#include "strong_types.h"

namespace fk {

    // ========================================================================
    // GenHandle
    // A strict 64-bit strong type acting as the secure key to our Generational 
    // Arena. It guarantees that an external caller can never accidentally read  
    // a rewritten memory slot from a previous lifecycle.
    //
    // Layout: 28 Bits Generation | 36 Bits Index/Slot
    // (Allows for 68 billion items simultaneously, and 268 million 
    // lifecycle rollovers per specific slot).
    // ========================================================================
    struct GenHandle : public fk::StrongType<uint64_t, struct GenHandleTag> {
        using StrongType::StrongType; 

        // Mask constants for bitwise packing/unpacking
        static constexpr uint64_t INDEX_BTM_MASK = (1ULL << 36) - 1; // Lower 36 bits
        static constexpr uint64_t GEN_TOP_MASK   = ~INDEX_BTM_MASK;  // Upper 28 bits

        // Factory function to quickly encode Index + Generation into a single u64
        static constexpr GenHandle create(uint64_t index, uint32_t generation) noexcept {
            uint64_t encoded = (index & INDEX_BTM_MASK) | (static_cast<uint64_t>(generation) << 36);
            return GenHandle{ encoded };
        }

        // Extracts the physical array index from the Handle
        constexpr uint64_t index() const noexcept {
            return this->get() & INDEX_BTM_MASK;
        }

        // Extracts the lifecycle generation from the Handle
        constexpr uint32_t generation() const noexcept {
            return static_cast<uint32_t>((this->get() & GEN_TOP_MASK) >> 36);
        }
    };


    // ========================================================================
    // GenerationalArena
    // An ECS (Entity Component System) inspired contiguous memory structure 
    // backed by a flat std::vector.
    //
    // It provides:
    // - O(1) Insertions (recycles empty slots instantly via an implicit free-list)
    // - O(1) Lookups (with immediate Use-After-Free validation)
    // - O(1) Deletions
    // - Zero continuous reallocation overhead (if capacity is pre-allocated)
    // ========================================================================

    // Internal node wrapping the user's data T.
    // Forward-declared outside the main class so the MSVC Base-Class deduction 
    // is rigorous and doesn't confuse the compiler during incomplete type
    // evaluation of private inheritance.
    template <typename T>
    struct ArenaSlot {
        // Modern C++ replacement for Unions.
        // - If has_value() is true -> Slot is ALIVE.
        // - If has_value() is false -> Slot is DEAD.
        // (std::optional correctly calls the destructor of T when reset).
        std::optional<T> data;      
        
        // If data is dead (nullopt), this index points to the NEXT dead slot, 
        // forming a blazing fast implicit linked list of free memory regions.
        uint64_t next_free_pos = 0; 
        
        // Tracks how many times this specific index was destroyed and reused.
        uint32_t generation = 0;    
    };

    template <typename T>
    class GenerationalArena final : private std::vector<ArenaSlot<T>> {
    private:
        using BaseClass = std::vector<ArenaSlot<T>>;

        // "UINT64_MAX" acts as our explicit null-pointer for the free-list
        uint64_t m_first_free = UINT64_MAX; 
        
        // Tracks how many items are currently actively stored vs total capacity
        uint64_t m_num_taken = 0;           

    public:
        // Expose absolutely essential std::vector geometric methods safely
        using BaseClass::capacity;
        using BaseClass::reserve;
        using BaseClass::clear;

        // Default Constructor
        GenerationalArena() = default;

        // Capacity Constructor
        // Pre-allocates the underlying vector to prevent expensive dynamic 
        // heap reallocations during runtime when many entities spawn at once.
        explicit GenerationalArena(size_t initial_capacity) {
            this->reserve(initial_capacity);
        }

        // Returns how many items are currently actively alive
        uint64_t size() const noexcept {
            return m_num_taken;
        }

        // True if no active elements exist in the Arena
        bool empty() const noexcept {
            return m_num_taken == 0;
        }

        // ====================================================================
        // Insert (O(1))
        // Places an item in the first free slot, updates generation, 
        // and returns the strong GenHandle.
        // ====================================================================
        template <typename... Args>
        GenHandle emplace(Args&&... args) {
            uint64_t target_idx;

            if (m_first_free != UINT64_MAX) {
                // FAST PATH: Reclaim a previously destroyed index from the free-list
                target_idx = m_first_free;
                ArenaSlot<T>& slot = BaseClass::operator[](target_idx);

                // Disconnect this slot from the free list (pop front)
                m_first_free = slot.next_free_pos;

                // Construct the actual user data perfectly in-place
                slot.data.emplace(std::forward<Args>(args)...);

                m_num_taken++;
                return GenHandle::create(target_idx, slot.generation);
            } else {
                // SLOW PATH: No free slot exists. We must expand the backing vector.
                target_idx = BaseClass::size();

                // Safety limit: We only have 36 bits for index representation
                if (target_idx > GenHandle::INDEX_BTM_MASK) {
                    throw std::overflow_error("GenerationalArena max capacity reached (36-bit index limit).");
                }

                ArenaSlot<T> new_slot;
                new_slot.generation = 0; // Fresh memory begins at generation 0
                new_slot.data.emplace(std::forward<Args>(args)...);

                BaseClass::push_back(std::move(new_slot));
                m_num_taken++;

                return GenHandle::create(target_idx, 0);
            }
        }

        // ====================================================================
        // Get / Lookup (O(1))
        // Validates both the spatial index AND the temporal generation. 
        // Returns a pointer to T if it perfectly matches.
        // Returns nullptr immediately if the item was deleted or recreated.
        // ====================================================================
        T* get(GenHandle handle) noexcept {
            const uint64_t idx = handle.index();
            const uint32_t expected_gen = handle.generation();

            // Guard against out-of-bounds queries
            if (idx >= BaseClass::size()) return nullptr;

            ArenaSlot<T>& slot = BaseClass::operator[](idx);

            // True Validation: Does data exist here? 
            // And more importantly: Is it still the same entity the Handle was issued to?
            if (slot.data.has_value() && slot.generation == expected_gen) {
                return &(*slot.data);
            }

            return nullptr; // Dead handle (Use-After-Free prevented!)
        }

        // Const version of the getter
        const T* get(GenHandle handle) const noexcept {
            const uint64_t idx = handle.index();
            const uint32_t expected_gen = handle.generation();

            if (idx >= BaseClass::size()) return nullptr;

            const ArenaSlot<T>& slot = BaseClass::operator[](idx);

            if (slot.data.has_value() && slot.generation == expected_gen) {
                return &(*slot.data);
            }
            return nullptr;
        }

        // ====================================================================
        // Remove / Erase (O(1))
        // Validates the passed Handle. If completely valid, destroys the 
        // underlying object T, increments the temporal generation of the slot,
        // and pushes the slot to the front of the implicit free-list.
        // ====================================================================
        bool erase(GenHandle handle) noexcept {
            const uint64_t idx = handle.index();
            const uint32_t expected_gen = handle.generation();

            if (idx >= BaseClass::size()) return false;

            ArenaSlot<T>& slot = BaseClass::operator[](idx);

            if (slot.data.has_value() && slot.generation == expected_gen) {
                // Destroy data via std::optional reset (calls T::~T)
                slot.data.reset();

                // Sever ties to the past: Increment generation so any old handle 
                // in the user's application pointing to this index becomes invalid.
                slot.generation++;

                // Prevent 28-Bit Generation Overflow.
                // Resetting to 0 is generally fine, but requires ~268 million 
                // insertions over the exact same spot to ever cause a collision.
                if (slot.generation > (GenHandle::GEN_TOP_MASK >> 36)) {
                    slot.generation = 0; 
                }

                // Push this now-empty slot onto the front of the free-list
                slot.next_free_pos = m_first_free;
                m_first_free = idx;

                m_num_taken--;
                return true;
            }

            return false; // Item didn't exist or generation mismatched (already dead)
        }
    };

} // namespace fk
