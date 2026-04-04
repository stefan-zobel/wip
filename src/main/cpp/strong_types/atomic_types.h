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

#include <atomic>
#include <concepts>
#include <type_traits>
#include <utility>

#include "strong_types.h"

namespace fk {

    // ====================================================================
    // Supported Strong integer types for atomic wrappers
    // ====================================================================

    template <typename T>
    concept AtomicStrongValue =
        std::same_as<T, i32> ||
        std::same_as<T, i64> ||
        std::same_as<T, u32> ||
        std::same_as<T, u64> ||
        std::same_as<T, isize> ||
        std::same_as<T, usize>;

    template <AtomicStrongValue T>
    struct atomic_value_traits;

    template <>
    struct atomic_value_traits<i32> {
        using underlying_type = int32_t;
    };

    template <>
    struct atomic_value_traits<i64> {
        using underlying_type = int64_t;
    };

    template <>
    struct atomic_value_traits<u32> {
        using underlying_type = uint32_t;
    };

    template <>
    struct atomic_value_traits<u64> {
        using underlying_type = uint64_t;
    };

    template <>
    struct atomic_value_traits<isize> {
        using underlying_type = ptrdiff_t;
    };

    template <>
    struct atomic_value_traits<usize> {
        using underlying_type = size_t;
    };

    template <AtomicStrongValue T>
    using atomic_underlying_t = typename atomic_value_traits<T>::underlying_type;

    template <AtomicStrongValue T>
    constexpr atomic_underlying_t<T> to_atomic_underlying(const T& value) noexcept {
        return static_cast<atomic_underlying_t<T>>(value.get());
    }

    // ====================================================================
    // AtomicStrong
    // A dedicated atomic wrapper family for selected Strong integer types.
    // Public API uses StrongTypes exclusively.
    // ====================================================================

    template <AtomicStrongValue TValue>
    struct AtomicStrong {
        using value_type = TValue;
        using underlying_type = atomic_underlying_t<value_type>;
        using atomic_type = std::atomic<underlying_type>;

        static_assert(std::is_integral_v<underlying_type>,
                      "AtomicStrong requires an integral underlying type.");
        static_assert(std::is_trivially_copyable_v<underlying_type>,
                      "AtomicStrong requires a trivially copyable underlying type.");

        static constexpr bool is_always_lock_free = atomic_type::is_always_lock_free;

        AtomicStrong() noexcept = default;

        explicit AtomicStrong(value_type desired) noexcept
            : value_(to_atomic_underlying(desired)) {
        }

        AtomicStrong(const AtomicStrong&) = delete;
        AtomicStrong(AtomicStrong&&) = delete;
        AtomicStrong& operator=(const AtomicStrong&) = delete;
        AtomicStrong& operator=(AtomicStrong&&) = delete;

        [[nodiscard]] value_type load(std::memory_order order = std::memory_order_seq_cst) const noexcept {
            return value_type(value_.load(order));
        }

        void store(value_type desired, std::memory_order order = std::memory_order_seq_cst) noexcept {
            value_.store(to_atomic_underlying(desired), order);
        }

        [[nodiscard]] value_type exchange(value_type desired,
                                          std::memory_order order = std::memory_order_seq_cst) noexcept {
            return value_type(value_.exchange(to_atomic_underlying(desired), order));
        }

        [[nodiscard]] bool compare_exchange_weak(value_type& expected,
                                                 value_type desired,
                                                 std::memory_order success,
                                                 std::memory_order failure) noexcept {
            underlying_type raw_expected = to_atomic_underlying(expected);
            const bool exchanged = value_.compare_exchange_weak(raw_expected,
                                                                to_atomic_underlying(desired),
                                                                success,
                                                                failure);
            expected = value_type(raw_expected);
            return exchanged;
        }

        [[nodiscard]] bool compare_exchange_weak(value_type& expected,
                                                 value_type desired,
                                                 std::memory_order order = std::memory_order_seq_cst) noexcept {
            underlying_type raw_expected = to_atomic_underlying(expected);
            const bool exchanged = value_.compare_exchange_weak(raw_expected,
                                                                to_atomic_underlying(desired),
                                                                order);
            expected = value_type(raw_expected);
            return exchanged;
        }

        [[nodiscard]] bool compare_exchange_strong(value_type& expected,
                                                   value_type desired,
                                                   std::memory_order success,
                                                   std::memory_order failure) noexcept {
            underlying_type raw_expected = to_atomic_underlying(expected);
            const bool exchanged = value_.compare_exchange_strong(raw_expected,
                                                                  to_atomic_underlying(desired),
                                                                  success,
                                                                  failure);
            expected = value_type(raw_expected);
            return exchanged;
        }

        [[nodiscard]] bool compare_exchange_strong(value_type& expected,
                                                   value_type desired,
                                                   std::memory_order order = std::memory_order_seq_cst) noexcept {
            underlying_type raw_expected = to_atomic_underlying(expected);
            const bool exchanged = value_.compare_exchange_strong(raw_expected,
                                                                  to_atomic_underlying(desired),
                                                                  order);
            expected = value_type(raw_expected);
            return exchanged;
        }

        [[nodiscard]] value_type fetch_add(value_type arg,
                                           std::memory_order order = std::memory_order_seq_cst) noexcept {
            return value_type(value_.fetch_add(to_atomic_underlying(arg), order));
        }

        [[nodiscard]] value_type fetch_sub(value_type arg,
                                           std::memory_order order = std::memory_order_seq_cst) noexcept {
            return value_type(value_.fetch_sub(to_atomic_underlying(arg), order));
        }

        void wait(value_type old, std::memory_order order = std::memory_order_seq_cst) const noexcept {
            value_.wait(to_atomic_underlying(old), order);
        }

        void notify_one() noexcept {
            value_.notify_one();
        }

        void notify_all() noexcept {
            value_.notify_all();
        }

        [[nodiscard]] bool is_lock_free() const noexcept {
            return value_.is_lock_free();
        }

    private:
        atomic_type value_{};
    };

    // Concrete atomic Strong types
    using atomic_i32 = AtomicStrong<i32>;
    using atomic_i64 = AtomicStrong<i64>;
    using atomic_u32 = AtomicStrong<u32>;
    using atomic_u64 = AtomicStrong<u64>;
    using atomic_isize = AtomicStrong<isize>;
    using atomic_usize = AtomicStrong<usize>;

    // ====================================================================
    // Compile-time sanity checks
    // ====================================================================

    static_assert(AtomicStrongValue<i32>);
    static_assert(AtomicStrongValue<i64>);
    static_assert(AtomicStrongValue<u32>);
    static_assert(AtomicStrongValue<u64>);
    static_assert(AtomicStrongValue<isize>);
    static_assert(AtomicStrongValue<usize>);

    static_assert(!AtomicStrongValue<i8>);
    static_assert(!AtomicStrongValue<u8>);
    static_assert(!AtomicStrongValue<i16>);
    static_assert(!AtomicStrongValue<u16>);
    static_assert(!AtomicStrongValue<f32>);
    static_assert(!AtomicStrongValue<f64>);
    static_assert(!AtomicStrongValue<octet>);

    static_assert(std::same_as<atomic_underlying_t<i32>, int32_t>);
    static_assert(std::same_as<atomic_underlying_t<i64>, int64_t>);
    static_assert(std::same_as<atomic_underlying_t<u32>, uint32_t>);
    static_assert(std::same_as<atomic_underlying_t<u64>, uint64_t>);
    static_assert(std::same_as<atomic_underlying_t<isize>, ptrdiff_t>);
    static_assert(std::same_as<atomic_underlying_t<usize>, size_t>);

    static_assert(std::same_as<typename atomic_i32::value_type, i32>);
    static_assert(std::same_as<typename atomic_u64::value_type, u64>);
    static_assert(std::same_as<typename atomic_isize::value_type, isize>);
    static_assert(std::same_as<typename atomic_usize::underlying_type, size_t>);

    static_assert(std::default_initializable<atomic_i32>);
    static_assert(!std::copy_constructible<atomic_i32>);
    static_assert(!std::movable<atomic_i32>);

    static_assert(std::same_as<decltype(std::declval<const atomic_i32&>().load()), i32>);
    static_assert(std::same_as<decltype(std::declval<atomic_u64&>().exchange(std::declval<u64>())), u64>);
    static_assert(std::same_as<decltype(std::declval<atomic_usize&>().fetch_add(std::declval<usize>())), usize>);
    static_assert(std::same_as<decltype(std::declval<atomic_i64&>().fetch_sub(std::declval<i64>())), i64>);
    static_assert(std::same_as<decltype(std::declval<atomic_i32&>().is_lock_free()), bool>);

} // namespace fk
