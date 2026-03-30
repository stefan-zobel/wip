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

#include <cstdint>
#include <utility>
#include <type_traits>
#include <functional> // For std::hash
#include <cstddef>    // For std::byte

namespace fk {

    template <typename T>
    struct must_init {
        constexpr must_init(T t) noexcept(std::is_nothrow_move_constructible_v<T>) : value(std::move(t)) {}
        must_init() = delete;
        constexpr operator T&() & noexcept { return value; }
        constexpr operator const T&() const & noexcept { return value; }
        constexpr operator T&&() && noexcept { return std::move(value); }
    private:
        T value;
    };


    template <typename T, typename Tag>
    struct StrongType {

        explicit constexpr StrongType(T v) noexcept(std::is_nothrow_move_constructible_v<T>)
            : value(std::move(v)) {
        }

        constexpr StrongType() noexcept requires std::is_trivial_v<T>
            : value(static_cast<T>(0)) {
        }

        // Return T by value if sizeof(T) <= 8 Byte, otherwise return const T&
        using ReturnType = std::conditional_t<(sizeof(T) <= sizeof(void*)), T, const T&>;

        // Implicit cast to underlying type
        constexpr operator ReturnType() const & noexcept {
            return value;
        }

        constexpr operator T&&() && noexcept {
            return std::move(value);
        }

        // Explicit getter (Useful when template deduction fails to naturally invoke the implicit cast)
        constexpr ReturnType get() const noexcept {
            return static_cast<const T&>(value);
        }

        /**
         * @brief Defaulted C++20 three-way comparison (spaceship operator).
         */
        auto operator<=>(const StrongType&) const = default;

        // ====================================================================
        // Arithmetic operators (hidden friends, automatically generated)
        // Restricted via C++20 Concepts to arithmetic base types
        // ====================================================================

        // Operator +
        friend constexpr StrongType operator+(const StrongType& lhs, const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            return StrongType(static_cast<T>(lhs.value) + static_cast<T>(rhs.value));
        }

        // Operator -
        friend constexpr StrongType operator-(const StrongType& lhs, const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            return StrongType(static_cast<T>(lhs.value) - static_cast<T>(rhs.value));
        }

        // Operator *
        friend constexpr StrongType operator*(const StrongType& lhs, const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            return StrongType(static_cast<T>(lhs.value) * static_cast<T>(rhs.value));
        }

        // Operator /
        friend constexpr StrongType operator/(const StrongType& lhs, const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            return StrongType(static_cast<T>(lhs.value) / static_cast<T>(rhs.value));
        }

        // Modulo is strictly limited to integral types (except boolean)
        friend constexpr StrongType operator%(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            return StrongType(static_cast<T>(lhs.value) % static_cast<T>(rhs.value));
        }

        // Compound Assignments (+=, -=, etc.)
        constexpr StrongType& operator+=(const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            static_cast<T&>(value) += static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator-=(const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            static_cast<T&>(value) -= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator*=(const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            static_cast<T&>(value) *= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator/=(const StrongType& rhs) noexcept
            requires std::is_arithmetic_v<T>
        {
            static_cast<T&>(value) /= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator%=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            static_cast<T&>(value) %= static_cast<const T&>(rhs.value);
            return *this;
        }

        // Unary minus (Ensure it is only defined for signed types)
        friend constexpr StrongType operator-(const StrongType& v) noexcept
            requires (std::is_arithmetic_v<T> && std::is_signed_v<T>)
        {
            return StrongType(-static_cast<T>(v.value));
        }

        // ====================================================================
        // Bitwise Operators (Crucial for bitmasks, flags, or std::byte types)
        // Restricted to integers and non-boolean types.
        // ====================================================================

        friend constexpr StrongType operator|(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(static_cast<T>(lhs.value) | static_cast<T>(rhs.value));
        }

        friend constexpr StrongType operator&(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(static_cast<T>(lhs.value) & static_cast<T>(rhs.value));
        }

        friend constexpr StrongType operator^(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(static_cast<T>(lhs.value) ^ static_cast<T>(rhs.value));
        }

        friend constexpr StrongType operator~(const StrongType& v) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(~static_cast<T>(v.value));
        }

        // Bitwise Shift operators (left/right)
        friend constexpr StrongType operator<<(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(static_cast<T>(lhs.value) << static_cast<T>(rhs.value));
        }

        friend constexpr StrongType operator>>(const StrongType& lhs, const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            return StrongType(static_cast<T>(lhs.value) >> static_cast<T>(rhs.value));
        }

        // Bitwise Assignment Operators
        constexpr StrongType& operator|=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            static_cast<T&>(value) |= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator&=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            static_cast<T&>(value) &= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator^=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            static_cast<T&>(value) ^= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator<<=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            static_cast<T&>(value) <<= static_cast<const T&>(rhs.value);
            return *this;
        }

        constexpr StrongType& operator>>=(const StrongType& rhs) noexcept
            requires (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>
        {
            static_cast<T&>(value) >>= static_cast<const T&>(rhs.value);
            return *this;
        }


        // ====================================================================
        // Increment / Decrement Operators
        // Restricted to integers. Strictly excluding 'bool' because ++/-- on 
        // boolean types is illogical and actively forbidden in modern C++.
        // ====================================================================

        // Pre-increment (++x)
        constexpr StrongType& operator++() noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            ++static_cast<T&>(value);
            return *this;
        }

        // Post-increment (x++)
        constexpr StrongType operator++(int) noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            StrongType copy(*this);
            ++static_cast<T&>(value);
            return copy;
        }

        // Pre-decrement (--x)
        constexpr StrongType& operator--() noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            --static_cast<T&>(value);
            return *this;
        }

        // Post-decrement (x--)
        constexpr StrongType operator--(int) noexcept
            requires (std::is_integral_v<T> && !std::is_same_v<T, bool>)
        {
            StrongType copy(*this);
            --static_cast<T&>(value);
            return copy;
        }

        // ====================================================================
        // Dereference and Member Access Operators (for Pointers / Iterators)
        // These are useful when creating StrongTypes around raw pointers
        // (like Handle or NodeID) or smart pointers.
        // ====================================================================

        // Dereference operator (*)
        // Requires that T can be dereferenced and is not a void pointer
        constexpr decltype(auto) operator*() const noexcept
            requires requires(T t) { *t; } && (!std::is_same_v<T, void*>)
        {
            return *static_cast<const T&>(value);
        }

        // Arrow operator (->)
        // Requires that T supports member access either natively (pointers) 
        // or via an overloaded operator-> (smart pointers).
        constexpr decltype(auto) operator->() const noexcept
            requires std::is_pointer_v<T> || requires(T t) { t.operator->(); }
        {
            return static_cast<const T&>(value);
        }

    protected:
        must_init<T> value;
    };

    // cstdint types:
    struct i8   : public StrongType<int8_t,   struct INT8Tag  > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i16  : public StrongType<int16_t , struct INT16Tag > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i32  : public StrongType<int32_t,  struct INT32Tag > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i64  : public StrongType<int64_t,  struct INT64Tag > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u8   : public StrongType<uint8_t,  struct UINT8Tag > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u16  : public StrongType<uint16_t, struct UINT16Tag> { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u32  : public StrongType<uint32_t, struct UINT32Tag> { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u64  : public StrongType<uint64_t, struct UINT64Tag> { using StrongType::StrongType; /*inheriting constructor*/ };

    // floating-point types:
    using float32 = float;  // C++23: std::float32_t
    using float64 = double; // C++23: std::float64_t

    struct f32 : public StrongType<float32, struct F32Tag> { using StrongType::StrongType; };
    struct f64 : public StrongType<float64, struct F64Tag> { using StrongType::StrongType; };

    // byte / size_t / ptrdiff_t:
    struct octet : public StrongType<std::byte, struct OCTETTag> { using StrongType::StrongType; };
    struct usize : public StrongType<size_t   , struct USIZETag> { using StrongType::StrongType; };
    struct isize : public StrongType<ptrdiff_t, struct ISIZETag> { using StrongType::StrongType; };

} // namespace fk


// ====================================================================
// Standard Library Hash Injection
// Enables 'StrongType' to be seamlessly used as a key in 
// std::unordered_map and std::unordered_set.
// ====================================================================
namespace std {
    template <typename T, typename Tag>
    struct hash<fk::StrongType<T, Tag>> {
        std::size_t operator()(const fk::StrongType<T, Tag>& st) const noexcept {
            // Forward the hashing logic to the underlying type T
            return std::hash<T>{}(st.get());
        }
    };
}
