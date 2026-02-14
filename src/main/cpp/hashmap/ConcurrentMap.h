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

template<typename MAP, typename K, typename V, typename RET>
concept ConcurrentMap =

    // Keys and values must be destructible
    std::destructible<K> &&
    std::destructible<V> &&
    // Keys and values must be at least moveable or copyable
    (std::move_constructible<K> || std::copy_constructible<K>) &&
    (std::move_constructible<V> || std::copy_constructible<V>) &&

        requires(MAP& map, const MAP& cmap, const K& key, V && val) {

            // 1. Basic methods
            { cmap.get(key) } -> std::same_as<std::optional<V>>;
            { cmap.contains(key) } -> std::same_as<bool>;
            { map.add(key, std::forward<V>(val)) } -> std::same_as<std::optional<V>>;
            { map.add(std::declval<K&&>(), std::forward<V>(val)) } -> std::same_as<std::optional<V>>;
            { map.remove(key) } -> std::same_as<std::optional<V>>;

            // 2. Size, capacity & management
            { cmap.size() } noexcept -> std::convertible_to<std::size_t>;
            { map.clear() } noexcept;
            { map.reserve(std::declval<std::size_t>()) };

            // 3. Inspection & updates (lambdas / callbacks)
            { cmap.inspect(key, [](const V&) {}) } -> std::same_as<bool>;
            { map.update(key, [](V&) {}) } -> std::same_as<bool>;
            { map.updateIf([](const K&, V&) { return true; }) } -> std::convertible_to<std::size_t>;
            { map.removeIf([](const K&, const V&) { return true; }) } -> std::convertible_to<std::size_t>;

            // 4. The most challenging: inspect2 (including "flattening" of nested optionals)
            { cmap.inspect2(key, [](const V&) {}) } -> std::same_as<bool>;
            { cmap.inspect2(key, [](const V&) { return RET{}; }) } -> std::same_as<std::optional<RET>>;
            { cmap.inspect2(key, [](const V&) { return std::optional<RET>{}; }) } -> std::same_as<std::optional<RET>>;

            // 5. Java-style operations
            {
                map.merge(key, std::forward<V>(val), [](V& current, V&& next) {
                    return std::optional<V>{next};
                })
            };
            { map.computeIfAbsent(key, []() { return V{}; }, [](V&) {}) };
            { map.computeIfAbsent2(key, []() { return V{}; }) } -> std::same_as<std::optional<V>>;
            { cmap.getOrDefault(key, std::declval<bool&>(), std::declval<V>()) } -> std::same_as<V>;
            { cmap.forEach([](const K&, const V&) {}) };

            // 6. extra methods
            { map.tryAdd(key, std::declval<V>()) } -> std::same_as<bool>;
            { cmap.containsIf([](const K&, const V&) { return true; }) } -> std::same_as<bool>;
            { cmap.find([](const K&, const V&) { return true; }) } -> std::same_as<std::optional<V>>;
            { cmap.forEachUntil([](const K&, const V&) { return false; }) };
};
