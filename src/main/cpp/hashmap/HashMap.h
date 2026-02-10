/*
 * Copyright 2025 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
#pragma once

#include <array>
#include <unordered_map>

#include <memory>
#include <shared_mutex>
#include <concepts>

// A minimal (hopefully) thread-safe hash map with a Java-like interface.

// Note 1: the noexcept claims are a bit exaggerated since std::unordered_map find() could actually throw
// because std::equal_to<K> can throw depending on the type K. But when will that ever happen?

// Note 2: using shared_lock is enough for the read-only operation find() because this is const and
// const member functions in the C++ containers library can be called concurrently by different threads.
// https://en.cppreference.com/w/cpp/container.html#Thread_safety


template<typename K, typename V, unsigned int SLOT_SIZE = 32, unsigned int BUCKET_SIZE = 16>
class HashMap {
    static_assert(SLOT_SIZE > 0, "SLOT_SIZE must be > 0.");
public:

    std::shared_ptr<V> get(const K& key) noexcept {
        return slotFor(key).get(key);
    }
    std::shared_ptr<V> remove(const K& key) noexcept {
        return slotFor(key).remove(key);
    }
    template<std::convertible_to<V> VALUE_TYPE>
    std::shared_ptr<V> add(const K& key, VALUE_TYPE && value) {
        return slotFor(key).add(key, std::forward<VALUE_TYPE>(value));
    }
    template<std::convertible_to<V> VALUE_TYPE>
    std::shared_ptr<V> add(K && key, VALUE_TYPE && value) {
        return slotFor(key).add(std::move(key), std::forward<VALUE_TYPE>(value));
    }
    bool contains(const K& key) noexcept {
        return slotFor(key).contains(key);
    }
    // best effort, does not acquire a global lock for all slots
    size_t size() const noexcept {
        size_t total = 0;
        for (const auto& slot : slots) {
            total += slot.size();
        }
        return total;
    }
    // best effort, does not acquire a global lock for all slots
    void clear() noexcept {
        for (auto& slot : slots) {
            slot.clear();
        }
    }

private:

    class Slot {
    public:

        std::shared_ptr<V> get(const K& key) const noexcept {
            std::shared_lock lock(mutex);
            auto it = map.find(key);
            if (it != map.end()) {
                return std::make_shared<V>(it->second);
            }
            return {};
        }
        std::shared_ptr<V> remove(const K& key) noexcept {
            std::unique_lock lock(mutex);
            auto it = map.find(key);
            if (it != map.end()) {
                auto value = std::make_shared<V>(it->second);
                map.erase(it);
                return value;
            }
            return {};
        }
        template<class VALUE_TYPE>
        std::shared_ptr<V> add(const K& key, VALUE_TYPE && value) {
            std::unique_lock lock(mutex);
            auto it = map.find(key);
            if (it != map.end()) {
                auto old = it->second;
                it->second = std::forward<VALUE_TYPE>(value);
                return std::make_shared<V>(old);
            } else {
                map.insert_or_assign(key, std::forward<VALUE_TYPE>(value));
                return {};
            }
        }
        bool contains(const K& key) const noexcept {
            std::shared_lock lock(mutex);
            return map.find(key) != map.end();
        }
        size_t size() const noexcept {
            std::shared_lock lock(mutex);
            return map.size();
        }
        void clear() noexcept {
            std::unique_lock lock(mutex);
            map.clear();
        }
    private:
        std::unordered_map<K, V> map{ BUCKET_SIZE };
        mutable std::shared_mutex mutex;
    };

    Slot& slotFor(K const& key) noexcept {
        if constexpr (SIZE_IS_POW2) {
            return slots[hash(key) & (SLOT_SIZE - 1)];
        } else {
            return slots[hash(key) % SLOT_SIZE];
        }
    }

    constexpr static bool SIZE_IS_POW2 = (SLOT_SIZE && ((SLOT_SIZE & (SLOT_SIZE - 1)) == 0));

    std::array<Slot, SLOT_SIZE> slots{};
    const std::hash<K> hash{};
};
