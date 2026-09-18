#pragma once

#include <algorithm>
#include <cstddef>
#include <cstring>
#include <immintrin.h>
#include <type_traits>

#include "Avx2BlockedGemmCommon.h"

template <typename T>
[[nodiscard]] constexpr size_t packed_a_micro_panel_elements() noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    return Traits::mr * Traits::kc;
}

template <typename T>
[[nodiscard]] constexpr size_t packed_b_micro_panel_elements() noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    return Traits::nr * Traits::kc;
}

template <typename T>
[[nodiscard]] constexpr size_t packed_a_panel_stride_elements(size_t kc) noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    return Traits::mr * kc;
}

template <typename T>
[[nodiscard]] constexpr size_t packed_b_panel_stride_elements(size_t kc) noexcept {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;
    return Traits::nr * kc;
}

// -----------------------------------------------------------------------------
// Generic fallback packing helpers.
// Only float/double are expected in the AVX2 GEMM path, but the generic form
// keeps the helpers structurally complete.
// -----------------------------------------------------------------------------
template <typename T>
void pack_a_panel(const MatrixView<const T>& a,
    size_t m0,
    size_t k0,
    size_t mc,
    size_t kc,
    T* packed_a) {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;

    const size_t padded_mc = round_up_to_multiple<T>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<T>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        T* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;

        for (size_t k = 0; k < kc; ++k) {
            T* dst = dst_panel + k * Traits::mr;

            for (size_t r = 0; r < Traits::mr; ++r) {
                const size_t row = mp + r;
                dst[r] = row < mc ? a(m0 + row, k0 + k) : T(0);
            }
        }
    }
}

template <typename T>
void pack_b_panel(const MatrixView<const T>& b,
    size_t k0,
    size_t n0,
    size_t kc,
    size_t nc,
    T* packed_b) {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;

    const size_t padded_nc = round_up_to_multiple<T>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<T>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        T* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;

        for (size_t k = 0; k < kc; ++k) {
            T* dst = dst_panel + k * Traits::nr;

            for (size_t c = 0; c < Traits::nr; ++c) {
                const size_t col = np + c;
                dst[c] = col < nc ? b(k0 + k, n0 + col) : T(0);
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Specialized A packing: float
//
// Full-tile path avoids per-element bounds checks and uses stable row pointers.
// Layout stays:
//   for each k
//     store MR consecutive row values
// -----------------------------------------------------------------------------
template <>
inline void pack_a_panel<float>(const MatrixView<const float>& a,
    size_t m0,
    size_t k0,
    size_t mc,
    size_t kc,
    float* packed_a) {
    using Traits = Avx2GemmTraits<float>;

    const size_t padded_mc = round_up_to_multiple<float>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<float>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        float* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;

        const size_t remaining_rows = (mp < mc) ? (mc - mp) : 0;

        if (remaining_rows >= Traits::mr) {
            const float* row0 = a.data + (m0 + mp + 0) * a.stride + k0;
            const float* row1 = a.data + (m0 + mp + 1) * a.stride + k0;
            const float* row2 = a.data + (m0 + mp + 2) * a.stride + k0;
            const float* row3 = a.data + (m0 + mp + 3) * a.stride + k0;
            const float* row4 = a.data + (m0 + mp + 4) * a.stride + k0;
            const float* row5 = a.data + (m0 + mp + 5) * a.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0, k + 32);
                    prefetch_l1(row3, k + 32);
                }

                float* dst = dst_panel + k * Traits::mr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
            }
        }
        else {
            for (size_t k = 0; k < kc; ++k) {
                float* dst = dst_panel + k * Traits::mr;

                for (size_t r = 0; r < Traits::mr; ++r) {
                    const size_t row = mp + r;
                    dst[r] = row < mc ? a(m0 + row, k0 + k) : 0.0f;
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Specialized A packing: double
// -----------------------------------------------------------------------------
template <>
inline void pack_a_panel<double>(const MatrixView<const double>& a,
    size_t m0,
    size_t k0,
    size_t mc,
    size_t kc,
    double* packed_a) {
    using Traits = Avx2GemmTraits<double>;

    const size_t padded_mc = round_up_to_multiple<double>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<double>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        double* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;

        const size_t remaining_rows = (mp < mc) ? (mc - mp) : 0;

        if (remaining_rows >= Traits::mr) {
            const double* row0 = a.data + (m0 + mp + 0) * a.stride + k0;
            const double* row1 = a.data + (m0 + mp + 1) * a.stride + k0;
            const double* row2 = a.data + (m0 + mp + 2) * a.stride + k0;
            const double* row3 = a.data + (m0 + mp + 3) * a.stride + k0;
            const double* row4 = a.data + (m0 + mp + 4) * a.stride + k0;
            const double* row5 = a.data + (m0 + mp + 5) * a.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0, k + 32);
                    prefetch_l1(row3, k + 32);
                }

                double* dst = dst_panel + k * Traits::mr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
            }
        }
        else {
            for (size_t k = 0; k < kc; ++k) {
                double* dst = dst_panel + k * Traits::mr;

                for (size_t r = 0; r < Traits::mr; ++r) {
                    const size_t row = mp + r;
                    dst[r] = row < mc ? a(m0 + row, k0 + k) : 0.0;
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Specialized B packing: float
//
// Full-tile path copies NR contiguous values per source row.
// This matches row-major source layout well.
// -----------------------------------------------------------------------------
template <>
inline void pack_b_panel<float>(const MatrixView<const float>& b,
    size_t k0,
    size_t n0,
    size_t kc,
    size_t nc,
    float* packed_b) {
    using Traits = Avx2GemmTraits<float>;

    const size_t padded_nc = round_up_to_multiple<float>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<float>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        float* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;
        const size_t remaining_cols = (np < nc) ? (nc - np) : 0;

        if (remaining_cols >= Traits::nr) {
            for (size_t k = 0; k < kc; ++k) {
                const float* src = b.data + (k0 + k) * b.stride + (n0 + np);

                if ((k & 31) == 0) {
                    prefetch_l1(src, 32);
                }

                std::memcpy(dst_panel + k * Traits::nr, src, Traits::nr * sizeof(float));
            }
        }
        else {
            for (size_t k = 0; k < kc; ++k) {
                float* dst = dst_panel + k * Traits::nr;
                const float* src = b.data + (k0 + k) * b.stride + (n0 + np);

                size_t c = 0;
                for (; c < remaining_cols; ++c) {
                    dst[c] = src[c];
                }
                for (; c < Traits::nr; ++c) {
                    dst[c] = 0.0f;
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Specialized B packing: double
// -----------------------------------------------------------------------------
template <>
inline void pack_b_panel<double>(const MatrixView<const double>& b,
    size_t k0,
    size_t n0,
    size_t kc,
    size_t nc,
    double* packed_b) {
    using Traits = Avx2GemmTraits<double>;

    const size_t padded_nc = round_up_to_multiple<double>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<double>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        double* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;
        const size_t remaining_cols = (np < nc) ? (nc - np) : 0;

        if (remaining_cols >= Traits::nr) {
            for (size_t k = 0; k < kc; ++k) {
                const double* src = b.data + (k0 + k) * b.stride + (n0 + np);

                if ((k & 31) == 0) {
                    prefetch_l1(src, 32);
                }

                std::memcpy(dst_panel + k * Traits::nr, src, Traits::nr * sizeof(double));
            }
        }
        else {
            for (size_t k = 0; k < kc; ++k) {
                double* dst = dst_panel + k * Traits::nr;
                const double* src = b.data + (k0 + k) * b.stride + (n0 + np);

                size_t c = 0;
                for (; c < remaining_cols; ++c) {
                    dst[c] = src[c];
                }
                for (; c < Traits::nr; ++c) {
                    dst[c] = 0.0;
                }
            }
        }
    }
}

// One multiply-add step of a micro-kernel: a single rounded instruction where FMA is available, a
// separate multiply and add otherwise. The two forms round differently.
inline __m256 gemm_madd_ps(__m256 a, __m256 b, __m256 acc) noexcept {
#if defined(__FMA__) || (defined(_MSC_VER) && defined(__AVX2__)) // MSVC does not define __FMA__; /arch:AVX2 implies FMA
    return _mm256_fmadd_ps(a, b, acc);
#else
    return _mm256_add_ps(acc, _mm256_mul_ps(a, b));
#endif
}

inline __m256d gemm_madd_pd(__m256d a, __m256d b, __m256d acc) noexcept {
#if defined(__FMA__) || (defined(_MSC_VER) && defined(__AVX2__))
    return _mm256_fmadd_pd(a, b, acc);
#else
    return _mm256_add_pd(acc, _mm256_mul_pd(a, b));
#endif
}

// -----------------------------------------------------------------------------
// AVX2 micro-kernel: SGEMM 6x16
//
// The same shape as dgemm_micro_kernel_6x8_avx2, one vector width up. One k step broadcasts the 6
// rows of the A micro panel and loads the 16 columns of the B micro panel as two vectors: 8 loads
// feed 12 FMAs. Two properties decide the speed of this loop:
//
//   * 12 independent accumulators. An FMA has a latency of 4 cycles and two of them can start
//     per cycle, so 8 independent chains are the minimum to keep both pipes busy.
//   * Fewer than one load per FMA. The load unit retires 2 per cycle, the FMA pipes 2 per
//     cycle, so anything above one load per FMA makes the load unit the limit.
//
// The accumulators hold C in row order and both halves are full vectors, so the storeback is a
// plain load/add/store, without the transpose and the masked accesses the previous 8x6 kernel
// needed. Register budget: 12 accumulators + 2 B vectors + 1 broadcast = 15 of 16.
// -----------------------------------------------------------------------------
inline void sgemm_micro_kernel_6x16_avx2(const float* packed_a,
    const float* packed_b,
    float* c,
    size_t c_stride,
    size_t kc) noexcept {
    // acc<row><half>: rows 0..5 of C, columns 0..7 (half 0) and 8..15 (half 1)
    __m256 acc00 = _mm256_setzero_ps();
    __m256 acc01 = _mm256_setzero_ps();
    __m256 acc10 = _mm256_setzero_ps();
    __m256 acc11 = _mm256_setzero_ps();
    __m256 acc20 = _mm256_setzero_ps();
    __m256 acc21 = _mm256_setzero_ps();
    __m256 acc30 = _mm256_setzero_ps();
    __m256 acc31 = _mm256_setzero_ps();
    __m256 acc40 = _mm256_setzero_ps();
    __m256 acc41 = _mm256_setzero_ps();
    __m256 acc50 = _mm256_setzero_ps();
    __m256 acc51 = _mm256_setzero_ps();

    constexpr size_t MR = Avx2GemmTraits<float>::mr;
    constexpr size_t NR = Avx2GemmTraits<float>::nr;

    for (size_t k = 0; k < kc; ++k) {
        const float* a_ptr = packed_a + k * MR;
        const float* b_ptr = packed_b + k * NR;

        if ((k & 7) == 0) {
            prefetch_l1(a_ptr, 16 * MR);
            prefetch_l1(b_ptr, 16 * NR);
        }

        // Both panels are 64-byte aligned buffers, but an unaligned load costs nothing on an
        // aligned address and keeps the kernel correct if the panel geometry ever changes.
        const __m256 b0 = _mm256_loadu_ps(b_ptr + 0);
        const __m256 b1 = _mm256_loadu_ps(b_ptr + 8);

        __m256 a = _mm256_broadcast_ss(a_ptr + 0);
        acc00 = gemm_madd_ps(a, b0, acc00);
        acc01 = gemm_madd_ps(a, b1, acc01);

        a = _mm256_broadcast_ss(a_ptr + 1);
        acc10 = gemm_madd_ps(a, b0, acc10);
        acc11 = gemm_madd_ps(a, b1, acc11);

        a = _mm256_broadcast_ss(a_ptr + 2);
        acc20 = gemm_madd_ps(a, b0, acc20);
        acc21 = gemm_madd_ps(a, b1, acc21);

        a = _mm256_broadcast_ss(a_ptr + 3);
        acc30 = gemm_madd_ps(a, b0, acc30);
        acc31 = gemm_madd_ps(a, b1, acc31);

        a = _mm256_broadcast_ss(a_ptr + 4);
        acc40 = gemm_madd_ps(a, b0, acc40);
        acc41 = gemm_madd_ps(a, b1, acc41);

        a = _mm256_broadcast_ss(a_ptr + 5);
        acc50 = gemm_madd_ps(a, b0, acc50);
        acc51 = gemm_madd_ps(a, b1, acc51);
    }

    float* c0 = c + 0 * c_stride;
    float* c1 = c + 1 * c_stride;
    float* c2 = c + 2 * c_stride;
    float* c3 = c + 3 * c_stride;
    float* c4 = c + 4 * c_stride;
    float* c5 = c + 5 * c_stride;

    _mm256_storeu_ps(c0 + 0, _mm256_add_ps(_mm256_loadu_ps(c0 + 0), acc00));
    _mm256_storeu_ps(c0 + 8, _mm256_add_ps(_mm256_loadu_ps(c0 + 8), acc01));
    _mm256_storeu_ps(c1 + 0, _mm256_add_ps(_mm256_loadu_ps(c1 + 0), acc10));
    _mm256_storeu_ps(c1 + 8, _mm256_add_ps(_mm256_loadu_ps(c1 + 8), acc11));
    _mm256_storeu_ps(c2 + 0, _mm256_add_ps(_mm256_loadu_ps(c2 + 0), acc20));
    _mm256_storeu_ps(c2 + 8, _mm256_add_ps(_mm256_loadu_ps(c2 + 8), acc21));
    _mm256_storeu_ps(c3 + 0, _mm256_add_ps(_mm256_loadu_ps(c3 + 0), acc30));
    _mm256_storeu_ps(c3 + 8, _mm256_add_ps(_mm256_loadu_ps(c3 + 8), acc31));
    _mm256_storeu_ps(c4 + 0, _mm256_add_ps(_mm256_loadu_ps(c4 + 0), acc40));
    _mm256_storeu_ps(c4 + 8, _mm256_add_ps(_mm256_loadu_ps(c4 + 8), acc41));
    _mm256_storeu_ps(c5 + 0, _mm256_add_ps(_mm256_loadu_ps(c5 + 0), acc50));
    _mm256_storeu_ps(c5 + 8, _mm256_add_ps(_mm256_loadu_ps(c5 + 8), acc51));
}

// -----------------------------------------------------------------------------
// AVX2 micro-kernel: DGEMM 6x8
//
// One k step broadcasts the 6 rows of the A micro panel and loads the 8 columns of the B micro
// panel as two vectors: 8 loads feed 12 FMAs. Two properties decide the speed of this loop:
//
//   * 12 independent accumulators. An FMA has a latency of 4 cycles and two of them can start
//     per cycle, so 8 independent chains are the minimum to keep both pipes busy.
//   * Fewer than one load per FMA. The load unit retires 2 per cycle, the FMA pipes 2 per
//     cycle, so anything above one load per FMA makes the load unit the limit.
//
// The accumulators hold C in row order, so the storeback is a plain load/add/store without a
// transpose. Register budget: 12 accumulators + 2 B vectors + 1 broadcast = 15 of 16.
// -----------------------------------------------------------------------------
inline void dgemm_micro_kernel_6x8_avx2(const double* packed_a,
    const double* packed_b,
    double* c,
    size_t c_stride,
    size_t kc) noexcept {
    // acc<row><half>: rows 0..5 of C, columns 0..3 (half 0) and 4..7 (half 1)
    __m256d acc00 = _mm256_setzero_pd();
    __m256d acc01 = _mm256_setzero_pd();
    __m256d acc10 = _mm256_setzero_pd();
    __m256d acc11 = _mm256_setzero_pd();
    __m256d acc20 = _mm256_setzero_pd();
    __m256d acc21 = _mm256_setzero_pd();
    __m256d acc30 = _mm256_setzero_pd();
    __m256d acc31 = _mm256_setzero_pd();
    __m256d acc40 = _mm256_setzero_pd();
    __m256d acc41 = _mm256_setzero_pd();
    __m256d acc50 = _mm256_setzero_pd();
    __m256d acc51 = _mm256_setzero_pd();

    constexpr size_t MR = Avx2GemmTraits<double>::mr;
    constexpr size_t NR = Avx2GemmTraits<double>::nr;

    for (size_t k = 0; k < kc; ++k) {
        const double* a_ptr = packed_a + k * MR;
        const double* b_ptr = packed_b + k * NR;

        if ((k & 7) == 0) {
            prefetch_l1(a_ptr, 16 * MR);
            prefetch_l1(b_ptr, 16 * NR);
        }

        // Both panels are 64-byte aligned buffers, but an unaligned load costs nothing on an
        // aligned address and keeps the kernel correct if the panel geometry ever changes.
        const __m256d b0 = _mm256_loadu_pd(b_ptr + 0);
        const __m256d b1 = _mm256_loadu_pd(b_ptr + 4);

        __m256d a = _mm256_broadcast_sd(a_ptr + 0);
        acc00 = gemm_madd_pd(a, b0, acc00);
        acc01 = gemm_madd_pd(a, b1, acc01);

        a = _mm256_broadcast_sd(a_ptr + 1);
        acc10 = gemm_madd_pd(a, b0, acc10);
        acc11 = gemm_madd_pd(a, b1, acc11);

        a = _mm256_broadcast_sd(a_ptr + 2);
        acc20 = gemm_madd_pd(a, b0, acc20);
        acc21 = gemm_madd_pd(a, b1, acc21);

        a = _mm256_broadcast_sd(a_ptr + 3);
        acc30 = gemm_madd_pd(a, b0, acc30);
        acc31 = gemm_madd_pd(a, b1, acc31);

        a = _mm256_broadcast_sd(a_ptr + 4);
        acc40 = gemm_madd_pd(a, b0, acc40);
        acc41 = gemm_madd_pd(a, b1, acc41);

        a = _mm256_broadcast_sd(a_ptr + 5);
        acc50 = gemm_madd_pd(a, b0, acc50);
        acc51 = gemm_madd_pd(a, b1, acc51);
    }

    double* c0 = c + 0 * c_stride;
    double* c1 = c + 1 * c_stride;
    double* c2 = c + 2 * c_stride;
    double* c3 = c + 3 * c_stride;
    double* c4 = c + 4 * c_stride;
    double* c5 = c + 5 * c_stride;

    _mm256_storeu_pd(c0 + 0, _mm256_add_pd(_mm256_loadu_pd(c0 + 0), acc00));
    _mm256_storeu_pd(c0 + 4, _mm256_add_pd(_mm256_loadu_pd(c0 + 4), acc01));
    _mm256_storeu_pd(c1 + 0, _mm256_add_pd(_mm256_loadu_pd(c1 + 0), acc10));
    _mm256_storeu_pd(c1 + 4, _mm256_add_pd(_mm256_loadu_pd(c1 + 4), acc11));
    _mm256_storeu_pd(c2 + 0, _mm256_add_pd(_mm256_loadu_pd(c2 + 0), acc20));
    _mm256_storeu_pd(c2 + 4, _mm256_add_pd(_mm256_loadu_pd(c2 + 4), acc21));
    _mm256_storeu_pd(c3 + 0, _mm256_add_pd(_mm256_loadu_pd(c3 + 0), acc30));
    _mm256_storeu_pd(c3 + 4, _mm256_add_pd(_mm256_loadu_pd(c3 + 4), acc31));
    _mm256_storeu_pd(c4 + 0, _mm256_add_pd(_mm256_loadu_pd(c4 + 0), acc40));
    _mm256_storeu_pd(c4 + 4, _mm256_add_pd(_mm256_loadu_pd(c4 + 4), acc41));
    _mm256_storeu_pd(c5 + 0, _mm256_add_pd(_mm256_loadu_pd(c5 + 0), acc50));
    _mm256_storeu_pd(c5 + 4, _mm256_add_pd(_mm256_loadu_pd(c5 + 4), acc51));
}

// -----------------------------------------------------------------------------
// Transposed packing helpers
// -----------------------------------------------------------------------------
template <typename T>
void pack_a_panel_transposed(const MatrixView<const T>& a,
                             size_t m0,
                             size_t k0,
                             size_t mc,
                             size_t kc,
                             T* packed_a) {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;

    const size_t padded_mc = round_up_to_multiple<T>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<T>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        T* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;

        for (size_t k = 0; k < kc; ++k) {
            T* dst = dst_panel + k * Traits::mr;

            for (size_t r = 0; r < Traits::mr; ++r) {
                const size_t row = mp + r;
                dst[r] = row < mc ? a(k0 + k, m0 + row) : T(0);
            }
        }
    }
}

template <>
inline void pack_a_panel_transposed<float>(const MatrixView<const float>& a,
                                           size_t m0,
                                           size_t k0,
                                           size_t mc,
                                           size_t kc,
                                           float* packed_a) {
    using Traits = Avx2GemmTraits<float>;

    const size_t padded_mc = round_up_to_multiple<float>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<float>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        float* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;
        const size_t remaining_rows = (mp < mc) ? (mc - mp) : 0;

        if (remaining_rows >= Traits::mr) {
            for (size_t k = 0; k < kc; ++k) {
                const float* src = a.data + (k0 + k) * a.stride + (m0 + mp);

                if ((k & 31) == 0) {
                    prefetch_l1(src, 32);
                }

                std::memcpy(dst_panel + k * Traits::mr, src, Traits::mr * sizeof(float));
            }
        } else {
            for (size_t k = 0; k < kc; ++k) {
                float* dst = dst_panel + k * Traits::mr;

                for (size_t r = 0; r < Traits::mr; ++r) {
                    const size_t row = mp + r;
                    dst[r] = row < mc ? a(k0 + k, m0 + row) : 0.0f;
                }
            }
        }
    }
}

template <>
inline void pack_a_panel_transposed<double>(const MatrixView<const double>& a,
                                            size_t m0,
                                            size_t k0,
                                            size_t mc,
                                            size_t kc,
                                            double* packed_a) {
    using Traits = Avx2GemmTraits<double>;

    const size_t padded_mc = round_up_to_multiple<double>(mc, Traits::mr);
    const size_t micro_panel_stride = packed_a_panel_stride_elements<double>(kc);

    for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
        double* dst_panel = packed_a + (mp / Traits::mr) * micro_panel_stride;
        const size_t remaining_rows = (mp < mc) ? (mc - mp) : 0;

        if (remaining_rows >= Traits::mr) {
            for (size_t k = 0; k < kc; ++k) {
                const double* src = a.data + (k0 + k) * a.stride + (m0 + mp);

                if ((k & 31) == 0) {
                    prefetch_l1(src, 32);
                }

                std::memcpy(dst_panel + k * Traits::mr, src, Traits::mr * sizeof(double));
            }
        } else {
            for (size_t k = 0; k < kc; ++k) {
                double* dst = dst_panel + k * Traits::mr;

                for (size_t r = 0; r < Traits::mr; ++r) {
                    const size_t row = mp + r;
                    dst[r] = row < mc ? a(k0 + k, m0 + row) : 0.0;
                }
            }
        }
    }
}

template <typename T>
void pack_b_panel_transposed(const MatrixView<const T>& b,
                             size_t k0,
                             size_t n0,
                             size_t kc,
                             size_t nc,
                             T* packed_b) {
    using Traits = Avx2GemmTraits<std::remove_cv_t<T>>;

    const size_t padded_nc = round_up_to_multiple<T>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<T>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        T* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;

        for (size_t k = 0; k < kc; ++k) {
            T* dst = dst_panel + k * Traits::nr;

            for (size_t c = 0; c < Traits::nr; ++c) {
                const size_t col = np + c;
                dst[c] = col < nc ? b(n0 + col, k0 + k) : T(0);
            }
        }
    }
}

template <>
inline void pack_b_panel_transposed<float>(const MatrixView<const float>& b,
                                           size_t k0,
                                           size_t n0,
                                           size_t kc,
                                           size_t nc,
                                           float* packed_b) {
    using Traits = Avx2GemmTraits<float>;

    const size_t padded_nc = round_up_to_multiple<float>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<float>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        float* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;
        const size_t remaining_cols = (np < nc) ? (nc - np) : 0;

        if (remaining_cols >= Traits::nr) {
            // NR = 16 source rows. Addressing them through a base pointer plus a row index keeps
            // the loop to two live pointers; sixteen named pointers would spill.
            const float* base = b.data + (n0 + np) * b.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(base, k + 32);
                    prefetch_l1(base + 8 * b.stride, k + 32);
                }

                float* dst = dst_panel + k * Traits::nr;

                for (size_t c = 0; c < Traits::nr; ++c) {
                    dst[c] = base[c * b.stride + k];
                }
            }
        } else {
            for (size_t k = 0; k < kc; ++k) {
                float* dst = dst_panel + k * Traits::nr;

                for (size_t c = 0; c < Traits::nr; ++c) {
                    const size_t col = np + c;
                    dst[c] = col < nc ? b(n0 + col, k0 + k) : 0.0f;
                }
            }
        }
    }
}

template <>
inline void pack_b_panel_transposed<double>(const MatrixView<const double>& b,
                                            size_t k0,
                                            size_t n0,
                                            size_t kc,
                                            size_t nc,
                                            double* packed_b) {
    using Traits = Avx2GemmTraits<double>;

    const size_t padded_nc = round_up_to_multiple<double>(nc, Traits::nr);
    const size_t micro_panel_stride = packed_b_panel_stride_elements<double>(kc);

    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
        double* dst_panel = packed_b + (np / Traits::nr) * micro_panel_stride;
        const size_t remaining_cols = (np < nc) ? (nc - np) : 0;

        if (remaining_cols >= Traits::nr) {
            const double* row0 = b.data + (n0 + np + 0) * b.stride + k0;
            const double* row1 = b.data + (n0 + np + 1) * b.stride + k0;
            const double* row2 = b.data + (n0 + np + 2) * b.stride + k0;
            const double* row3 = b.data + (n0 + np + 3) * b.stride + k0;
            const double* row4 = b.data + (n0 + np + 4) * b.stride + k0;
            const double* row5 = b.data + (n0 + np + 5) * b.stride + k0;
            const double* row6 = b.data + (n0 + np + 6) * b.stride + k0;
            const double* row7 = b.data + (n0 + np + 7) * b.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0, k + 32);
                    prefetch_l1(row4, k + 32);
                }

                double* dst = dst_panel + k * Traits::nr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
                dst[6] = row6[k];
                dst[7] = row7[k];
            }
        } else {
            for (size_t k = 0; k < kc; ++k) {
                double* dst = dst_panel + k * Traits::nr;

                for (size_t c = 0; c < Traits::nr; ++c) {
                    const size_t col = np + c;
                    dst[c] = col < nc ? b(n0 + col, k0 + k) : 0.0;
                }
            }
        }
    }
}
