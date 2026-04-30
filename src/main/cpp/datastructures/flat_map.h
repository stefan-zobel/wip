#pragma once

#include <vector>
#include <cstdint>
#include <cstddef>
#include <functional>
#include <stdexcept>
#include <utility>
#include <memory>

namespace fk {

    // ========================================================================
    // SwissFlatMap (Metadata-Accelerated Open Addressing Hash Map)
    //
    // Unlike standard Open Addressing (which thrashes the cache pulling vast 
    // keys for probing), this map maintains an isolated, ultra-dense 
    // 1-byte control array. Lookups scan ONLY this 1-byte array for 7-bit hash 
    // signatures (h7).
    //
    // Guaranteed to support types WITHOUT default constructors.
    // ========================================================================
    template <typename Key, typename Value, typename Hash = std::hash<Key>, typename KeyEqual = std::equal_to<Key>>
    class SwissFlatMap final {
    private:
        // --- Control Bytes Magic Constants ---
        static constexpr uint8_t CTRL_EMPTY   = 0b1000'0000; // 0x80 (128) - Never touched
        static constexpr uint8_t CTRL_DELETED = 0b1111'1110; // 0xFE (254) - Tombstone

        // H7 Mask: Isolates the lower 7 bits of a full 64-bit hash
        static constexpr size_t  H7_MASK      = 0b0111'1111; // 0x7F (127)

        // The Metadata Array. 
        // 1 byte completely governs 1 slot of the data array.
        // It's exceptionally dense (A 64-byte Cache Line covers 64 slots of probing).
        std::vector<uint8_t> m_ctrl;

        // Using a dynamically allocated raw-byte buffer to completely avoid requiring 
        // a Default Constructor for Key or Value during capacity allocations.
        using Slot = std::pair<Key, Value>;
        std::unique_ptr<std::byte[]> m_data_memory;
        
        size_t m_capacity = 0;
        size_t m_size = 0;

        Slot* data_slots() noexcept {
            return reinterpret_cast<Slot*>(m_data_memory.get());
        }

        const Slot* data_slots() const noexcept {
            return reinterpret_cast<const Slot*>(m_data_memory.get());
        }

        // The Load Factor limit (Google's Abseil utilizes exactly 0.875)
        // (7/8) * Capacity
        size_t max_load() const noexcept {
            return (m_capacity * 7) / 8;
        }

    public:
        SwissFlatMap() {
            // Must launch with a power-of-2 capacity for bitwise modulo
            initialize_arrays(8);
        }

        explicit SwissFlatMap(size_t initial_capacity) {
            // Force up to nearest power of 2
            size_t cap = 8;
            while (cap < initial_capacity) cap *= 2;
            initialize_arrays(cap);
        }

        ~SwissFlatMap() {
            destroy_all_elements();
        }

        // --- Core Size API ---
        size_t size()     const noexcept { return m_size; }
        bool   empty()    const noexcept { return m_size == 0; }
        size_t capacity() const noexcept { return m_capacity; }

        // ====================================================================
        // Emplace / Insertion
        // ====================================================================
        template <typename K, typename V>
        bool emplace(K&& key, V&& value) {
            if (m_size >= max_load()) {
                rehash(m_capacity * 2);
            }

            const size_t full_hash = Hash{}(key);
            const uint8_t h7 = static_cast<uint8_t>(full_hash & H7_MASK);

            // Bitwise modulo using power-of-2 capacity
            size_t idx = full_hash & (m_capacity - 1);
            size_t first_deleted_idx = static_cast<size_t>(-1);
            
            Slot* slots = data_slots();

            // Linear Probing through the ultra-fast control array
            while (true) {
                uint8_t ctrl_byte = m_ctrl[idx];

                if (ctrl_byte == CTRL_EMPTY) {
                    // Spot is entirely pristine. Insert here (or in a prior tombstone if found).
                    const size_t target_idx = (first_deleted_idx != static_cast<size_t>(-1)) ? first_deleted_idx : idx;
                    insert_at(target_idx, h7, std::forward<K>(key), std::forward<V>(value));
                    return true;
                }

                if (ctrl_byte == CTRL_DELETED) {
                    // Remember the first tombstone to recycle it, but WE MUST CONTINUE 
                    // probing to ensure the key isn't actually hiding further down the line!
                    if (first_deleted_idx == static_cast<size_t>(-1)) first_deleted_idx = idx;
                }
                else if (ctrl_byte == h7) {
                    // H7 Match! The 7-bit hash signature matches. 
                    // Now, and only now, do we suffer the cache-miss to check the actual Key.
                    if (KeyEqual{}(slots[idx].first, key)) {
                        // Key strictly exists! Overwrite value or reject. (We overwrite here).
                        slots[idx].second = std::forward<V>(value);
                        return false; 
                    }
                }

                // Probe further (Linear Probing with wrap-around)
                idx = (idx + 1) & (m_capacity - 1);
            }
        }

        // ====================================================================
        // Get / Lookup
        // ====================================================================
        Value* get(const Key& key) noexcept {
            const size_t full_hash = Hash{}(key);
            const uint8_t h7 = static_cast<uint8_t>(full_hash & H7_MASK);
            size_t idx = full_hash & (m_capacity - 1);

            Slot* slots = data_slots();

            while (true) {
                uint8_t ctrl_byte = m_ctrl[idx];

                // If we hit an empty slot, the probe chain is broken. Key doesn't exist.
                if (ctrl_byte == CTRL_EMPTY) {
                    return nullptr; 
                }

                // If the 7-bit metadata matches exactly, check the heavy key.
                if (ctrl_byte == h7 && KeyEqual{}(slots[idx].first, key)) {
                    return &slots[idx].second;
                }

                idx = (idx + 1) & (m_capacity - 1);
            }
        }

        const Value* get(const Key& key) const noexcept {
            return const_cast<SwissFlatMap*>(this)->get(key);
        }

        // ====================================================================
        // Erase (Tombstoning)
        // ====================================================================
        bool erase(const Key& key) noexcept {
            const size_t full_hash = Hash{}(key);
            const uint8_t h7 = static_cast<uint8_t>(full_hash & H7_MASK);
            size_t idx = full_hash & (m_capacity - 1);

            Slot* slots = data_slots();

            while (true) {
                uint8_t ctrl_byte = m_ctrl[idx];

                if (ctrl_byte == CTRL_EMPTY) {
                    return false; 
                }

                if (ctrl_byte == h7 && KeyEqual{}(slots[idx].first, key)) {
                    // DESTROY internal object (e.g. if Value is a std::string or shared_ptr)
                    // Manually destroy the object resident in raw memory
                    slots[idx].first.~Key();
                    slots[idx].second.~Value();

                    // Leave a tombstone so probing chains don't break
                    m_ctrl[idx] = CTRL_DELETED;
                    m_size--;
                    return true;
                }

                idx = (idx + 1) & (m_capacity - 1);
            }
        }

    private:
        void initialize_arrays(size_t cap) {
            m_capacity = cap;

            // Fill metadata completely with EMPTY flag
            m_ctrl.assign(cap, CTRL_EMPTY);

            // Allocate raw uninitialized bytes. No Default Constructors are called.
            m_data_memory = std::make_unique<std::byte[]>(cap * sizeof(Slot));
        }

        void destroy_all_elements() noexcept {
            if (!m_data_memory) return;
            Slot* slots = data_slots();
            for (size_t i = 0; i < m_capacity; ++i) {
                if (m_ctrl[i] < CTRL_EMPTY) { 
                    slots[i].first.~Key();
                    slots[i].second.~Value();
                }
            }
        }

        template <typename K, typename V>
        void insert_at(size_t pos, uint8_t h7, K&& key, V&& value) {
            m_ctrl[pos] = h7;

            Slot* slots = data_slots();
            // Placement new constructs the Pair strictly inside the raw byte buffer
            new (&slots[pos].first) Key(std::forward<K>(key));
            new (&slots[pos].second) Value(std::forward<V>(value));

            m_size++;
        }

        // Extremely expensive, but physically required to eliminate Tombstones 
        // and expand the universe safely.
        void rehash(size_t new_cap) {
            std::vector<uint8_t> old_ctrl = std::move(m_ctrl);
            std::unique_ptr<std::byte[]> old_data_memory = std::move(m_data_memory);

            size_t old_cap = m_capacity;
            Slot* old_slots = reinterpret_cast<Slot*>(old_data_memory.get());

            initialize_arrays(new_cap);
            m_size = 0; 

            for (size_t i = 0; i < old_cap; ++i) {
                // If it wasn't empty or deleted, it contains a living H7 payload
                if (old_ctrl[i] < CTRL_EMPTY) {
                    // Re-insert via Move-Semantics. This strips all tombstones out of existence implicitly.
                    emplace(std::move(old_slots[i].first), std::move(old_slots[i].second));

                    // Destroy the extracted old payload
                    old_slots[i].first.~Key();
                    old_slots[i].second.~Value();
                }
            }
        }
    };

} // namespace fk
