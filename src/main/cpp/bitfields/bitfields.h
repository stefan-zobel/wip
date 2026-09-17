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
#include <type_traits>

// Bitwise operators for scoped enums
//
// Usage: invoke ENABLE_BITMASK_OPERATORS(E) in the namespace that declares the enum E (the global
// namespace for a global enum):
//
//     namespace lib {
//     enum class Flags : uint32_t { None = 0, X = 1, Y = 2 };
//     ENABLE_BITMASK_OPERATORS(Flags)
//     }
//
// The macro defines the operators |, &, ^, ~, |= and &= for E in that namespace, where
// argument-dependent lookup finds them from everywhere, even in a namespace that declares an
// operator| of its own (templates in the global namespace would be hidden there). has_all, has_any
// and create accept every enum for which the macro was invoked this way.

// Marker declared by ENABLE_BITMASK_OPERATORS and found by argument-dependent lookup
template<typename E>
concept BitmaskEnum = std::is_enum_v<E> && requires(E e) {
    { fk_bitmask_enum_marker(e) } -> std::same_as<bool>;
};

namespace bitmask_detail {

template<typename E>
constexpr E bit_or(E lhs, E rhs) noexcept {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) | static_cast<T>(rhs));
}

template<typename E>
constexpr E bit_and(E lhs, E rhs) noexcept {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) & static_cast<T>(rhs));
}

template<typename E>
constexpr E bit_xor(E lhs, E rhs) noexcept {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(static_cast<T>(lhs) ^ static_cast<T>(rhs));
}

template<typename E>
constexpr E bit_not(E value) noexcept {
    using T = std::underlying_type_t<E>;
    return static_cast<E>(~static_cast<T>(value));
}

} // namespace bitmask_detail

// Syntax explanation (((value & flags) == flags) && ...)
// That's a "Unary Left Fold Expression".
// When you call has_all(val, A, B, C), it gets expanded to:
// ((val & A) == A) && ((val & B) == B) && ((val & C) == C)
/**
 * Checks whether ALL passed flags are set in 'value'
 */
template<typename E, typename... Args>
    requires BitmaskEnum<E> && (std::same_as<E, Args> && ...)
constexpr bool has_all(E value, Args... flags) {
    // Fold expression combines all comparisons with &&
    return (((value & flags) == flags) && ...);
}

/**
 * Checks whether AT LEAST ONE of the passed flags is set in 'value'
 */
template<typename E, typename... Args>
    requires BitmaskEnum<E> && (std::same_as<E, Args> && ...)
constexpr bool has_any(E value, Args... flags) {
    // check whether the bitwise comparison is not equal to 0
    return (((value & flags) != E{ 0 }) || ...);
}

/**
 * Creates a new enum value where all the passed flags are set
 */
template<typename E, typename... Args>
    requires BitmaskEnum<E> && (std::same_as<E, Args> && ...)
constexpr E create(Args... flags) {
    // if there are no arguments we return 0 (None)
    if constexpr (sizeof...(Args) == 0) {
        return E{ 0 };
    } else {
        // Fold Expression (Unary Right Fold): combine all flags with '|'
        return ((flags) | ...);
    }
}


// Invoke in the namespace that declares E (see the usage note above).
#define ENABLE_BITMASK_OPERATORS(E)                                                                \
    [[maybe_unused]] constexpr bool fk_bitmask_enum_marker(E) noexcept { return true; }            \
    [[maybe_unused]] constexpr E operator|(E lhs, E rhs) noexcept { return ::bitmask_detail::bit_or(lhs, rhs); }   \
    [[maybe_unused]] constexpr E operator&(E lhs, E rhs) noexcept { return ::bitmask_detail::bit_and(lhs, rhs); }  \
    [[maybe_unused]] constexpr E operator^(E lhs, E rhs) noexcept { return ::bitmask_detail::bit_xor(lhs, rhs); }  \
    [[maybe_unused]] constexpr E operator~(E value) noexcept { return ::bitmask_detail::bit_not(value); }          \
    [[maybe_unused]] constexpr E& operator|=(E& lhs, E rhs) noexcept { return lhs = ::bitmask_detail::bit_or(lhs, rhs); }  \
    [[maybe_unused]] constexpr E& operator&=(E& lhs, E rhs) noexcept { return lhs = ::bitmask_detail::bit_and(lhs, rhs); }
