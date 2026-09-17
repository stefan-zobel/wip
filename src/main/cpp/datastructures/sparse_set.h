#pragma once

#include <algorithm>
#include <vector>
#include <cstdint>
#include <stdexcept>
#include <span>
#include <cassert>
#include "strong_types.h"

namespace fk {

    // ========================================================================
    // DenseEntity
    // A handle for the SparseSet: 32 bits slot index | 32 bits generation.
    // Slots are recycled after erase() and clear(); the generation tells a
    // stale handle apart from the handle of the object that reuses its slot.
    // ========================================================================
    struct DenseEntity : public fk::StrongType<uint64_t, struct DenseEntityTag> {
        using StrongType::StrongType;

        // Internal marker for "Null / Invalid / Free" slot indices
        static constexpr uint32_t INVALID_INDEX = UINT32_MAX;

        [[nodiscard]] static constexpr DenseEntity create(uint32_t index, uint32_t generation) noexcept {
            return DenseEntity{ (static_cast<uint64_t>(generation) << 32) | index };
        }

        [[nodiscard]] constexpr uint32_t index() const noexcept {
            return static_cast<uint32_t>(get() & 0xFFFF'FFFFull);
        }

        [[nodiscard]] constexpr uint32_t generation() const noexcept {
            return static_cast<uint32_t>(get() >> 32);
        }
    };


    // ========================================================================
    // SparseSet
    // A radically optimized container for ECS and physics loops.
    // Guarantees that all active objects T are perfectly contiguous in memory.
    // Deletions happen via O(1) Swap-and-Pop mechanism.
    // Iterating over this container is exactly as fast as a raw C-array.
    //
    // Entity IDs (slots) are recycled: erase() and clear() put slots on a free
    // list, and every release increments the slot's generation, so a handle from
    // before the release never resolves again. The sparse array therefore only
    // grows to the highest number of entities alive at the same time (8 bytes per
    // slot). A slot whose 32-bit generation is exhausted is retired instead of
    // being reused.
    // ========================================================================
    template <typename T>
    class SparseSet final {
    public:
        // Slot bookkeeping of the sparse array
        struct SparseSlot {
            // occupied: index into the dense array; free: next free slot (or INVALID_INDEX)
            uint32_t dense_or_next = DenseEntity::INVALID_INDEX;
            uint32_t generation = 0;
        };

        // Advances the generation of a released slot. Returns false if the generation is
        // exhausted; the slot must then be retired (never handed out again).
        [[nodiscard]] static constexpr bool advance_generation(SparseSlot& slot) noexcept {
            if (slot.generation == UINT32_MAX) {
                return false;
            }
            ++slot.generation;
            return true;
        }

    private:
        // 'dense' holds the actual data T closely packed with absolute zero gaps.
        std::vector<T> m_dense;

        // 'dense_to_sparse' tells us: "The object at m_dense[i] belongs to which Entity?"
        // This is strictly required so that when we move an object in m_dense,
        // we know WHICH Entity's sparse slot we need to update.
        std::vector<DenseEntity> m_dense_to_sparse;

        // 'sparse' is basically a lookup table: slot index -> dense array index (plus generation)
        std::vector<SparseSlot> m_sparse;

        // Head of the implicit free list threaded through SparseSlot::dense_or_next
        uint32_t m_free_head = DenseEntity::INVALID_INDEX;

        // Puts a slot that no longer refers to an object on the free list (or retires it)
        void release_slot(uint32_t index) noexcept {
            SparseSlot& slot = m_sparse[index];
            if (advance_generation(slot)) {
                slot.dense_or_next = m_free_head;
                m_free_head = index;
            } else {
                slot.dense_or_next = DenseEntity::INVALID_INDEX;  // retired
            }
        }

    public:
        SparseSet() = default;

        explicit SparseSet(size_t capacity) {
            m_dense.reserve(capacity);
            m_dense_to_sparse.reserve(capacity);
            m_sparse.reserve(capacity);
        }

        // --- Core Size API ---

        size_t size() const noexcept { return m_dense.size(); }
        bool empty() const noexcept { return m_dense.empty(); }

        // Number of slots in the sparse array (the highest number of entities that were
        // alive at the same time, plus retired slots)
        [[nodiscard]] size_t slot_count() const noexcept { return m_sparse.size(); }

        // Destroys all objects and releases their slots: the slots are reused, handles
        // from before clear() never resolve again.
        void clear() noexcept {
            for (const DenseEntity entity : m_dense_to_sparse) {
                release_slot(entity.index());
            }
            m_dense.clear();
            m_dense_to_sparse.clear();
        }

        // --- Contiguous Exposing API ---

        // This is why SparseSets are kings: One can extract the entire living
        // data array as a flat span and run AVX/SIMD over it without checking
        // for 'nullopt' or gaps
        std::span<T> data_span() noexcept { return m_dense; }
        std::span<const T> data_span() const noexcept { return m_dense; }

        auto begin() noexcept { return m_dense.begin(); }
        auto end() noexcept { return m_dense.end(); }
        auto begin() const noexcept { return m_dense.begin(); }
        auto end() const noexcept { return m_dense.end(); }

        // ====================================================================
        // Emplace (O(1))
        // Pushes the object purely to the back of the dense array and returns a
        // handle for a free (recycled) or new slot.
        // Strong exception guarantee: if the constructor of T (or an allocation)
        // throws, the set is left unchanged and no slot is consumed.
        // ====================================================================
        template <typename... Args>
        DenseEntity emplace(Args&&... args) {
            const bool reuse = m_free_head != DenseEntity::INVALID_INDEX;
            uint32_t index = m_free_head;
            if (!reuse) {
                if (m_sparse.size() >= DenseEntity::INVALID_INDEX) {
                    throw std::overflow_error("SparseSet: entity slots exhausted (32-bit index limit).");
                }
                index = static_cast<uint32_t>(m_sparse.size());
                if (m_sparse.size() == m_sparse.capacity()) {
                    // may throw; the push_back below cannot (geometric growth as in push_back)
                    m_sparse.reserve(std::max<size_t>(16, m_sparse.capacity() * 2));
                }
            }
            const uint32_t generation = reuse ? m_sparse[index].generation : 0;
            const DenseEntity entity = DenseEntity::create(index, generation);
            const uint32_t dense_index = static_cast<uint32_t>(m_dense.size());

            // Construct the object first: if T's constructor throws, nothing refers to it yet
            m_dense.emplace_back(std::forward<Args>(args)...);
            try {
                m_dense_to_sparse.push_back(entity);
            } catch (...) {
                m_dense.pop_back();
                throw;
            }

            // Record the slot only after everything succeeded (cannot throw)
            if (reuse) {
                m_free_head = m_sparse[index].dense_or_next;
            } else {
                m_sparse.push_back(SparseSlot{});
            }
            m_sparse[index].dense_or_next = dense_index;

            return entity;
        }

        // ====================================================================
        // Contains / Validation (O(1))
        // Verify if a handle is currently alive and part of this set.
        // ====================================================================
        bool contains(DenseEntity entity) const noexcept {
            const uint32_t index = entity.index();
            if (index >= m_sparse.size()) {
                return false;
            }
            const SparseSlot& slot = m_sparse[index];
            // The generation rejects stale handles; the cross-check rejects free slots
            return slot.generation == entity.generation()
                && slot.dense_or_next < m_dense_to_sparse.size()
                && m_dense_to_sparse[slot.dense_or_next] == entity;
        }

        // ====================================================================
        // Get / Lookup (O(1))
        // Safe access (returns nullptr if deleted).
        // ====================================================================
        T* get(DenseEntity entity) noexcept {
            if (!contains(entity)) return nullptr;
            return &m_dense[m_sparse[entity.index()].dense_or_next];
        }

        const T* get(DenseEntity entity) const noexcept {
            if (!contains(entity)) return nullptr;
            return &m_dense[m_sparse[entity.index()].dense_or_next];
        }

        // ====================================================================
        // Erase: Swap and Pop (O(1))
        // This is the magic. It deletes the item, takes the LAST item in the
        // array, moves it into the deleted item's spot, and pops the back.
        // The slot of the deleted item goes to the free list.
        // ====================================================================
        bool erase(DenseEntity entity) noexcept {
            if (!contains(entity)) return false;

            const uint32_t index_to_delete = entity.index();
            const uint32_t hole_dense_index = m_sparse[index_to_delete].dense_or_next;
            const uint32_t last_dense_index = static_cast<uint32_t>(m_dense.size() - 1);

            // 1. If the victim is NOT the last element, we must physically move the last element into the hole
            if (hole_dense_index != last_dense_index) {
                // Move data
                m_dense[hole_dense_index] = std::move(m_dense[last_dense_index]);

                // Which entity was living at that last spot?
                const DenseEntity displaced_entity = m_dense_to_sparse[last_dense_index];

                // Track it in the reverse map
                m_dense_to_sparse[hole_dense_index] = displaced_entity;

                // Tell the sparse map that the displaced entity has a new home coordinates
                m_sparse[displaced_entity.index()].dense_or_next = hole_dense_index;
            }

            // 2. The back of the array is now either garbage or holds the victim. Pop it!
            m_dense.pop_back();
            m_dense_to_sparse.pop_back();

            // 3. Recycle the victim's slot
            release_slot(index_to_delete);

            return true;
        }
    };

} // namespace fk
