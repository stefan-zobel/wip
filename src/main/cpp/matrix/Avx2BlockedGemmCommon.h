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

// True if the elements of two non-empty views share memory. Compared as integers, because the
// order of pointers into different objects is unspecified.
template <typename T, typename U>
[[nodiscard]] bool views_overlap(const MatrixView<T>& x, const MatrixView<U>& y) noexcept {
    const std::uintptr_t x_first = reinterpret_cast<std::uintptr_t>(x.data);
    const std::uintptr_t x_end = x_first + ((x.rows - 1) * x.stride + x.cols) * sizeof(T);
    const std::uintptr_t y_first = reinterpret_cast<std::uintptr_t>(y.data);
    const std::uintptr_t y_end = y_first + ((y.rows - 1) * y.stride + y.cols) * sizeof(U);
    return x_first < y_end && y_first < x_end;
}

template <typename T>
struct Avx2GemmTraits;

template <>
struct Avx2GemmTraits<float> {
    using Scalar = float;
    using Vec = __m256;

    // 6x16: same shape as the double kernel one vector width up. 12 accumulator registers keep the
    // two FMA pipes busy despite the 4-cycle FMA latency, and one k step needs 6 broadcasts plus 2
    // vector loads for 12 FMAs, which stays below one load per FMA. Both columns of the micro tile
    // are full vectors, so the storeback needs neither a transpose nor a mask.
    // See sgemm_micro_kernel_6x16_avx2.
    static constexpr size_t vector_lanes = 8;
    static constexpr size_t mr = 6;
    static constexpr size_t nr = 16;

    // The packed A panel (mc x kc, 192 KB) is meant to stay in L2, the packed B panel
    // (kc x nc, 512 KB) in L3. Measured on Zen 3 over n = 200 ... 2000. Most of the gain over the
    // previous 128/256/256 comes from mc alone: 128 is not a multiple of mr, so the last micro
    // panel carried 2 useful rows out of 6. Among the candidates with an aligned mc the spread is
    // about 1 %, which is the run-to-run drift of this machine; 192/512/256 led consistently.
    static constexpr size_t kc = 256;
    static constexpr size_t mc = 192; // 32 micro panels of mr rows
    static constexpr size_t nc = 512; // 32 micro panels of nr columns

    static constexpr size_t alignment = 64;
};

template <>
struct Avx2GemmTraits<double> {
    using Scalar = double;
    using Vec = __m256d;

    // 6x8: 12 accumulator registers keep the two FMA pipes busy despite the 4-cycle FMA
    // latency, and one k step needs 6 broadcasts plus 2 vector loads for 12 FMAs, which
    // stays below one load per FMA. See dgemm_micro_kernel_6x8_avx2.
    static constexpr size_t vector_lanes = 4;
    static constexpr size_t mr = 6;
    static constexpr size_t nr = 8;

    // A is packed once per nc block, so a wide nc cuts the packing work; kc trades the number of
    // passes over C against the panel sizes. The packed A panel (mc x kc, 192 KB) is meant to stay
    // in L2, the packed B panel (kc x nc, 1 MB) in L3. Measured on Zen 3 over n = 200 ... 2000.
    static constexpr size_t kc = 256;
    static constexpr size_t mc = 96;  // 16 micro panels of mr rows
    static constexpr size_t nc = 512; // 64 micro panels of nr columns

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

// Prefetches the element 'offset' positions after 'base'. The address is computed as an integer:
// it may lie past the end of the array, where forming a pointer would be undefined behavior.
// _mm_prefetch never faults.
template <typename T>
inline void prefetch_l1(const T* base, size_t offset) noexcept {
    const std::uintptr_t address = reinterpret_cast<std::uintptr_t>(base) + offset * sizeof(T);
    _mm_prefetch(reinterpret_cast<const char*>(address), _MM_HINT_T0);
}

inline void prefetch_l2(const void* ptr) noexcept {
    _mm_prefetch(static_cast<const char*>(ptr), _MM_HINT_T1);
}

inline void prefetch_stream(const void* ptr) noexcept {
    _mm_prefetch(static_cast<const char*>(ptr), _MM_HINT_NTA);
}
