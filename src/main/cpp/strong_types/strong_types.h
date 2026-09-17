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


    namespace strong_types_detail {
        // Shift count of a shift operator: std::byte only shifts by an integer, so an octet shifts by
        // the integer value of its right operand.
        template <typename T>
        constexpr auto shift_count(const T& value) noexcept {
            if constexpr (std::is_same_v<T, std::byte>) {
                return std::to_integer<unsigned int>(value);
            }
            else {
                return static_cast<T>(value);
            }
        }
    }

    // ========================================================================
    // Policies
    // A policy decides what a strong type can do:
    //   implicit_conversion  converts implicitly to T (otherwise only via get() or static_cast)
    //   arithmetic           + - * / %, unary -, += -= *= /= %=  (arithmetic T only)
    //   bitwise              | & ^ ~ << >>, |= &= ^= <<= >>=     (integral T or std::byte)
    //   increment            ++ and --                           (integral T)
    // Comparison (== <=> ...) and std::hash are always available.
    //
    // Presets: Number (implicit, all operators; used by fk::i32 etc.), StrictNumber (explicit,
    // all operators; the default), Id (explicit, comparison and hashing only).
    // A user-defined policy is a struct with the same four static constexpr bool members.
    //
    // Operators only combine two operands of the same strong type and return that type
    // (i32 + i32 is i32). With implicit_conversion, mixed expressions such as i32 + int or
    // i32 + i64 still compile through the built-in operators on T; with an explicit policy they
    // do not compile.
    //
    //     struct entity_id : fk::StrongType<uint32_t, struct EntityIdTag, fk::strong_policy::Id> {
    //         using StrongType::StrongType;
    //     };
    // ========================================================================
    template <typename P>
    concept StrongTypePolicy = requires {
        { std::bool_constant<P::implicit_conversion>{} };
        { std::bool_constant<P::arithmetic>{} };
        { std::bool_constant<P::bitwise>{} };
        { std::bool_constant<P::increment>{} };
    };

    namespace strong_policy {
        struct Number {
            static constexpr bool implicit_conversion = true;
            static constexpr bool arithmetic = true;
            static constexpr bool bitwise = true;
            static constexpr bool increment = true;
        };

        struct StrictNumber {
            static constexpr bool implicit_conversion = false;
            static constexpr bool arithmetic = true;
            static constexpr bool bitwise = true;
            static constexpr bool increment = true;
        };

        struct Id {
            static constexpr bool implicit_conversion = false;
            static constexpr bool arithmetic = false;
            static constexpr bool bitwise = false;
            static constexpr bool increment = false;
        };
    }

    namespace strong_types_detail {
        template <typename T>
        concept arithmetic_value = std::is_arithmetic_v<T>;

        template <typename T>
        concept bitwise_value = (std::is_integral_v<T> || std::is_same_v<T, std::byte>) && !std::is_same_v<T, bool>;

        template <typename T>
        concept incrementable_value = std::is_integral_v<T> && !std::is_same_v<T, bool>;
    }

    template <typename T, typename Tag, StrongTypePolicy Policy = strong_policy::StrictNumber>
    struct StrongType {

        using policy_type = Policy;

        explicit constexpr StrongType(T v) noexcept(std::is_nothrow_move_constructible_v<T>)
            : value(std::move(v)) {
        }

        constexpr StrongType() noexcept requires std::is_trivial_v<T>
            : value(static_cast<T>(0)) {
        }

        // Return T by value if sizeof(T) <= 8 Byte, otherwise return const T&
        using ReturnType = std::conditional_t<(sizeof(T) <= sizeof(void*)), T, const T&>;

        // Cast to the underlying type (implicit only if the policy allows it)
        constexpr explicit(!Policy::implicit_conversion) operator ReturnType() const & noexcept {
            return value;
        }

        constexpr explicit(!Policy::implicit_conversion) operator T&&() && noexcept {
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

    private:
        // Value access for the operator templates below, whose operands are the derived type S
        static constexpr const T& raw(const StrongType& s) noexcept { return static_cast<const T&>(s.value); }
        static constexpr T& raw(StrongType& s) noexcept { return static_cast<T&>(s.value); }

    public:
        // ====================================================================
        // Operators (hidden friend templates)
        // S is the actual strong type of both operands (e.g. fk::i32, not its StrongType base);
        // it is deduced from both operands, so different strong types never combine.
        // ====================================================================

        // Arithmetic operators
        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S operator+(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) + raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S operator-(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) - raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S operator*(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) * raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S operator/(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) / raw(rhs)));
        }

        // Modulo is strictly limited to integral types (except boolean)
        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::incrementable_value<T>)
        friend constexpr S operator%(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) % raw(rhs)));
        }

        // Unary minus (Ensure it is only defined for signed types)
        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T> && std::is_signed_v<T>)
        friend constexpr S operator-(const S& v) noexcept {
            return S(static_cast<T>(-raw(v)));
        }

        // Compound Assignments (+=, -=, etc.)
        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S& operator+=(S& lhs, const S& rhs) noexcept {
            raw(lhs) += raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S& operator-=(S& lhs, const S& rhs) noexcept {
            raw(lhs) -= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S& operator*=(S& lhs, const S& rhs) noexcept {
            raw(lhs) *= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::arithmetic_value<T>)
        friend constexpr S& operator/=(S& lhs, const S& rhs) noexcept {
            raw(lhs) /= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::arithmetic && strong_types_detail::incrementable_value<T>)
        friend constexpr S& operator%=(S& lhs, const S& rhs) noexcept {
            raw(lhs) %= raw(rhs);
            return lhs;
        }

        // ====================================================================
        // Bitwise Operators (Crucial for bitmasks, flags, or std::byte types)
        // Restricted to integers and std::byte, excluding 'bool'.
        // ====================================================================

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator|(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) | raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator&(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) & raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator^(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) ^ raw(rhs)));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator~(const S& v) noexcept {
            return S(static_cast<T>(~raw(v)));
        }

        // Bitwise Shift operators (left/right)
        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator<<(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) << strong_types_detail::shift_count<T>(raw(rhs))));
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S operator>>(const S& lhs, const S& rhs) noexcept {
            return S(static_cast<T>(raw(lhs) >> strong_types_detail::shift_count<T>(raw(rhs))));
        }

        // Bitwise Assignment Operators
        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S& operator|=(S& lhs, const S& rhs) noexcept {
            raw(lhs) |= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S& operator&=(S& lhs, const S& rhs) noexcept {
            raw(lhs) &= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S& operator^=(S& lhs, const S& rhs) noexcept {
            raw(lhs) ^= raw(rhs);
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S& operator<<=(S& lhs, const S& rhs) noexcept {
            raw(lhs) <<= strong_types_detail::shift_count<T>(raw(rhs));
            return lhs;
        }

        template <std::derived_from<StrongType> S>
            requires (Policy::bitwise && strong_types_detail::bitwise_value<T>)
        friend constexpr S& operator>>=(S& lhs, const S& rhs) noexcept {
            raw(lhs) >>= strong_types_detail::shift_count<T>(raw(rhs));
            return lhs;
        }

        // ====================================================================
        // Increment / Decrement Operators
        // Restricted to integers. Strictly excluding 'bool' because ++/-- on
        // boolean types is illogical and actively forbidden in modern C++.
        // ====================================================================

        // Pre-increment (++x)
        template <std::derived_from<StrongType> S>
            requires (Policy::increment && strong_types_detail::incrementable_value<T>)
        friend constexpr S& operator++(S& v) noexcept {
            ++raw(v);
            return v;
        }

        // Post-increment (x++)
        template <std::derived_from<StrongType> S>
            requires (Policy::increment && strong_types_detail::incrementable_value<T>)
        friend constexpr S operator++(S& v, int) noexcept {
            S copy(v);
            ++raw(v);
            return copy;
        }

        // Pre-decrement (--x)
        template <std::derived_from<StrongType> S>
            requires (Policy::increment && strong_types_detail::incrementable_value<T>)
        friend constexpr S& operator--(S& v) noexcept {
            --raw(v);
            return v;
        }

        // Post-decrement (x--)
        template <std::derived_from<StrongType> S>
            requires (Policy::increment && strong_types_detail::incrementable_value<T>)
        friend constexpr S operator--(S& v, int) noexcept {
            S copy(v);
            --raw(v);
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
    struct i8   : public StrongType<int8_t,   struct INT8Tag, strong_policy::Number  > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i16  : public StrongType<int16_t , struct INT16Tag, strong_policy::Number > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i32  : public StrongType<int32_t,  struct INT32Tag, strong_policy::Number > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct i64  : public StrongType<int64_t,  struct INT64Tag, strong_policy::Number > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u8   : public StrongType<uint8_t,  struct UINT8Tag, strong_policy::Number > { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u16  : public StrongType<uint16_t, struct UINT16Tag, strong_policy::Number> { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u32  : public StrongType<uint32_t, struct UINT32Tag, strong_policy::Number> { using StrongType::StrongType; /*inheriting constructor*/ };
    struct u64  : public StrongType<uint64_t, struct UINT64Tag, strong_policy::Number> { using StrongType::StrongType; /*inheriting constructor*/ };

    // floating-point types:
    using float32 = float;  // C++23: std::float32_t
    using float64 = double; // C++23: std::float64_t

    struct f32 : public StrongType<float32, struct F32Tag, strong_policy::Number> { using StrongType::StrongType; };
    struct f64 : public StrongType<float64, struct F64Tag, strong_policy::Number> { using StrongType::StrongType; };

    // byte / size_t / ptrdiff_t:
    struct octet : public StrongType<std::byte, struct OCTETTag, strong_policy::Number> { using StrongType::StrongType; };
    struct usize : public StrongType<size_t   , struct USIZETag, strong_policy::Number> { using StrongType::StrongType; };
    struct isize : public StrongType<ptrdiff_t, struct ISIZETag, strong_policy::Number> { using StrongType::StrongType; };

} // namespace fk


// ====================================================================
// Standard Library Hash Injection
// Enables 'StrongType' and all types derived from it (fk::i32, fk::u64,
// user-defined strong types, ...) to be seamlessly used as a key in
// std::unordered_map and std::unordered_set.
// ====================================================================
namespace fk::detail {
    // Finds the StrongType<T, Tag, Policy> base of a derived strong type (deduction via base conversion).
    template <typename T, typename Tag, typename Policy>
    StrongType<T, Tag, Policy> strong_base_of(const StrongType<T, Tag, Policy>&);

    template <typename S>
    concept derived_strong_type = requires(const S& s) { strong_base_of(s); }
        && !std::same_as<S, decltype(strong_base_of(std::declval<const S&>()))>;
}

namespace std {
    template <typename T, typename Tag, typename Policy>
    struct hash<fk::StrongType<T, Tag, Policy>> {
        std::size_t operator()(const fk::StrongType<T, Tag, Policy>& st) const noexcept {
            // Forward the hashing logic to the underlying type T
            return std::hash<T>{}(st.get());
        }
    };
}

// Derived strong types (fk::i32, fk::u64, user types deriving from StrongType, ...) hash like
// their StrongType<T, Tag> base, i.e. like the underlying T. A specialization for StrongType<T, Tag>
// alone does not apply to derived types, because template specialization ignores inheritance.
template <typename S>
    requires fk::detail::derived_strong_type<S>
struct std::hash<S> : std::hash<decltype(fk::detail::strong_base_of(std::declval<const S&>()))> {};
