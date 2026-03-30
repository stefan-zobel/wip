#pragma once

#include <vector>
#include <cstdint>
#include <stdexcept>
#include <span>
#include <cassert>
#include "strong_types.h"

namespace fk {

    // ========================================================================
    // DenseEntity
    // A handle for the SparseSet. It represents an entity's ID.
    // ========================================================================
    struct DenseEntity : public fk::StrongType<uint32_t, struct DenseEntityTag> {
        using StrongType::StrongType; 

        // Internal marker for "Null / Invalid / Free"
        static constexpr uint32_t INVALID = UINT32_MAX;
    };


    // ========================================================================
    // SparseSet
    // A radically optimized container for ECS and physics loops.
    // Guarantees that all active objects T are perfectly contiguous in memory.
    // Deletions happen via O(1) Swap-and-Pop mechanism.
    // Iterating over this container is exactly as fast as a raw C-array.
    // ========================================================================
    template <typename T>
    class SparseSet final {
    private:
        // 'dense' holds the actual data T closely packed with absolute zero gaps.
        std::vector<T> m_dense;

        // 'dense_to_sparse' tells us: "The object at m_dense[i] belongs to which Entity ID?"
        // This is strictly required so that when we move an object in m_dense, 
        // we know WHICH Entity's sparse pointer we need to update.
        std::vector<DenseEntity> m_dense_to_sparse;

        // 'sparse' is basically a lookup table: Sparse_ID -> Dense Array Index
        std::vector<uint32_t> m_sparse;

        // Generates progressively growing unique Entity-IDs for new objects
        uint32_t m_next_entity_id = 0;

    public:
        SparseSet() = default;

        explicit SparseSet(size_t capacity) {
            m_dense.reserve(capacity);
            m_dense_to_sparse.reserve(capacity);
            m_sparse.reserve(capacity); // Sparse actually needs to grow to max EntityID used
        }

        // --- Core Size API ---

        size_t size() const noexcept { return m_dense.size(); }
        bool empty() const noexcept { return m_dense.empty(); }

        void clear() noexcept {
            m_dense.clear();
            m_dense_to_sparse.clear();
            m_sparse.clear();
            m_next_entity_id = 0;
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
        // Pushes the object purely to the back of the dense array.
        // Unconditionally returns a new unique Entity Handle.
        // ====================================================================
        template <typename... Args>
        DenseEntity emplace(Args&&... args) {
            const uint32_t entity_id = m_next_entity_id++;
            const uint32_t dense_index = static_cast<uint32_t>(m_dense.size());

            // Expand the sparse translation table if this is a newly seen ID peak
            if (entity_id >= m_sparse.size()) {
                m_sparse.resize(entity_id + 1, DenseEntity::INVALID);
            }

            // Record translations
            m_sparse[entity_id] = dense_index;
            m_dense_to_sparse.push_back(DenseEntity{entity_id});
            m_dense.emplace_back(std::forward<Args>(args)...);

            return DenseEntity{entity_id};
        }

        // ====================================================================
        // Contains / Validation (O(1))
        // Verify if a handle is currently alive and part of this set.
        // ====================================================================
        bool contains(DenseEntity entity) const noexcept {
            const uint32_t id = entity.get();
            // It must be within tracked range AND not marked invalid AND cross-verified
            if (id < m_sparse.size()) {
                const uint32_t dense_idx = m_sparse[id];
                if (dense_idx < m_dense_to_sparse.size() && m_dense_to_sparse[dense_idx] == entity) {
                    return true;
                }
            }
            return false;
        }

        // ====================================================================
        // Get / Lookup (O(1))
        // Safe access (returns nullptr if deleted).
        // ====================================================================
        T* get(DenseEntity entity) noexcept {
            if (!contains(entity)) return nullptr;
            return &m_dense[m_sparse[entity.get()]];
        }

        const T* get(DenseEntity entity) const noexcept {
            if (!contains(entity)) return nullptr;
            return &m_dense[m_sparse[entity.get()]];
        }

        // ====================================================================
        // Erase: Swap and Pop (O(1))
        // This is the magic. It deletes the item, takes the LAST item in the 
        // array, moves it into the deleted item's spot, and pops the back.
        // ====================================================================
        bool erase(DenseEntity entity) noexcept {
            if (!contains(entity)) return false;

            const uint32_t id_to_delete = entity.get();
            const uint32_t hole_dense_index = m_sparse[id_to_delete];

            // 1. Invalidate the sparse entry for the victim
            m_sparse[id_to_delete] = DenseEntity::INVALID;

            const uint32_t last_dense_index = static_cast<uint32_t>(m_dense.size() - 1);

            // 2. If the victim is NOT the last element, we must physically move the last element into the hole
            if (hole_dense_index != last_dense_index) {
                // Move data
                m_dense[hole_dense_index] = std::move(m_dense[last_dense_index]);

                // Which entity was living at that last spot?
                DenseEntity displaced_entity = m_dense_to_sparse[last_dense_index];

                // Track it in the reverse map
                m_dense_to_sparse[hole_dense_index] = displaced_entity;

                // Tell the sparse map that the displaced entity has a new home coordinates
                m_sparse[displaced_entity.get()] = hole_dense_index;
            }

            // 3. The back of the array is now either garbage or holds the victim. Pop it!
            m_dense.pop_back();
            m_dense_to_sparse.pop_back();

            return true;
        }
    };

} // namespace fk
