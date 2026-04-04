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
            const float* row6 = a.data + (m0 + mp + 6) * a.stride + k0;
            const float* row7 = a.data + (m0 + mp + 7) * a.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0 + k + 32);
                    prefetch_l1(row4 + k + 32);
                }

                float* dst = dst_panel + k * Traits::mr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
                dst[6] = row6[k];
                dst[7] = row7[k];
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

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0 + k + 32);
                    prefetch_l1(row2 + k + 32);
                }

                double* dst = dst_panel + k * Traits::mr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
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
                    prefetch_l1(src + 32);
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
                    prefetch_l1(src + 32);
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

inline void transpose8_ps(__m256& row0,
    __m256& row1,
    __m256& row2,
    __m256& row3,
    __m256& row4,
    __m256& row5,
    __m256& row6,
    __m256& row7) noexcept {
    __m256 t0 = _mm256_unpacklo_ps(row0, row1);
    __m256 t1 = _mm256_unpackhi_ps(row0, row1);
    __m256 t2 = _mm256_unpacklo_ps(row2, row3);
    __m256 t3 = _mm256_unpackhi_ps(row2, row3);
    __m256 t4 = _mm256_unpacklo_ps(row4, row5);
    __m256 t5 = _mm256_unpackhi_ps(row4, row5);
    __m256 t6 = _mm256_unpacklo_ps(row6, row7);
    __m256 t7 = _mm256_unpackhi_ps(row6, row7);

    __m256 s0 = _mm256_shuffle_ps(t0, t2, 0x44);
    __m256 s1 = _mm256_shuffle_ps(t0, t2, 0xEE);
    __m256 s2 = _mm256_shuffle_ps(t1, t3, 0x44);
    __m256 s3 = _mm256_shuffle_ps(t1, t3, 0xEE);
    __m256 s4 = _mm256_shuffle_ps(t4, t6, 0x44);
    __m256 s5 = _mm256_shuffle_ps(t4, t6, 0xEE);
    __m256 s6 = _mm256_shuffle_ps(t5, t7, 0x44);
    __m256 s7 = _mm256_shuffle_ps(t5, t7, 0xEE);

    row0 = _mm256_permute2f128_ps(s0, s4, 0x20);
    row1 = _mm256_permute2f128_ps(s1, s5, 0x20);
    row2 = _mm256_permute2f128_ps(s2, s6, 0x20);
    row3 = _mm256_permute2f128_ps(s3, s7, 0x20);
    row4 = _mm256_permute2f128_ps(s0, s4, 0x31);
    row5 = _mm256_permute2f128_ps(s1, s5, 0x31);
    row6 = _mm256_permute2f128_ps(s2, s6, 0x31);
    row7 = _mm256_permute2f128_ps(s3, s7, 0x31);
}

inline void transpose4_pd(__m256d& row0,
    __m256d& row1,
    __m256d& row2,
    __m256d& row3) noexcept {
    __m256d t0 = _mm256_unpacklo_pd(row0, row1);
    __m256d t1 = _mm256_unpackhi_pd(row0, row1);
    __m256d t2 = _mm256_unpacklo_pd(row2, row3);
    __m256d t3 = _mm256_unpackhi_pd(row2, row3);

    row0 = _mm256_permute2f128_pd(t0, t2, 0x20);
    row1 = _mm256_permute2f128_pd(t1, t3, 0x20);
    row2 = _mm256_permute2f128_pd(t0, t2, 0x31);
    row3 = _mm256_permute2f128_pd(t1, t3, 0x31);
}

// -----------------------------------------------------------------------------
// AVX2 micro-kernel: SGEMM 8x6
//
// The k-loop is unrolled by 4. B values are broadcast directly from memory.
// Storeback transposes 6 column vectors into 8 row vectors and updates C via
// AVX2 mask load/store.
// -----------------------------------------------------------------------------
inline void sgemm_micro_kernel_8x6_avx2(const float* packed_a,
    const float* packed_b,
    float* c,
    size_t c_stride,
    size_t kc) noexcept {
    __m256 acc0 = _mm256_setzero_ps();
    __m256 acc1 = _mm256_setzero_ps();
    __m256 acc2 = _mm256_setzero_ps();
    __m256 acc3 = _mm256_setzero_ps();
    __m256 acc4 = _mm256_setzero_ps();
    __m256 acc5 = _mm256_setzero_ps();

    constexpr size_t MR = Avx2GemmTraits<float>::mr;
    constexpr size_t NR = Avx2GemmTraits<float>::nr;

    size_t k = 0;

    for (; k + 3 < kc; k += 4) {
        const float* a0 = packed_a + (k + 0) * MR;
        const float* a1 = packed_a + (k + 1) * MR;
        const float* a2 = packed_a + (k + 2) * MR;
        const float* a3 = packed_a + (k + 3) * MR;

        const float* b0 = packed_b + (k + 0) * NR;
        const float* b1 = packed_b + (k + 1) * NR;
        const float* b2 = packed_b + (k + 2) * NR;
        const float* b3 = packed_b + (k + 3) * NR;

        prefetch_l1(a3 + 16 * MR);
        prefetch_l1(b3 + 16 * NR);

        const __m256 av0 = _mm256_load_ps(a0);
        const __m256 av1 = _mm256_load_ps(a1);
        const __m256 av2 = _mm256_load_ps(a2);
        const __m256 av3 = _mm256_load_ps(a3);

#if defined(__AVX2__)
        acc0 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 0), acc0);
        acc1 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 1), acc1);
        acc2 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 2), acc2);
        acc3 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 3), acc3);
        acc4 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 4), acc4);
        acc5 = _mm256_fmadd_ps(av0, _mm256_broadcast_ss(b0 + 5), acc5);

        acc0 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 0), acc0);
        acc1 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 1), acc1);
        acc2 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 2), acc2);
        acc3 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 3), acc3);
        acc4 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 4), acc4);
        acc5 = _mm256_fmadd_ps(av1, _mm256_broadcast_ss(b1 + 5), acc5);

        acc0 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 0), acc0);
        acc1 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 1), acc1);
        acc2 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 2), acc2);
        acc3 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 3), acc3);
        acc4 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 4), acc4);
        acc5 = _mm256_fmadd_ps(av2, _mm256_broadcast_ss(b2 + 5), acc5);

        acc0 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 0), acc0);
        acc1 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 1), acc1);
        acc2 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 2), acc2);
        acc3 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 3), acc3);
        acc4 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 4), acc4);
        acc5 = _mm256_fmadd_ps(av3, _mm256_broadcast_ss(b3 + 5), acc5);
#else
        acc0 = _mm256_add_ps(acc0, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 0)));
        acc1 = _mm256_add_ps(acc1, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 1)));
        acc2 = _mm256_add_ps(acc2, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 2)));
        acc3 = _mm256_add_ps(acc3, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 3)));
        acc4 = _mm256_add_ps(acc4, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 4)));
        acc5 = _mm256_add_ps(acc5, _mm256_mul_ps(av0, _mm256_broadcast_ss(b0 + 5)));

        acc0 = _mm256_add_ps(acc0, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 0)));
        acc1 = _mm256_add_ps(acc1, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 1)));
        acc2 = _mm256_add_ps(acc2, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 2)));
        acc3 = _mm256_add_ps(acc3, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 3)));
        acc4 = _mm256_add_ps(acc4, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 4)));
        acc5 = _mm256_add_ps(acc5, _mm256_mul_ps(av1, _mm256_broadcast_ss(b1 + 5)));

        acc0 = _mm256_add_ps(acc0, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 0)));
        acc1 = _mm256_add_ps(acc1, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 1)));
        acc2 = _mm256_add_ps(acc2, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 2)));
        acc3 = _mm256_add_ps(acc3, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 3)));
        acc4 = _mm256_add_ps(acc4, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 4)));
        acc5 = _mm256_add_ps(acc5, _mm256_mul_ps(av2, _mm256_broadcast_ss(b2 + 5)));

        acc0 = _mm256_add_ps(acc0, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 0)));
        acc1 = _mm256_add_ps(acc1, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 1)));
        acc2 = _mm256_add_ps(acc2, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 2)));
        acc3 = _mm256_add_ps(acc3, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 3)));
        acc4 = _mm256_add_ps(acc4, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 4)));
        acc5 = _mm256_add_ps(acc5, _mm256_mul_ps(av3, _mm256_broadcast_ss(b3 + 5)));
#endif
    }

    for (; k < kc; ++k) {
        const float* a_ptr = packed_a + k * MR;
        const float* b_ptr = packed_b + k * NR;

        const __m256 a = _mm256_load_ps(a_ptr);

#if defined(__AVX2__)
        acc0 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 0), acc0);
        acc1 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 1), acc1);
        acc2 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 2), acc2);
        acc3 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 3), acc3);
        acc4 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 4), acc4);
        acc5 = _mm256_fmadd_ps(a, _mm256_broadcast_ss(b_ptr + 5), acc5);
#else
        acc0 = _mm256_add_ps(acc0, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 0)));
        acc1 = _mm256_add_ps(acc1, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 1)));
        acc2 = _mm256_add_ps(acc2, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 2)));
        acc3 = _mm256_add_ps(acc3, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 3)));
        acc4 = _mm256_add_ps(acc4, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 4)));
        acc5 = _mm256_add_ps(acc5, _mm256_mul_ps(a, _mm256_broadcast_ss(b_ptr + 5)));
#endif
    }

    __m256 row0 = acc0;
    __m256 row1 = acc1;
    __m256 row2 = acc2;
    __m256 row3 = acc3;
    __m256 row4 = acc4;
    __m256 row5 = acc5;
    __m256 row6 = _mm256_setzero_ps();
    __m256 row7 = _mm256_setzero_ps();

    transpose8_ps(row0, row1, row2, row3, row4, row5, row6, row7);

    const __m256i store_mask = _mm256_setr_epi32(-1, -1, -1, -1, -1, -1, 0, 0);

    float* c0 = c + 0 * c_stride;
    float* c1 = c + 1 * c_stride;
    float* c2 = c + 2 * c_stride;
    float* c3 = c + 3 * c_stride;
    float* c4 = c + 4 * c_stride;
    float* c5 = c + 5 * c_stride;
    float* c6 = c + 6 * c_stride;
    float* c7 = c + 7 * c_stride;

    _mm256_maskstore_ps(c0, store_mask, _mm256_add_ps(_mm256_maskload_ps(c0, store_mask), row0));
    _mm256_maskstore_ps(c1, store_mask, _mm256_add_ps(_mm256_maskload_ps(c1, store_mask), row1));
    _mm256_maskstore_ps(c2, store_mask, _mm256_add_ps(_mm256_maskload_ps(c2, store_mask), row2));
    _mm256_maskstore_ps(c3, store_mask, _mm256_add_ps(_mm256_maskload_ps(c3, store_mask), row3));
    _mm256_maskstore_ps(c4, store_mask, _mm256_add_ps(_mm256_maskload_ps(c4, store_mask), row4));
    _mm256_maskstore_ps(c5, store_mask, _mm256_add_ps(_mm256_maskload_ps(c5, store_mask), row5));
    _mm256_maskstore_ps(c6, store_mask, _mm256_add_ps(_mm256_maskload_ps(c6, store_mask), row6));
    _mm256_maskstore_ps(c7, store_mask, _mm256_add_ps(_mm256_maskload_ps(c7, store_mask), row7));
}

// -----------------------------------------------------------------------------
// AVX2 micro-kernel: DGEMM 4x6
//
// The k-loop is unrolled by 4. The first 4 columns are written back as vectors.
// The last 2 columns remain a lightweight scalar tail.
// -----------------------------------------------------------------------------
inline void dgemm_micro_kernel_4x6_avx2(const double* packed_a,
    const double* packed_b,
    double* c,
    size_t c_stride,
    size_t kc) noexcept {
    __m256d acc0 = _mm256_setzero_pd();
    __m256d acc1 = _mm256_setzero_pd();
    __m256d acc2 = _mm256_setzero_pd();
    __m256d acc3 = _mm256_setzero_pd();
    __m256d acc4 = _mm256_setzero_pd();
    __m256d acc5 = _mm256_setzero_pd();

    constexpr size_t MR = Avx2GemmTraits<double>::mr;
    constexpr size_t NR = Avx2GemmTraits<double>::nr;

    size_t k = 0;

    for (; k + 3 < kc; k += 4) {
        const double* a0 = packed_a + (k + 0) * MR;
        const double* a1 = packed_a + (k + 1) * MR;
        const double* a2 = packed_a + (k + 2) * MR;
        const double* a3 = packed_a + (k + 3) * MR;

        const double* b0 = packed_b + (k + 0) * NR;
        const double* b1 = packed_b + (k + 1) * NR;
        const double* b2 = packed_b + (k + 2) * NR;
        const double* b3 = packed_b + (k + 3) * NR;

        prefetch_l1(a3 + 16 * MR);
        prefetch_l1(b3 + 16 * NR);

        const __m256d av0 = _mm256_load_pd(a0);
        const __m256d av1 = _mm256_load_pd(a1);
        const __m256d av2 = _mm256_load_pd(a2);
        const __m256d av3 = _mm256_load_pd(a3);

#if defined(__AVX2__)
        acc0 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 0), acc0);
        acc1 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 1), acc1);
        acc2 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 2), acc2);
        acc3 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 3), acc3);
        acc4 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 4), acc4);
        acc5 = _mm256_fmadd_pd(av0, _mm256_broadcast_sd(b0 + 5), acc5);

        acc0 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 0), acc0);
        acc1 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 1), acc1);
        acc2 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 2), acc2);
        acc3 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 3), acc3);
        acc4 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 4), acc4);
        acc5 = _mm256_fmadd_pd(av1, _mm256_broadcast_sd(b1 + 5), acc5);

        acc0 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 0), acc0);
        acc1 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 1), acc1);
        acc2 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 2), acc2);
        acc3 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 3), acc3);
        acc4 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 4), acc4);
        acc5 = _mm256_fmadd_pd(av2, _mm256_broadcast_sd(b2 + 5), acc5);

        acc0 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 0), acc0);
        acc1 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 1), acc1);
        acc2 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 2), acc2);
        acc3 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 3), acc3);
        acc4 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 4), acc4);
        acc5 = _mm256_fmadd_pd(av3, _mm256_broadcast_sd(b3 + 5), acc5);
#else
        acc0 = _mm256_add_pd(acc0, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 0)));
        acc1 = _mm256_add_pd(acc1, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 1)));
        acc2 = _mm256_add_pd(acc2, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 2)));
        acc3 = _mm256_add_pd(acc3, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 3)));
        acc4 = _mm256_add_pd(acc4, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 4)));
        acc5 = _mm256_add_pd(acc5, _mm256_mul_pd(av0, _mm256_broadcast_sd(b0 + 5)));

        acc0 = _mm256_add_pd(acc0, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 0)));
        acc1 = _mm256_add_pd(acc1, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 1)));
        acc2 = _mm256_add_pd(acc2, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 2)));
        acc3 = _mm256_add_pd(acc3, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 3)));
        acc4 = _mm256_add_pd(acc4, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 4)));
        acc5 = _mm256_add_pd(acc5, _mm256_mul_pd(av1, _mm256_broadcast_sd(b1 + 5)));

        acc0 = _mm256_add_pd(acc0, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 0)));
        acc1 = _mm256_add_pd(acc1, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 1)));
        acc2 = _mm256_add_pd(acc2, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 2)));
        acc3 = _mm256_add_pd(acc3, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 3)));
        acc4 = _mm256_add_pd(acc4, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 4)));
        acc5 = _mm256_add_pd(acc5, _mm256_mul_pd(av2, _mm256_broadcast_sd(b2 + 5)));

        acc0 = _mm256_add_pd(acc0, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 0)));
        acc1 = _mm256_add_pd(acc1, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 1)));
        acc2 = _mm256_add_pd(acc2, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 2)));
        acc3 = _mm256_add_pd(acc3, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 3)));
        acc4 = _mm256_add_pd(acc4, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 4)));
        acc5 = _mm256_add_pd(acc5, _mm256_mul_pd(av3, _mm256_broadcast_sd(b3 + 5)));
#endif
    }

    for (; k < kc; ++k) {
        const double* a_ptr = packed_a + k * MR;
        const double* b_ptr = packed_b + k * NR;

        const __m256d a = _mm256_load_pd(a_ptr);

#if defined(__AVX2__)
        acc0 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 0), acc0);
        acc1 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 1), acc1);
        acc2 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 2), acc2);
        acc3 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 3), acc3);
        acc4 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 4), acc4);
        acc5 = _mm256_fmadd_pd(a, _mm256_broadcast_sd(b_ptr + 5), acc5);
#else
        acc0 = _mm256_add_pd(acc0, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 0)));
        acc1 = _mm256_add_pd(acc1, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 1)));
        acc2 = _mm256_add_pd(acc2, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 2)));
        acc3 = _mm256_add_pd(acc3, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 3)));
        acc4 = _mm256_add_pd(acc4, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 4)));
        acc5 = _mm256_add_pd(acc5, _mm256_mul_pd(a, _mm256_broadcast_sd(b_ptr + 5)));
#endif
    }

    __m256d row0 = acc0;
    __m256d row1 = acc1;
    __m256d row2 = acc2;
    __m256d row3 = acc3;
    transpose4_pd(row0, row1, row2, row3);

    alignas(32) double tail4[4];
    alignas(32) double tail5[4];
    _mm256_store_pd(tail4, acc4);
    _mm256_store_pd(tail5, acc5);

    double* c0 = c + 0 * c_stride;
    double* c1 = c + 1 * c_stride;
    double* c2 = c + 2 * c_stride;
    double* c3 = c + 3 * c_stride;

    _mm256_storeu_pd(c0, _mm256_add_pd(_mm256_loadu_pd(c0), row0));
    _mm256_storeu_pd(c1, _mm256_add_pd(_mm256_loadu_pd(c1), row1));
    _mm256_storeu_pd(c2, _mm256_add_pd(_mm256_loadu_pd(c2), row2));
    _mm256_storeu_pd(c3, _mm256_add_pd(_mm256_loadu_pd(c3), row3));

    c0[4] += tail4[0];
    c0[5] += tail5[0];
    c1[4] += tail4[1];
    c1[5] += tail5[1];
    c2[4] += tail4[2];
    c2[5] += tail5[2];
    c3[4] += tail4[3];
    c3[5] += tail5[3];
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
                    prefetch_l1(src + 32);
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
                    prefetch_l1(src + 32);
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
            const float* row0 = b.data + (n0 + np + 0) * b.stride + k0;
            const float* row1 = b.data + (n0 + np + 1) * b.stride + k0;
            const float* row2 = b.data + (n0 + np + 2) * b.stride + k0;
            const float* row3 = b.data + (n0 + np + 3) * b.stride + k0;
            const float* row4 = b.data + (n0 + np + 4) * b.stride + k0;
            const float* row5 = b.data + (n0 + np + 5) * b.stride + k0;

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0 + k + 32);
                    prefetch_l1(row3 + k + 32);
                }

                float* dst = dst_panel + k * Traits::nr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
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

            for (size_t k = 0; k < kc; ++k) {
                if ((k & 31) == 0) {
                    prefetch_l1(row0 + k + 32);
                    prefetch_l1(row3 + k + 32);
                }

                double* dst = dst_panel + k * Traits::nr;
                dst[0] = row0[k];
                dst[1] = row1[k];
                dst[2] = row2[k];
                dst[3] = row3[k];
                dst[4] = row4[k];
                dst[5] = row5[k];
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
