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

        // Return T by value if sizeof(T) <= 8 Byte, otherwise return const T&
        using ReturnType = std::conditional_t<(sizeof(T) <= sizeof(void*)), T, const T&>;

        constexpr operator ReturnType() const & noexcept {
            return value;
        }

        constexpr operator T() && noexcept {
            return std::move(value);
        }

        /**
         * @brief Defaulted C++20 three-way comparison (spaceship operator).
         *
         * @note Risk for pointers: If T is a raw pointer, this will perform
         * identity comparison (address vs. address) rather than content comparison.
         * For floating-point types, it returns a std::partial_ordering (handling NaN).
         * For types without a defined <=> operator, this will fail at compile time.
         */
        auto operator<=>(const StrongType&) const = default;

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


    //// Just as an example of a more complex use case:
    //struct StringIdentifier : public StrongType<std::string, struct STRINGTag> { using StrongType::StrongType; };
    //// Usage:
    //StringIdentifier hello{ "World" };
    //std::cout << "Hello: " << (std::string)hello << "\n";

} // namespace fk
