#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <immintrin.h>
#include <type_traits>

#include "StackAllocator.h"

template <typename T>
struct MatrixView {
    T* data = nullptr;
    size_t rows = 0;
    size_t cols = 0;
    size_t stride = 0; // elements per row

    [[nodiscard]] T& operator()(size_t row, size_t col) const noexcept {
        return data[row * stride + col];
    }

    [[nodiscard]] MatrixView<T> subview(size_t row_offset,
                                        size_t col_offset,
                                        size_t sub_rows,
                                        size_t sub_cols) const noexcept {
        return MatrixView<T>{
            data + row_offset * stride + col_offset,
            sub_rows,
            sub_cols,
            stride
        };
    }
};

template <typename T>
struct Avx2GemmTraits;

template <>
struct Avx2GemmTraits<float> {
    using Scalar = float;
    using Vec = __m256;

    static constexpr size_t vector_lanes = 8;
    static constexpr size_t mr = 8;
    static constexpr size_t nr = 6;

    static constexpr size_t kc = 256;
    static constexpr size_t mc = 128;
    static constexpr size_t nc = 256;

    static constexpr size_t alignment = 64;
};

template <>
struct Avx2GemmTraits<double> {
    using Scalar = double;
    using Vec = __m256d;

    static constexpr size_t vector_lanes = 4;
    static constexpr size_t mr = 4;
    static constexpr size_t nr = 6;

    static constexpr size_t kc = 192;
    static constexpr size_t mc = 96;
    static constexpr size_t nc = 192;

    static constexpr size_t alignment = 64;
};

template <typename T>
[[nodiscard]] constexpr size_t round_up_to_multiple(size_t value, size_t multiple) noexcept {
    return (value + multiple - 1) / multiple * multiple;
}

template <typename T>
[[nodiscard]] constexpr size_t packed_a_panel_elements(size_t mc, size_t kc) noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    const size_t padded_mc = round_up_to_multiple<T>(mc, Traits::mr);
    return padded_mc * kc;
}

template <typename T>
[[nodiscard]] constexpr size_t packed_b_panel_elements(size_t kc, size_t nc) noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    const size_t padded_nc = round_up_to_multiple<T>(nc, Traits::nr);
    return kc * padded_nc;
}

template <typename T>
[[nodiscard]] T* allocate_scratch_elements(StackAllocator& scratch,
                                           size_t count,
                                           size_t alignment = Avx2GemmTraits<std::remove_cv_t<T>>::alignment) {
    void* memory = scratch.allocate_raw_aligned(count * sizeof(T), alignment);
    return static_cast<T*>(memory);
}

template <typename T>
[[nodiscard]] T* allocate_zeroed_scratch_elements(StackAllocator& scratch,
                                                  size_t count,
                                                  size_t alignment = Avx2GemmTraits<std::remove_cv_t<T>>::alignment) {
    T* memory = allocate_scratch_elements<T>(scratch, count, alignment);
    if (!memory) {
        return nullptr;
    }

    std::memset(memory, 0, count * sizeof(T));
    return memory;
}

inline void prefetch_l1(const void* ptr) noexcept {
    _mm_prefetch(static_cast<const char*>(ptr), _MM_HINT_T0);
}

inline void prefetch_l2(const void* ptr) noexcept {
    _mm_prefetch(static_cast<const char*>(ptr), _MM_HINT_T1);
}

inline void prefetch_stream(const void* ptr) noexcept {
    _mm_prefetch(static_cast<const char*>(ptr), _MM_HINT_NTA);
}
