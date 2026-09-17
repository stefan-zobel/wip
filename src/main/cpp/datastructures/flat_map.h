#pragma once

#include <vector>
#include <cstdint>
#include <cstddef>
#include <functional>
#include <stdexcept>
#include <utility>
#include <memory>
#include <new>

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

        // The hash is mixed first (std::hash<int> is the identity with libstdc++). The slot index
        // uses the low bits, the 7-bit tag (h7) the top 7 bits: if both used the same bits, all
        // entries of a home slot would share their tag and the tag would not filter anything.
        // Stafford's "variant 13" mixer, as in hashmap/HashMix.h
        static constexpr size_t mix_hash(size_t h) noexcept {
            h = (h ^ (h >> 30)) * 0xbf58476d1ce4e5b9ULL;
            h = (h ^ (h >> 27)) * 0x94d049bb133111ebULL;
            return h ^ (h >> 31);
        }

        static constexpr uint8_t tag_of(size_t mixed_hash) noexcept {
            return static_cast<uint8_t>(mixed_hash >> (sizeof(size_t) * 8 - 7));  // always < CTRL_EMPTY
        }

        // The Metadata Array. 
        // 1 byte completely governs 1 slot of the data array.
        // It's exceptionally dense (A 64-byte Cache Line covers 64 slots of probing).
        std::vector<uint8_t> m_ctrl;

        // Using a dynamically allocated raw-byte buffer to completely avoid requiring 
        // a Default Constructor for Key or Value during capacity allocations.
        // The buffer is allocated with the alignment of Slot (new[] of std::byte would not be
        // aligned for over-aligned keys or values).
        using Slot = std::pair<Key, Value>;
        struct AlignedDelete {
            void operator()(std::byte* memory) const noexcept {
                ::operator delete(memory, std::align_val_t{ alignof(Slot) });
            }
        };
        using SlotMemory = std::unique_ptr<std::byte, AlignedDelete>;
        SlotMemory m_data_memory;
        
        size_t m_capacity = 0;
        size_t m_size = 0;
        // Number of CTRL_DELETED slots. Tombstones count against the load factor,
        // otherwise a table without any CTRL_EMPTY slot makes the probe loops spin forever.
        size_t m_tombstones = 0;

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

        // Diagnostics (tests): the slot where the probe sequence of 'key' starts.
        size_t home_slot(const Key& key) const noexcept {
            return mix_hash(Hash{}(key)) & (m_capacity - 1);
        }

        // ====================================================================
        // Emplace / Insertion
        // ====================================================================
        template <typename K, typename V>
        bool emplace(K&& key, V&& value) {
            if (m_size + m_tombstones >= max_load()) {
                // If mostly tombstones fill the table, rehashing at the same capacity is enough
                // to purge them; only grow when the live elements themselves need the room.
                rehash(m_size + 1 > max_load() / 2 ? m_capacity * 2 : m_capacity);
            }

            const size_t mixed_hash = mix_hash(Hash{}(key));
            const uint8_t h7 = tag_of(mixed_hash);

            // Bitwise modulo using power-of-2 capacity
            size_t idx = mixed_hash & (m_capacity - 1);
            size_t first_deleted_idx = static_cast<size_t>(-1);
            
            Slot* slots = data_slots();

            // Linear Probing through the ultra-fast control array
            while (true) {
                uint8_t ctrl_byte = m_ctrl[idx];

                if (ctrl_byte == CTRL_EMPTY) {
                    // Spot is entirely pristine. Insert here (or in a prior tombstone if found).
                    const size_t target_idx = (first_deleted_idx != static_cast<size_t>(-1)) ? first_deleted_idx : idx;
                    insert_at(target_idx, h7, std::forward<K>(key), std::forward<V>(value));
                    if (target_idx == first_deleted_idx) {
                        m_tombstones--;  // only after a successful insertion
                    }
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
            const size_t mixed_hash = mix_hash(Hash{}(key));
            const uint8_t h7 = tag_of(mixed_hash);
            size_t idx = mixed_hash & (m_capacity - 1);

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
            const size_t mixed_hash = mix_hash(Hash{}(key));
            const uint8_t h7 = tag_of(mixed_hash);
            size_t idx = mixed_hash & (m_capacity - 1);

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
                    m_tombstones++;
                    return true;
                }

                idx = (idx + 1) & (m_capacity - 1);
            }
        }

    private:
        // Allocates raw uninitialized bytes. No Default Constructors are called.
        static SlotMemory allocate_slots(size_t cap) {
            return SlotMemory(static_cast<std::byte*>(
                ::operator new(cap * sizeof(Slot), std::align_val_t{ alignof(Slot) })));
        }

        // Only for a map without elements (constructors).
        void initialize_arrays(size_t cap) {
            // Fill metadata completely with EMPTY flag
            m_ctrl.assign(cap, CTRL_EMPTY);
            m_data_memory = allocate_slots(cap);
            m_capacity = cap;
            m_tombstones = 0;
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
            Slot* slots = data_slots();
            // Placement new constructs the Pair strictly inside the raw byte buffer
            new (&slots[pos].first) Key(std::forward<K>(key));
            try {
                new (&slots[pos].second) Value(std::forward<V>(value));
            } catch (...) {
                slots[pos].first.~Key();
                throw;
            }

            // The slot counts as used only once key and value are constructed
            m_ctrl[pos] = h7;
            m_size++;
        }

        // Extremely expensive, but physically required to eliminate Tombstones 
        // and expand the universe safely.
        // Basic exception guarantee: if allocating the new arrays throws, the map is unchanged. If
        // moving an element throws, the map keeps the elements moved so far, the others are
        // destroyed (lost), and the exception propagates.
        void rehash(size_t new_cap) {
            std::vector<uint8_t> new_ctrl(new_cap, CTRL_EMPTY);
            SlotMemory new_data_memory = allocate_slots(new_cap);

            std::vector<uint8_t> old_ctrl = std::exchange(m_ctrl, std::move(new_ctrl));
            SlotMemory old_data_memory = std::exchange(m_data_memory, std::move(new_data_memory));
            const size_t old_cap = std::exchange(m_capacity, new_cap);
            Slot* old_slots = reinterpret_cast<Slot*>(old_data_memory.get());
            m_size = 0;
            m_tombstones = 0;

            size_t i = 0;
            try {
                for (; i < old_cap; ++i) {
                    // If it wasn't empty or deleted, it contains a living H7 payload
                    if (old_ctrl[i] < CTRL_EMPTY) {
                        // Re-insert via Move-Semantics. This strips all tombstones out of existence implicitly.
                        emplace(std::move(old_slots[i].first), std::move(old_slots[i].second));

                        // Destroy the extracted old payload
                        old_slots[i].first.~Key();
                        old_slots[i].second.~Value();
                    }
                }
            } catch (...) {
                // Element i was not inserted; destroy it and all elements not yet moved
                for (; i < old_cap; ++i) {
                    if (old_ctrl[i] < CTRL_EMPTY) {
                        old_slots[i].first.~Key();
                        old_slots[i].second.~Value();
                    }
                }
                throw;
            }
        }
    };

} // namespace fk
