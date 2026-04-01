#pragma once

#include <vector>
#include <cstdint>
#include <stdexcept>
#include <cassert>
#include <type_traits>
#include <memory>
#include "strong_types.h"

namespace fk {

    // ========================================================================
    // GlobalHandle
    // A strict 64-bit ID representing any object in the entire world, exactly
    // identifying its Type-Pool, its logical Slot, and its temporal Generation.
    // Layout: 
    // [16 Bits TypeID] [16 Bits Generation] [32 Bits Logic Slot]
    // ========================================================================
    struct GlobalHandle : public fk::StrongType<uint64_t, struct GlobalHandleTag> {
        using StrongType::StrongType; 

        static constexpr uint64_t SLOT_MASK = (1ULL << 32) - 1; // Lower 32 bits
        static constexpr uint64_t GEN_MASK  = ((1ULL << 16) - 1) << 32; // Mid 16 bits
        static constexpr uint64_t TYPE_MASK = ((1ULL << 16) - 1) << 48; // Top 16 bits

        static constexpr GlobalHandle create(uint16_t type_id, uint32_t slot, uint16_t generation) noexcept {
            uint64_t encoded = (static_cast<uint64_t>(type_id) << 48) |
                               (static_cast<uint64_t>(generation) << 32) |
                               (static_cast<uint64_t>(slot));
            return GlobalHandle{ encoded };
        }

        constexpr uint16_t type_id() const noexcept { return static_cast<uint16_t>((this->get() & TYPE_MASK) >> 48); }
        constexpr uint16_t generation() const noexcept { return static_cast<uint16_t>((this->get() & GEN_MASK) >> 32); }
        constexpr uint32_t slot() const noexcept { return static_cast<uint32_t>(this->get() & SLOT_MASK); }
    };

    inline constexpr GlobalHandle INVALID_HANDLE{ 0 };

    // ========================================================================
    // Compile-Time Type ID Generator
    // Assigns a perfectly unique integer (0, 1, 2...) to every C++ Type requested.
    // By utilizing static local variables inside templates, the compiler 
    // assigns indices purely upon instantiation across the application.
    // ========================================================================
    class TypeRegistry {
        static inline uint16_t m_next_id = 1; // 0 is reserved for 'invalid'
    public:
        template <typename T>
        static uint16_t id() noexcept {
            static const uint16_t type_id = m_next_id++;
            return type_id;
        }
    };


    // ========================================================================
    // HandleManager
    // The central dictionary (telephone book). It routes abstract handles to 
    // physical memory pointers. It manages 'Swap-and-Pop' defragmentation 
    // seamlessly, allowing RAM layouts to shift while external handles remain
    // valid.
    // ========================================================================
    class HandleManager final {
    private:
        // --------------------------------------------------------------------
        // Abstract Type-Backend (Type Erasure Interface)
        // Required so the manager can hold a generic list of totally different 
        // typed densely packed vectors.
        // --------------------------------------------------------------------
        class IBackend {
        public:
            virtual ~IBackend() = default;
            // Destroys an element by logical slot ID, which triggers defragmentation.
            virtual bool destroy_and_swap_pop(uint32_t logical_slot) = 0; 
        };

        // --------------------------------------------------------------------
        // Dense Typed Array Backend (The Physical Memory Store)
        // --------------------------------------------------------------------
        template <typename T>
        class DenseBackend final : public IBackend {
        private:
            HandleManager& m_manager_ref;

            // 1. Physical RAM Array (100% Contiguous for CPU cache performance)
            std::vector<T> m_dense_data;

            // 2. Forward Routing Map
            // Routes: Logical Slot ID -> Current Physical Index in m_dense_data
            std::vector<uint32_t> m_logic_to_phys;

            // 3. Reverse Routing Map
            // Routes: Current Physical Index -> Original Logical Slot ID
            // Highly important to reconstruct identity when elements are moved
            std::vector<uint32_t> m_phys_to_logic;

        public:
            explicit DenseBackend(HandleManager& mgr) : m_manager_ref(mgr) {}

            T* physical_get(uint32_t physical_idx) {
                return &m_dense_data[physical_idx];
            }

            // Look up where the item currently physically resides in RAM
            uint32_t get_physical_index(uint32_t logical_slot) const noexcept {
                if (logical_slot >= m_logic_to_phys.size()) return UINT32_MAX;
                return m_logic_to_phys[logical_slot];
            }

            // Emplaces structural data purely at the back
            uint32_t emplace_data(T&& data, uint32_t assigned_logical_slot) {
                uint32_t active_phys_idx = static_cast<uint32_t>(m_dense_data.size());
                m_dense_data.push_back(std::forward<T>(data));

                // Track the bidirectional mapping for immediate resolves
                if (assigned_logical_slot >= m_logic_to_phys.size()) {
                    m_logic_to_phys.resize(assigned_logical_slot + 1, UINT32_MAX);
                }
                m_logic_to_phys[assigned_logical_slot] = active_phys_idx;
                m_phys_to_logic.push_back(assigned_logical_slot);

                return active_phys_idx;
            }

            // ================================================================
            // DEFRAGMENTATION / SWAP AND POP
            // The core DOD concept: Destroying an element leaves a hole. To 
            // prevent array fragmentation, the extremely last element of the 
            // array is structurally moved into this hole, sealing the gap.
            // ================================================================
            bool destroy_and_swap_pop(uint32_t logical_slot) override {
                if (logical_slot >= m_logic_to_phys.size() || m_logic_to_phys[logical_slot] == UINT32_MAX) 
                    return false; // Already dead

                uint32_t dead_phys_idx = m_logic_to_phys[logical_slot];
                uint32_t last_phys_idx = static_cast<uint32_t>(m_dense_data.size() - 1);

                // Erase the victims presence from the logic router
                m_logic_to_phys[logical_slot] = UINT32_MAX;

                // Move last element into the hole to maintain dense arrays
                if (dead_phys_idx != last_phys_idx) {

                    // 1. Physically move heavy data into the gap
                    m_dense_data[dead_phys_idx] = std::move(m_dense_data[last_phys_idx]);

                    // 2. Discover who we just relocated using the reverse map
                    uint32_t relocated_logical_slot = m_phys_to_logic[last_phys_idx];

                    // 3. Re-wire the dictionaries. The relocated element now officially 
                    // registers under the index of the previously dead hole. 
                    // (This effectively swizzles the pointer for external Handles on the fly!)
                    m_logic_to_phys[relocated_logical_slot] = dead_phys_idx; 
                    m_phys_to_logic[dead_phys_idx] = relocated_logical_slot;
                }

                // Eject the now-redundant data tail from physical memory
                m_dense_data.pop_back();
                m_phys_to_logic.pop_back();

                return true;
            }
        };

        // --------------------------------------------------------------------
        // The Application Global Logic Router
        // --------------------------------------------------------------------
        struct SlotMeta {
            uint16_t generation = 0;
        };

        std::vector<std::unique_ptr<IBackend>> m_backends; // TypeID -> Specific Dense Array Backend
        std::vector<SlotMeta> m_slots;                     // Logic Slot ID -> Generational State

        // O(1) recycled logic slots to keep Handle numbers low
        std::vector<uint32_t> m_free_slots; 

    public:
        // Automatically instantiates highly specific arrays based on type requirements
        template <typename T>
        DenseBackend<T>& get_backend() {
            uint16_t tid = TypeRegistry::id<T>();
            if (tid >= m_backends.size()) m_backends.resize(tid + 1);
            if (!m_backends[tid]) m_backends[tid] = std::make_unique<DenseBackend<T>>(*this);
            return static_cast<DenseBackend<T>&>(*m_backends[tid]);
        }

        // ====================================================================
        // Spawn Object (Returns unshakeable Handle)
        // ====================================================================
        template <typename T>
        GlobalHandle spawn(T&& obj) {
            uint32_t slot = 0;

            if (!m_free_slots.empty()) {
                slot = m_free_slots.back();
                m_free_slots.pop_back();
            } else {
                slot = static_cast<uint32_t>(m_slots.size());
                m_slots.push_back(SlotMeta{0});
            }

            uint16_t gen = m_slots[slot].generation;
            uint16_t tid = TypeRegistry::id<T>();

            // Let the isolated backend place the raw memory
            get_backend<T>().emplace_data(std::forward<T>(obj), slot);

            return GlobalHandle::create(tid, slot, gen);
        }

        // ====================================================================
        // Resolve (The Pointer Swizzler)
        // Converts the abstract uint64_t handle back into a highly explosive RAM
        // pointer. Safely rejects dead variables (use-after-free) and type-mismatches.
        // ====================================================================
        template <typename T>
        T* resolve(GlobalHandle handle) {
            if (handle == INVALID_HANDLE) return nullptr;

            uint32_t slot = handle.slot();
            if (slot >= m_slots.size() || m_slots[slot].generation != handle.generation()) {
                return nullptr;
            }

            if (handle.type_id() != TypeRegistry::id<T>()) {
                return nullptr; // Type safety mismatch
            }

            // Retrieve the type-specific physical storage
            DenseBackend<T>& backend = get_backend<T>();

            // Retrieve the current physical index, which may have changed drastically 
            // since the handle was created due to DOD Defragmentation (Swap & Pop).
            uint32_t physical_idx = backend.get_physical_index(slot);
            
            if (physical_idx == UINT32_MAX) return nullptr;

            return backend.physical_get(physical_idx);
        }

        // ====================================================================
        // Destroy (Triggers intense Defragmentation silently)
        // ====================================================================
        void destroy(GlobalHandle handle) {
            uint32_t slot = handle.slot();
            if (slot >= m_slots.size() || m_slots[slot].generation != handle.generation()) return;

            uint16_t tid = handle.type_id();
            if (tid < m_backends.size() && m_backends[tid]) {

                if (m_backends[tid]->destroy_and_swap_pop(slot)) {
                    // Update validation token to permanently render the users external handle unusable
                    m_slots[slot].generation++; 
                    m_free_slots.push_back(slot);
                }
            }
        }
    };

} // namespace fk
