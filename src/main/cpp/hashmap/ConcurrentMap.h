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

#include <concepts>
#include <optional>

template<typename MAP, typename K, typename V>
concept ConcurrentMap =

    // Keys and values must be at least moveable or copyable
    (std::move_constructible<K> || std::copy_constructible<K>) &&
    (std::move_constructible<V> || std::copy_constructible<V>) &&

        requires(MAP& map, const MAP& cmap, K& key, const K& ckey, V& val, const V& cval) {

            // 1. Basic methods
            { cmap.get(ckey) } -> std::same_as<std::optional<V>>;
            { cmap.contains(ckey) } -> std::same_as<bool>;
            { map.add(ckey, std::move(val)) } -> std::same_as<std::optional<V>>;
            { map.add(std::move(key), std::move(val)) } -> std::same_as<std::optional<V>>;
            { map.remove(ckey) } -> std::same_as<std::optional<V>>;

            // 2. Size, capacity & management
            { cmap.size() } noexcept -> std::convertible_to<std::size_t>;
            { map.clear() } noexcept;
            { map.reserve(std::declval<std::size_t>()) };

            // 3. Inspection & updates (lambdas / callbacks)
            { cmap.inspect(ckey, [](const V&) {}) } -> std::same_as<bool>;
            { map.update(ckey, std::declval<void(*)(V&)>()) } -> std::same_as<bool>;
            { map.updateIf(std::declval<bool(*)(const K&, V&)>()) } -> std::convertible_to<std::size_t>;
            { map.removeIf(std::declval<bool(*)(const K&, const V&)>()) } -> std::convertible_to<std::size_t>;

            // 4. The most challenging: inspect2 (including "flattening" of nested optionals)
            { cmap.inspect2(ckey, std::declval<void(*)(const V&)>()) } -> std::same_as<bool>;
            { cmap.inspect2(ckey, std::declval<int(*)(const V&)>()) } -> std::same_as<std::optional<int>>;
            { cmap.inspect2(ckey, std::declval<std::optional<int>(*)(const V&)>()) } -> std::same_as<std::optional<int>>;

            // 5. Java-style operations
            { map.merge(ckey, std::declval<V>(), std::declval<std::optional<V>(*)(V&, V&&)>()) };
            { map.computeIfAbsent(ckey, std::declval<V(*)()>(), std::declval<void(*)(V&)>()) };
            { map.computeIfAbsent2(ckey, std::declval<V(*)()>()) } -> std::same_as<std::optional<V>>;
            { cmap.getOrDefault(ckey, std::declval<bool&>(), std::declval<V>()) } -> std::same_as<V>;
            { cmap.forEach(std::declval<void(*)(const K&, const V&)>()) };

            // 6. extra methods
            { map.tryAdd(ckey, std::declval<V>()) } -> std::same_as<bool>;
            { cmap.containsIf(std::declval<bool(*)(const K&, const V&)>()) } -> std::same_as<bool>;
            { cmap.find(std::declval<bool(*)(const K&, const V&)>()) } -> std::same_as<std::optional<V>>;
            { cmap.forEachUntil(std::declval<bool(*)(const K&, const V&)>()) };
};
