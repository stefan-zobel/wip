#pragma once

#include <array>
#include <vector>
#include <span>
#include "strong_types.h"

// Just some experiments with private inheritance

namespace fk {

    using octet = fk::octet;

    class HeapByteBuffer : private std::vector<octet> {
        using Base = std::vector<octet>;
    public:
        using Base::Base; // all constructors of std::vector<octet> are available

        // read access
        using Base::operator[];
        using Base::at;
        using Base::data;
        using Base::size;
        using Base::empty;

        // iterators
        using Base::begin;
        using Base::end;
        using Base::cbegin;
        using Base::cend;
        using Base::rbegin;
        using Base::rend;
        using Base::crbegin;
        using Base::crend;

        // write access
        constexpr void push_back(octet val) noexcept {
            Base::push_back(val);
        }
   
        // modifying the buffer (clear, reserve, resize)
        using Base::clear;
        using Base::reserve;
        using Base::resize;

        // Implicit conversion to std::span<octet> and std::span<const octet>
        constexpr operator std::span<octet>() { return std::span<octet>(Base::data(), Base::size()); }
        constexpr operator std::span<const octet>() const { return std::span<const octet>(Base::data(), Base::size()); }
    };


    // use 32 Byte alignment for AVX2
    template <std::size_t N, std::size_t Alignment = 32>
    class alignas(Alignment) StackByteBuffer : private std::array<octet, N> {
        using Base = std::array<octet, N>;
    public:

        // zero out everything
        constexpr StackByteBuffer() noexcept : Base{} {}

        // read access
        // do return by value
        constexpr octet operator[](std::size_t index) const noexcept {
            return static_cast<const Base&>(*this)[index];
        }
        using Base::operator[];
        using Base::at;
        using Base::data;

        // no bounds check when the index is known at compile time
        template <std::size_t I>
        constexpr octet& get() noexcept {
            static_assert(I < N, "Index out of bounds");
            return std::get<I>(static_cast<Base&>(*this));
        }

        // do return by value
        template <std::size_t I>
        constexpr octet get() const noexcept {
            static_assert(I < N, "Index out of bounds");
            return std::get<I>(static_cast<const Base&>(*this));
        }

        // iterators
        using Base::begin;
        using Base::end;
        using Base::cbegin;
        using Base::cend;
        using Base::rbegin;
        using Base::rend;
        using Base::crbegin;
        using Base::crend;

        // std::array: fill
        constexpr void fill(octet val) {
            Base::fill(val);
        }

        // Implicit conversion to std::span<octet> and std::span<const octet>
        constexpr operator std::span<octet>() { return std::span<octet>(Base::data(), N); }
        constexpr operator std::span<const octet>() const { return std::span<const octet>(Base::data(), N); }

        // no memory accesses guaranteed (static constexpr)
        static constexpr std::size_t size() noexcept { return N; }
        static constexpr std::size_t capacity() noexcept { return N; }
    };
}
