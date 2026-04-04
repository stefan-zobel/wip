#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <type_traits>

#include "Avx2BlockedGemmCommon.h"
#include "Avx2BlockedGemmKernels.h"

template <typename T>
concept Avx2GemmScalar =
    std::same_as<std::remove_cv_t<T>, float> ||
    std::same_as<std::remove_cv_t<T>, double>;

enum class GemmTranspose : uint8_t {
    NoTrans = 0,
    Trans = 1
};

struct BlockedGemmConfig {
    size_t mc = 0;
    size_t nc = 0;
    size_t kc = 0;
};

template <Avx2GemmScalar T>
[[nodiscard]] static constexpr BlockedGemmConfig default_blocked_gemm_config() noexcept {
    using Traits = Avx2GemmTraits<T>;
    return BlockedGemmConfig{
        .mc = Traits::mc,
        .nc = Traits::nc,
        .kc = Traits::kc
    };
}

template <Avx2GemmScalar T>
static void zero_matrix(const MatrixView<T>& c) noexcept {
    for (size_t row = 0; row < c.rows; ++row) {
        std::fill_n(c.data + row * c.stride, c.cols, T(0));
    }
}

template <Avx2GemmScalar T>
static void add_edge_tile_to_c(const T* tile,
                               size_t tile_rows,
                               size_t tile_cols,
                               T* c,
                               size_t c_stride) noexcept {
    using Traits = Avx2GemmTraits<T>;

    for (size_t r = 0; r < tile_rows; ++r) {
        T* c_row = c + r * c_stride;
        const T* tile_row = tile + r * Traits::nr;

        for (size_t col = 0; col < tile_cols; ++col) {
            c_row[col] += tile_row[col];
        }
    }
}

inline void gemm_micro_kernel_dispatch(const float* packed_a,
                                       const float* packed_b,
                                       float* c,
                                       size_t c_stride,
                                       size_t kc) noexcept {
    sgemm_micro_kernel_8x6_avx2(packed_a, packed_b, c, c_stride, kc);
}

inline void gemm_micro_kernel_dispatch(const double* packed_a,
                                       const double* packed_b,
                                       double* c,
                                       size_t c_stride,
                                       size_t kc) noexcept {
    dgemm_micro_kernel_4x6_avx2(packed_a, packed_b, c, c_stride, kc);
}

template <Avx2GemmScalar T, bool TransA, bool TransB>
static void gemm_accumulate_blocked_avx2_impl(SimpleArena3& scratch_arena,
                                              const MatrixView<const T>& a,
                                              const MatrixView<const T>& b,
                                              const MatrixView<T>& c,
                                              BlockedGemmConfig config) {
    using Traits = Avx2GemmTraits<T>;

    if (!a.data || !b.data || !c.data) {
        return;
    }

    const size_t m = TransA ? a.cols : a.rows;
    const size_t k_a = TransA ? a.rows : a.cols;
    const size_t k_b = TransB ? b.cols : b.rows;
    const size_t n = TransB ? b.rows : b.cols;

    if (k_a != k_b || m != c.rows || n != c.cols) {
        return;
    }

    if (m == 0 || n == 0 || k_a == 0) {
        return;
    }

    const size_t mc_block = std::max(config.mc, Traits::mr);
    const size_t nc_block = std::max(config.nc, Traits::nr);
    const size_t kc_block = std::max<size_t>(config.kc, 1);

    StackAllocator gemm_scratch(scratch_arena);

    for (size_t n0 = 0; n0 < n; n0 += nc_block) {
        const size_t nc = std::min(nc_block, n - n0);

        for (size_t k0 = 0; k0 < k_a; k0 += kc_block) {
            const size_t kc = std::min(kc_block, k_a - k0);

            StackAllocator b_panel_scratch(scratch_arena);

            T* packed_b = allocate_scratch_elements<T>(
                b_panel_scratch,
                packed_b_panel_elements<T>(kc, nc),
                Traits::alignment);

            if (!packed_b) {
                return;
            }

            if constexpr (TransB) {
                pack_b_panel_transposed(b, k0, n0, kc, nc, packed_b);
            } else {
                pack_b_panel(b, k0, n0, kc, nc, packed_b);
            }

            for (size_t m0 = 0; m0 < m; m0 += mc_block) {
                const size_t mc = std::min(mc_block, m - m0);

                StackAllocator a_block_scratch(scratch_arena);

                T* packed_a = allocate_scratch_elements<T>(
                    a_block_scratch,
                    packed_a_panel_elements<T>(mc, kc),
                    Traits::alignment);

                if (!packed_a) {
                    return;
                }

                if constexpr (TransA) {
                    pack_a_panel_transposed(a, m0, k0, mc, kc, packed_a);
                } else {
                    pack_a_panel(a, m0, k0, mc, kc, packed_a);
                }

                const size_t padded_mc = round_up_to_multiple<T>(mc, Traits::mr);
                const size_t padded_nc = round_up_to_multiple<T>(nc, Traits::nr);

                const size_t a_panel_stride = packed_a_panel_stride_elements<T>(kc);
                const size_t b_panel_stride = packed_b_panel_stride_elements<T>(kc);

                for (size_t mp = 0; mp < padded_mc; mp += Traits::mr) {
                    const T* a_micro_panel =
                        packed_a + (mp / Traits::mr) * a_panel_stride;

                    for (size_t np = 0; np < padded_nc; np += Traits::nr) {
                        const T* b_micro_panel =
                            packed_b + (np / Traits::nr) * b_panel_stride;

                        const size_t valid_rows = std::min(Traits::mr, mc - std::min(mp, mc));
                        const size_t valid_cols = std::min(Traits::nr, nc - std::min(np, nc));

                        if (mp < mc && np < nc &&
                            valid_rows == Traits::mr &&
                            valid_cols == Traits::nr) {
                            T* c_tile = c.data + (m0 + mp) * c.stride + (n0 + np);

                            prefetch_l1(c_tile);
                            prefetch_l1(c_tile + c.stride);

                            gemm_micro_kernel_dispatch(a_micro_panel,
                                                       b_micro_panel,
                                                       c_tile,
                                                       c.stride,
                                                       kc);
                        } else if (mp < mc && np < nc) {
                            alignas(64) std::array<T, Avx2GemmTraits<T>::mr * Avx2GemmTraits<T>::nr> edge_tile{};
                            gemm_micro_kernel_dispatch(a_micro_panel,
                                                       b_micro_panel,
                                                       edge_tile.data(),
                                                       Traits::nr,
                                                       kc);

                            T* c_tile = c.data + (m0 + mp) * c.stride + (n0 + np);
                            add_edge_tile_to_c(edge_tile.data(),
                                               valid_rows,
                                               valid_cols,
                                               c_tile,
                                               c.stride);
                        }
                    }
                }
            }
        }
    }
}

template <Avx2GemmScalar T>
void gemm_nn_accumulate_blocked_avx2(SimpleArena3& scratch_arena,
                                     const MatrixView<const T>& a,
                                     const MatrixView<const T>& b,
                                     const MatrixView<T>& c,
                                     BlockedGemmConfig config = default_blocked_gemm_config<T>()) {
    gemm_accumulate_blocked_avx2_impl<T, false, false>(scratch_arena, a, b, c, config);
}

template <Avx2GemmScalar T>
void gemm_nn_blocked_avx2(SimpleArena3& scratch_arena,
                          const MatrixView<const T>& a,
                          const MatrixView<const T>& b,
                          const MatrixView<T>& c,
                          BlockedGemmConfig config = default_blocked_gemm_config<T>()) {
    zero_matrix(c);
    gemm_nn_accumulate_blocked_avx2(scratch_arena, a, b, c, config);
}

template <Avx2GemmScalar T>
void gemm_accumulate_blocked_avx2(SimpleArena3& scratch_arena,
                                  GemmTranspose op_a,
                                  GemmTranspose op_b,
                                  const MatrixView<const T>& a,
                                  const MatrixView<const T>& b,
                                  const MatrixView<T>& c,
                                  BlockedGemmConfig config = default_blocked_gemm_config<T>()) {
    if (op_a == GemmTranspose::NoTrans && op_b == GemmTranspose::NoTrans) {
        gemm_accumulate_blocked_avx2_impl<T, false, false>(scratch_arena, a, b, c, config);
    } else if (op_a == GemmTranspose::NoTrans && op_b == GemmTranspose::Trans) {
        gemm_accumulate_blocked_avx2_impl<T, false, true>(scratch_arena, a, b, c, config);
    } else if (op_a == GemmTranspose::Trans && op_b == GemmTranspose::NoTrans) {
        gemm_accumulate_blocked_avx2_impl<T, true, false>(scratch_arena, a, b, c, config);
    } else {
        gemm_accumulate_blocked_avx2_impl<T, true, true>(scratch_arena, a, b, c, config);
    }
}

template <Avx2GemmScalar T>
void gemm_blocked_avx2(SimpleArena3& scratch_arena,
                       GemmTranspose op_a,
                       GemmTranspose op_b,
                       const MatrixView<const T>& a,
                       const MatrixView<const T>& b,
                       const MatrixView<T>& c,
                       BlockedGemmConfig config = default_blocked_gemm_config<T>()) {
    zero_matrix(c);
    gemm_accumulate_blocked_avx2(scratch_arena, op_a, op_b, a, b, c, config);
}
