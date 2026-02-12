/*
 * Copyright 2026 Stefan Zobel
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

#include <unordered_map>
#include <shared_mutex>
#include <mutex>
#include <array>
#include <optional>
#include <memory>
#include <memory_resource> // for pmr::
#include <concepts>

// A minimal (hopefully) thread-safe hash map with value semantics and a Java-like interface.

namespace detail {
    // Checks whether a type is std::optional
    template<typename T> struct is_optional : std::false_type {};
    template<typename T> struct is_optional<std::optional<T>> : std::true_type {};
    template<typename T> constexpr bool is_optional_v = is_optional<T>::value;
    // Extract the inner type or keep the type itself
    template<typename T> struct extract_optional_type { using type = T; };
    template<typename T> struct extract_optional_type<std::optional<T>> { using type = T; };
}


template<typename F, typename Key, typename Val>
concept MergeCallback = std::invocable<F, Val&, Val&&>
                     && std::convertible_to<std::invoke_result_t<F, Val&, Val&&>, std::optional<Val>>;

template<typename CREATE_FUNC, typename ACCESS_FUNC, typename V>
concept ComputeIfAbsentFuncs = std::invocable<CREATE_FUNC>
                            && std::convertible_to<std::invoke_result_t<CREATE_FUNC>, V>
                            && std::invocable<ACCESS_FUNC, V&>;

template<typename FUNC, typename K, typename V>
concept ForEachUntil = std::invocable<FUNC, const K&, const V&>
                    && std::convertible_to<std::invoke_result_t<FUNC, const K&, const V&>, bool>;

template<typename FUNC, typename K, typename V>
concept SearchPredicate = std::invocable<FUNC, const K&, const V&>
                       && std::convertible_to<std::invoke_result_t<FUNC, const K&, const V&>, bool>;

template<typename FUNC, typename K, typename V>
concept UpdatePredicate = std::invocable<FUNC, const K&, V&>
                       && std::convertible_to<std::invoke_result_t<FUNC, const K&, V&>, bool>;


template<typename K, typename V, unsigned int SLOT_SIZE = 32, unsigned int BUCKET_SIZE = 16>
class HashMap5 {
    static_assert(SLOT_SIZE > 0, "SLOT_SIZE must be > 0.");
public:

    HashMap5() {}
    HashMap5(const HashMap5&) = delete;
    HashMap5(HashMap5&&) = delete;

    std::optional<V> get(const K& key) const {
        return slotFor(key).get(key);
    }

    template<typename VALUE_TYPE>
        requires std::convertible_to<VALUE_TYPE, V>
    V getOrDefault(const K& key, bool& found, VALUE_TYPE && defaultValue) const {
        return slotFor(key).getOrDefault(key, found, std::forward<VALUE_TYPE>(defaultValue));
    }

    template<typename FUNC>
        requires std::invocable<FUNC, const V&>
    bool inspect(const K& key, FUNC && callback) const {
        return slotFor(key).inspect(key, std::forward<FUNC>(callback));
    }

    template<typename FUNC>
        requires std::invocable<FUNC, V&>
    bool update(const K& key, FUNC && callback) {
        return slotFor(key).update(key, std::forward<FUNC>(callback));
    }

    template<typename PREDICATE>
        requires UpdatePredicate<PREDICATE, K, V>
    size_t updateIf(PREDICATE && predicate) {
        size_t totalUpdated = 0;
        for (auto& slot : slots) {
            totalUpdated += slot.updateIf(predicate);
        }
        return totalUpdated;
    }

    template<typename FUNC>
        requires std::invocable<FUNC, const V&>
    auto inspect2(const K& key, FUNC && callback) const {
        return slotFor(key).inspect2(key, std::forward<FUNC>(callback));
    }

    template<typename CREATE_FUNC, typename ACCESS_FUNC>
        requires ComputeIfAbsentFuncs<CREATE_FUNC, ACCESS_FUNC, V>
    void computeIfAbsent(const K& key, CREATE_FUNC && create, ACCESS_FUNC && access) {
        slotFor(key).computeIfAbsent(key, std::forward<CREATE_FUNC>(create), access);
    }

    template<typename FUNC>
        requires std::invocable<FUNC>&& std::convertible_to<std::invoke_result_t<FUNC>, V>
    std::optional<V> computeIfAbsent2(const K& key, FUNC&& createFunction) {
        return slotFor(key).computeIfAbsent2(key, std::forward<FUNC>(createFunction));
    }

    template<MergeCallback<K, V> FUNC>
    void merge(const K& key, V && value, FUNC && remappingFunction) {
        slotFor(key).merge(key, std::forward<V>(value), std::forward<FUNC>(remappingFunction));
    }

    std::optional<V> remove(const K& key) {
        return slotFor(key).remove(key);
    }

    template<typename PREDICATE>
        requires SearchPredicate<PREDICATE, K, V>
    size_t removeIf(PREDICATE && predicate) {
        size_t totalRemoved = 0;
        for (auto& slot : slots) {
            totalRemoved += slot.removeIf(predicate);
        }
        return totalRemoved;
    }

    template<std::convertible_to<V> VALUE_TYPE>
    std::optional<V> add(const K& key, VALUE_TYPE && value) {
        return slotFor(key).add(key, std::forward<VALUE_TYPE>(value));
    }

    template<std::convertible_to<V> VALUE_TYPE>
    std::optional<V> add(K&& key, VALUE_TYPE && value) {
        return slotFor(key).add(std::move(key), std::forward<VALUE_TYPE>(value));
    }

    template<typename VALUE_TYPE>
        requires std::convertible_to<VALUE_TYPE, V>
    bool tryAdd(K key, VALUE_TYPE&& value) {
        return slotFor(key).tryAdd(std::move(key), std::forward<VALUE_TYPE>(value));
    }

    template<typename FUNC>
        requires std::invocable<FUNC, const K&, const V&>
    void forEach(FUNC&& callback) const {
        for (const auto& slot : slots) {
            slot.forEach(callback);
        }
    }

    bool contains(const K& key) const {
        return slotFor(key).contains(key);
    }

    template<typename PREDICATE>
        requires SearchPredicate<PREDICATE, K, V>
    bool containsIf(PREDICATE&& predicate) const {
        bool found = false;
        // Use our own forEachUntil
        this->forEachUntil([&](const K& key, const V& value) -> bool {
            if (std::invoke(predicate, key, value)) {
                found = true;
                return true; // break
            }
            return false; // continue
        });

        return found;
    }

    template<typename PREDICATE>
        requires SearchPredicate<PREDICATE, K, V>
    std::optional<V> find(PREDICATE&& predicate) const {
        for (const auto& slot : slots) {
            auto result = slot.find(predicate);
            if (result.has_value()) {
                return result; // we found it
            }
        }
        return std::nullopt;
    }

    template<typename FUNC>
    void forEachUntil(FUNC&& func) const {
        for (const auto& slot : slots) {
            if (!slot.forEachUntil(func)) {
                break;
            }
        }
    }

    size_t size() const noexcept {
        size_t total = 0;
        for (const auto& slot : slots) {
            total += slot.size();
        }
        return total;
    }

    void clear() noexcept {
        for (auto& slot : slots) {
            slot.clear();
        }
    }

    void reserve(size_t hint) {
        const size_t perSlotHint = (hint / SLOT_SIZE) + (hint % SLOT_SIZE > 0 ? 1 : 0);
        for (auto& slot : slots) {
            slot.reserve(perSlotHint);
        }
    }

private:
    static constexpr size_t hardware_destructive_interference_size = 64;

    class alignas(hardware_destructive_interference_size) Slot {
    public:
        Slot() : local_pool(), map(BUCKET_SIZE, &local_pool) {}

        std::optional<V> get(const K& key) const {
            std::shared_lock lock(mutex);
            if (auto it = map.find(key); it != map.end()) {
                return it->second;
            }
            return std::nullopt;
        }

        template<typename VALUE_TYPE>
            requires std::convertible_to<VALUE_TYPE, V>
        V getOrDefault(const K& key, bool& found, VALUE_TYPE && defaultValue) const {
            std::shared_lock lock(mutex);
            auto it = map.find(key);
            if (it != map.end()) {
                found = true;
                return it->second;
            }
            found = false;
            return std::forward<VALUE_TYPE>(defaultValue);
        }

        template<typename FUNC>
            requires std::invocable<FUNC, const V&>
        bool inspect(const K& key, FUNC && callback) const {
            std::shared_lock lock(mutex);
            if (auto it = map.find(key); it != map.end()) {
                std::invoke(std::forward<FUNC>(callback), it->second);
                return true;
            }
            return false;
        }

        template<typename PREDICATE>
            requires UpdatePredicate<PREDICATE, K, V>
        size_t updateIf(PREDICATE && predicate) {
            std::unique_lock lock(mutex);
            size_t updatedCount = 0;
            for (auto& [key, value] : map) {
                if (std::invoke(predicate, key, value)) {
                    updatedCount++;
                }
            }
            return updatedCount;
        }

        template<typename FUNC>
            requires std::invocable<FUNC, V&>
        bool update(const K& key, FUNC && callback) {
            std::unique_lock lock(mutex);
            if (auto it = map.find(key); it != map.end()) {
                std::invoke(std::forward<FUNC>(callback), it->second);
                return true;
            }
            return false;
        }

        template<typename FUNC>
            requires std::invocable<FUNC, const V&>
        auto inspect2(const K& key, FUNC&& callback) const {
            using RawReturnType = std::invoke_result_t<FUNC, const V&>;

            std::shared_lock lock(mutex);
            auto it = map.find(key);

            // case 1: callback returns void
            if constexpr (std::is_void_v<RawReturnType>) {
                if (it != map.end()) {
                    std::invoke(std::forward<FUNC>(callback), it->second);
                    return true; // key found
                }
                return false; // key not found
            }
            // cases 2 & 3: callback returns something
            else {
                if (it != map.end()) {
                    auto result = std::invoke(std::forward<FUNC>(callback), it->second);

                    // if we get an optional as a return we return it 1:1
                    if constexpr (detail::is_optional_v<RawReturnType>) {
                        return result;
                    }
                    else {
                        // otherwise we wrap the return in an optional
                        return std::optional<RawReturnType>(std::move(result));
                    }
                }
                // key not found: return an empty optional of the type that the user expects
                using FinalReturnType = typename detail::extract_optional_type<RawReturnType>::type;
                return std::optional<FinalReturnType>{std::nullopt};
            }
        }

        template<typename CREATE_FUNC, typename ACCESS_FUNC>
            requires ComputeIfAbsentFuncs<CREATE_FUNC, ACCESS_FUNC, V>
        void computeIfAbsent(const K& key, CREATE_FUNC && createFunction, ACCESS_FUNC && accessCallback) {
            std::unique_lock lock(mutex);
            auto it = map.find(key);

            if (it == map.end()) {
                // expensive creation only when necessary
                auto [newIt, inserted] = map.emplace(key, std::invoke(std::forward<CREATE_FUNC>(createFunction)));
                it = newIt;
            }

            // access is safe (we still hold the lock)
            std::invoke(std::forward<ACCESS_FUNC>(accessCallback), it->second);
        }

        template<typename FUNC>
            requires std::invocable<FUNC>&& std::convertible_to<std::invoke_result_t<FUNC>, V>
        std::optional<V> computeIfAbsent2(const K& key, FUNC&& createFunction) {
            std::unique_lock lock(mutex);
            auto it = map.find(key);

            if (it == map.end()) {
                auto [newIt, inserted] = map.emplace(key, std::invoke(std::forward<FUNC>(createFunction)));
                return newIt->second;
            }

            return it->second;
        }

        template<MergeCallback<K, V> FUNC>
        void merge(const K& key, V && value, FUNC && remappingFunction) {
            std::unique_lock lock(mutex);
            auto it = map.find(key);

            if (it != map.end()) {
                // the returned optional of 'remappingFunction' decides on the new value or deletion
                std::optional<V> result = std::invoke(std::forward<FUNC>(remappingFunction), it->second, std::forward<V>(value));

                if (result.has_value()) {
                    it->second = std::move(*result);
                }
                else {
                    map.erase(it); // delete if returned std::optional is empty
                }
            }
            else {
                map.emplace(key, std::forward<V>(value));
            }
        }

        std::optional<V> remove(const K& key) {
            std::unique_lock lock(mutex);
            if (auto it = map.find(key); it != map.end()) {
                std::optional<V> old = std::move(it->second);
                map.erase(it);
                return old;
            }
            return std::nullopt;
        }

        template<typename PREDICATE>
            requires SearchPredicate<PREDICATE, K, V>
        size_t removeIf(PREDICATE && predicate) {
            std::unique_lock lock(mutex);
            // the predicate receives (key, value) pairs
            return std::erase_if(map, [&](const auto& item) {
                // item is a std::pair<const K, V>
                return std::invoke(predicate, item.first, item.second);
            });
        }

        template<std::convertible_to<V> VALUE_TYPE>
        std::optional<V> add(K key, VALUE_TYPE && value) {
            std::unique_lock lock(mutex);
            auto it = map.find(key);
            if (it != map.end()) {
                std::optional<V> old = std::move(it->second);
                it->second = std::forward<VALUE_TYPE>(value);
                return old;
            }
            else {
                map.emplace(std::move(key), std::forward<VALUE_TYPE>(value));
                return std::nullopt;
            }
        }

        template<std::convertible_to<V> VALUE_TYPE>
        bool tryAdd(K key, VALUE_TYPE&& value) {
            std::unique_lock lock(mutex);
            // 'inserted' is true when the key was not known
            auto [it, inserted] = map.emplace(std::move(key), std::forward<VALUE_TYPE>(value));
            return inserted;
        }

        template<typename FUNC>
            requires std::invocable<FUNC, const K&, const V&>
        void forEach(FUNC && callback) const {
            std::shared_lock lock(mutex);
            for (const auto& [key, value] : map) {
                std::invoke(callback, key, value);
            }
        }

        template<typename FUNC>
            requires ForEachUntil<FUNC, K, V>
        bool forEachUntil(FUNC && callback) const {
            std::shared_lock lock(mutex);
            for (const auto& [key, value] : map) {
                if (std::invoke(callback, key, value)) return false;
            }
            return true;
        }

        bool contains(const K& key) const {
            std::shared_lock lock(mutex);
            return map.contains(key);
        }

        template<typename PREDICATE>
            requires SearchPredicate<PREDICATE, K, V>
        std::optional<V> find(PREDICATE && predicate) const {
            std::optional<V> result;
            // capture result by reference
            this->forEachUntil([&](const K& key, const V& value) -> bool {
                if (std::invoke(predicate, key, value)) {
                    result = value;
                    return true;
                }
                return false;
            });

            return result;
        }

        size_t size() const noexcept {
            std::shared_lock lock(mutex);
            return map.size();
        }

        void clear() noexcept {
            std::unique_lock lock(mutex);
            map.clear();
        }

        void reserve(size_t hint) {
            std::unique_lock lock(mutex);
            map.reserve(hint);
        }

    private:
        std::pmr::unsynchronized_pool_resource local_pool;
        std::pmr::unordered_map<K, V> map;
        mutable std::shared_mutex mutex;
    };


    Slot& slotFor(const K& key) noexcept {
        if constexpr (SIZE_IS_POW2) {
            return slots[finalize(hash(key)) & (SLOT_SIZE - 1)];
        }
        else {
            return slots[finalize(hash(key)) % SLOT_SIZE];
        }
    }

    const Slot& slotFor(const K& key) const noexcept {
        if constexpr (SIZE_IS_POW2) {
            return slots[finalize(hash(key)) & (SLOT_SIZE - 1)];
        }
        else {
            return slots[finalize(hash(key)) % SLOT_SIZE];
        }
    }

    [[nodiscard]] static size_t finalize(size_t h) noexcept {
        h = (h ^ (h >> 30)) * 0xbf58476d1ce4e5b9ULL;
        h = (h ^ (h >> 27)) * 0x94d049bb133111ebULL;
        h = h ^ (h >> 31);
        return h;
    }

    constexpr static bool SIZE_IS_POW2 = (SLOT_SIZE && ((SLOT_SIZE & (SLOT_SIZE - 1)) == 0));

    std::array<Slot, SLOT_SIZE> slots {};
    std::hash<K> hash{};

    static_assert(sizeof(Slot) % alignof(Slot) == 0, "Wrong padding. Compiler Error?");
    static_assert(alignof(Slot) >= hardware_destructive_interference_size, "Under-alignment: may cause false sharing!");
};
