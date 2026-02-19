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

// Bitwise operators for scoped enums

// A helper template to decide which enums are allowed to have bitwise operators
template<typename E>
struct is_bitmask_enum : std::false_type {};

// Bitwise operators
template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E operator|(E lhs, E rhs) {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) | static_cast<T>(rhs));
}

template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E operator&(E lhs, E rhs) {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) & static_cast<T>(rhs));
}

template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E operator^(E lhs, E rhs) {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) ^ static_cast<T>(rhs));
}

template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E operator~(E lhs) {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(~static_cast<T>(lhs));
}

// Compound assignment operators
template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E& operator|=(E& lhs, E rhs) {
    lhs = lhs | rhs;
    return lhs;
}

template<typename E>
    requires is_bitmask_enum<E>::value
constexpr E& operator&=(E& lhs, E rhs) {
    lhs = lhs & rhs;
    return lhs;
}

// Syntax explanation (((value & flags) == flags) && ...)
// That's a "Unary Left Fold Expression".
// When you call has_all(val, A, B, C), it gets expanded to:
// ((val & A) == A) && ((val & B) == B) && ((val & C) == C)
/**
 * Checks whether ALL passed flags are set in 'value'
 */
template<typename E, typename... Args>
    requires is_bitmask_enum<E>::value && (std::same_as<E, Args> && ...)
constexpr bool has_all(E value, Args... flags) {
    // Fold expression combines all comparisons with &&
    return (((value & flags) == flags) && ...);
}

/**
 * Checks whether AT LEAST ONE of the passed flags is set in 'value'
 */
template<typename E, typename... Args>
    requires is_bitmask_enum<E>::value && (std::same_as<E, Args> && ...)
constexpr bool has_any(E value, Args... flags) {
    // check whether the bitwise comparison is not equal to 0
    return (((value & flags) != E{ 0 }) || ...);
}

#define ENABLE_BITMASK_OPERATORS(E) \
    template<> struct is_bitmask_enum<E> : std::true_type {};

