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

#include <bit>
#include <cassert>
#include <cstdint>
#include <format>

union Value {

    enum class Type : uint64_t {
        Nil,
        Integer, // 48-bit
        Double,
        Pointer,
        Bool,
        Error,
        Atom,
        Undef,
        Tomb,    // for open addressing
        Unknown  // should be impossible
    };

    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isDouble() const noexcept { return bits < TAG_PTR; }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isInt()    const noexcept { return (bits & ~PAYLOAD_MASK) == TAG_INT; }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isPtr()    const noexcept { return (bits & ~PAYLOAD_MASK) == TAG_PTR; }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isBool()   const noexcept { return (bits & ~PAYLOAD_MASK) == TAG_BOOL; }
    // Basic check: Is it even the "Special"-Space (0xFFFA)?
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isSpecial() const noexcept {
        return (bits & ~PAYLOAD_MASK) == TAG_SPECIAL;
    }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isNil()    const noexcept { return bits == TAG_SPECIAL; } // (TAG_SPECIAL | 0)
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isUndefined() const noexcept {
        return bits == (TAG_SPECIAL | 1ULL);
    }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isError() const noexcept {
        return (bits & ~PAYLOAD_MASK) == TAG_SPECIAL && (bits & 0xFFULL) == static_cast<uint64_t>(SpecialType::Error);
    }
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isAtom() const noexcept {
        // Check the Special-Tag (0xFFFA) AND Sub-Tag 3 in the lowest byte
        return (bits & ~PAYLOAD_MASK) == TAG_SPECIAL && (bits & 0xFFULL) == static_cast<uint64_t>(SpecialType::Atom);
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr uint32_t asErrorCode() const noexcept {
        // Since the lower 8 Bit are reserved fo the Sub-Tag (2), 
        // we shift to the right by 8 bit to get to the code
        return static_cast<uint32_t>((bits & PAYLOAD_MASK) >> 8);
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr uint32_t asAtomId() const noexcept {
        assert(isAtom() && "Value is not an atom!");
        // move the lower 8 bit (Sub-Tag) away, to get to the ID
        return static_cast<uint32_t>((bits & PAYLOAD_MASK) >> 8);
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr double asDouble() const noexcept { return dbl; }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr int64_t asSigned48() const noexcept {
        // Extract 48 bit and do the Sign-Extension to 64 bit
        return (static_cast<int64_t>(bits & PAYLOAD_MASK) << 16) >> 16;
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr uint64_t asRaw48() const noexcept {
        return bits & PAYLOAD_MASK;
    }

    [[nodiscard]] [[msvc::forceinline]]
    void* asPtr() const noexcept {
        // remove the tag again to get the real address
        return reinterpret_cast<void*>(bits & PAYLOAD_MASK);
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool asBool() const noexcept {
        assert(isBool() && "Value is not a boolean!");
        return (bits & 1ULL) != 0;
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool operator==(const Value& other) const noexcept {

        // "Fast path" for almost all types (Int, Bool, Nil, Pointer)
        // When the bit patterns are equal, the Values are equal.
        if (bits == other.bits) [[likely]] {
            // As a special case for Double: IEEE-754 says, that NaN != NaN.
            if (bits == CANONICAL_QNAN && other.bits == CANONICAL_QNAN) {
                return false;
            }
            return true;
        }

        // The "Double path"
        // When the bits are different it could nevertheless be numerically
        // equal doubles (e.g., 0.0 == -0.0)
        if (isDouble() && other.isDouble()) {
            return dbl == other.dbl;
        }

        // Mixed types (e.g., Int 1 == Double 1.0)
        if (isInt() && other.isDouble()) {
            return static_cast<double>(asSigned48()) == other.dbl;
        }
        if (isDouble() && other.isInt()) {
            return dbl == static_cast<double>(other.asSigned48());
        }

        // Everything else (either different types and/or different pointers) is unequal
        return false;
    }

    // For arithmetic: Takes signed 64-bit, truncates to 48-bit and tags it as Int
    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromSigned48(int64_t i) noexcept {
        // The masking ' & PAYLOAD_MASK' is essential here, when i < 0 
        // or a 64-bit overflow occurred.
        return Value{ (static_cast<uint64_t>(i) & PAYLOAD_MASK) | TAG_INT };
    }

    // For logic shifts: Takes uint64_t (must be already < 2^48 !) and tag it as Int
    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromRaw48(uint64_t r) noexcept {
        return Value{ (r & PAYLOAD_MASK) | TAG_INT };
    }

    // can't be constexpr because of reinterpret_cast
    [[nodiscard]] [[msvc::forceinline]]
    static Value fromPtr(void* p) noexcept {
        // Add the TAG_PTR. Since p < 2^48 there are no collisions
        return Value { reinterpret_cast<uint64_t>(p) | TAG_PTR };
    }

    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromDouble(double d) noexcept {
        // Get the bit pattern for the check
        const uint64_t d_bits = std::bit_cast<uint64_t>(d);

        // IEEE-754 NaN Check: Exponent (Bits 52-62) are all 1.
        // That corresponds to the mask 0x7FF0000000000000
        // When the mantissa (lower 52 Bits) are all > 0 in addition, it's a NaN.
        if ((d_bits & 0x7FF0000000000000ULL) == 0x7FF0000000000000ULL &&
            (d_bits & 0x000F'FFFF'FFFF'FFFFULL) != 0)
        {
            return Value{ CANONICAL_QNAN };
        }
        return Value{ d_bits };
    }

    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromBool(bool b) noexcept {
        // Take the TAG_BOOL and set the lowest bit to 0 or 1
        return Value{ TAG_BOOL | (b ? 1ULL : 0ULL) };
    }

    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromNil() noexcept {
        return Value{ TAG_SPECIAL | static_cast<uint64_t>(SpecialType::Nil) };
    }

    // Construct a Undefined
    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromUndefined() noexcept {
        return Value{ TAG_SPECIAL | static_cast<uint64_t>(SpecialType::Undefined) };
    }

    // Create a specific Error-Code (e.g., NotFound = 404)
    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromError(uint32_t errorCode) noexcept {
        // Set the Error-Flag and then the code in the remaining bits
        return Value{ TAG_SPECIAL | static_cast<uint64_t>(SpecialType::Error) | (static_cast<uint64_t>(errorCode) << 8) };
    }

    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value fromAtom(uint32_t atomId) noexcept {
        // Sub-Tag 3 for Atoms, shift the ID by 8 Bits to the left
        return Value{ TAG_SPECIAL | static_cast<uint64_t>(SpecialType::Atom) | (static_cast<uint64_t>(atomId) << 8) };
    }

    [[nodiscard]] [[msvc::forceinline]]
    constexpr uint32_t hash() const noexcept {
        uint64_t x = bits;
        x = (x ^ (x >> 30)) * 0xbf58476d1ce4e5b9ULL;
        x = (x ^ (x >> 27)) * 0x94d049bb133111ebULL;
        x = x ^ (x >> 31);
        return static_cast<uint32_t>(x);
    }

    [[nodiscard]] [[msvc::forceinline]]
    static constexpr Value tombstone() noexcept {
        // Use the private constructor to set the bit pattern directly.
        // A pointer to 0x1 is never a valid address on a 64-Bit OS
        // (the first 4KB or even 64KB are always reserved/protected)
        return Value{ 0xFFF7'0000'0000'0001ULL };
    }

    static constexpr Value nan() noexcept {
        return Value{ CANONICAL_QNAN };
    }

    // Helper method for an open addressing HashTable (since 'bits' is private)
    [[nodiscard]] [[msvc::forceinline]]
    constexpr bool isTombstone() const noexcept {
        return bits == 0xFFF7'0000'0000'0001ULL;
    }

    // Only used for debugging
    [[nodiscard]] [[msvc::forceinline]]
    constexpr Type type() const noexcept {
        if (isSpecial()) {
            if (isNil()) {
                return Type::Nil;
            }
            if (isError()) {
                return Type::Error;
            }
            if (isAtom()) {
                return Type::Atom;
            }
            if (isUndefined()) {
                return Type::Undef;
            }
        }
        if (isTombstone()) {
            return Type::Tomb;
        }
        if (isPtr()) {
            return Type::Pointer;
        }
        if (isInt()) {
            return Type::Integer;
        }
        if (isDouble()) {
            return Type::Double;
        }
        if (isBool()) {
            return Type::Bool;
        }
        return Type::Unknown;
    }

    // default constructor with Undefined as standard value
    [[nodiscard]] [[msvc::forceinline]]
    constexpr Value() noexcept : bits(TAG_SPECIAL | static_cast<uint64_t>(SpecialType::Undefined)) {}

private:
    uint64_t bits;
    double dbl;

    [[nodiscard]] [[msvc::forceinline]]
    constexpr explicit Value(uint64_t value) noexcept : bits(value) {}

    enum class SpecialType : uint64_t {
        Nil = 0,
        Undefined = 1,
        Error = 2, // 0xFFFA...02 (Payload: ErrorCode)
        Atom = 3,  // 0xFFFA...03 (Payload: AtomID), 40-Bit ID space
        // 4 up to 255 are still free
    };

    static constexpr uint64_t PAYLOAD_MASK = 0x0000'FFFF'FFFF'FFFFULL;

    // Tags are all in the upper range (NaN-Space)
    static constexpr uint64_t TAG_PTR = 0xFFF7'0000'0000'0000ULL;
    static constexpr uint64_t TAG_INT = 0xFFF8'0000'0000'0000ULL;
    static constexpr uint64_t TAG_BOOL = 0xFFF9'0000'0000'0000ULL;
    static constexpr uint64_t TAG_SPECIAL = 0xFFFA'0000'0000'0000ULL;

    static constexpr uint64_t CANONICAL_QNAN = 0x7FF8'0000'0000'0000ULL;
};


// Ensure that the Union is exactly 8 Bytes
static_assert(sizeof(Value) == 8, "Value union must be exactly 8 bytes (64 bits)!");

// Ensure that the Union is trivially copyable (performance, can use memcpy)
#include <type_traits>
static_assert(std::is_trivially_copyable_v<Value>, "Value must be trivially copyable for performance!");

static_assert(Value::Type::Nil     == Value::fromNil().type());
static_assert(Value::Type::Undef   == Value::fromUndefined().type());
static_assert(Value::Type::Tomb    == Value::tombstone().type());
static_assert(Value::Type::Error   == Value::fromError(404).type());
static_assert(Value::Type::Atom    == Value::fromAtom(25).type());
static_assert(Value::Type::Bool    == Value::fromBool(true).type());
static_assert(Value::Type::Integer == Value::fromSigned48(-101).type());
static_assert(Value::Type::Integer == Value::fromRaw48(222'222).type());
static_assert(Value::Type::Double  == Value::fromDouble(0.0).type());
static_assert(Value::Type::Double  == Value::fromDouble(0.23455).type());
static_assert(Value::Type::Double  == Value::nan().type());
// Nan != NaN
static_assert(Value::nan() != Value::nan());



template <>
struct std::formatter<Value> {
    constexpr auto parse(std::format_parse_context& ctx) {
        return ctx.begin();
    }

    auto format(const Value& v, std::format_context& ctx) const {
        auto type = v.type();
        switch (type) {
            // Double: everything below TAG_PTR 0xFFF7...
            case Value::Type::Double  : return std::format_to(ctx.out(), "Dbl({:g})", v.asDouble());
            // Pointer (0xFFF7...)
            case Value::Type::Pointer : return std::format_to(ctx.out(), "Ptr({})", v.asPtr());
            // Integer (0xFFF8...)
            case Value::Type::Integer : return std::format_to(ctx.out(), "Int({})", v.asSigned48());
            // Boolean (0xFFF9...)
            case Value::Type::Bool    : return std::format_to(ctx.out(), "bool({})", v.asBool() ? "true" : "false");
            // Special Types (0xFFFA...)
            case Value::Type::Nil     : return std::format_to(ctx.out(), "nil");
            case Value::Type::Undef   : return std::format_to(ctx.out(), "undefined");
            // Error 0xFFFA...02
            case Value::Type::Error   : return std::format_to(ctx.out(), "Error({})", v.asErrorCode());
            // Atom 0xFFFA...03
            case Value::Type::Atom    : return std::format_to(ctx.out(), "Atom(#{})", v.asAtomId());
            // Tombstone
            case Value::Type::Tomb    : return std::format_to(ctx.out(), "tombstone");
            // Something went wrong
            default                   : return std::format_to(ctx.out(), "unknown({:g})", v.asDouble());
        }
    }
};
