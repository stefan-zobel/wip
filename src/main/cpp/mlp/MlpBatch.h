#pragma once

#include <cstddef>
#include <span>
#include <stdexcept>
#include <string>
#include <vector>

#include "Avx2BlockedGemm.h"

// -----------------------------------------------------------------------------
// Buffers for training a SimpleMlp on mini-batches.
//
// An MLP is a fixed chain, not an arbitrary graph, so its forward and backward pass can be written
// directly as matrix operations - no tape is needed:
//
//   forward    Z = X * W^T + b          A = act(Z)
//   backward   dZ = dA (*) act'(Z)      dW = dZ^T * X     db = column sums of dZ     dA_prev = dZ * W
//
// DenseLayer::weights is (output_size x input_size) row-major, so dW comes out of the GEMM in
// exactly the layout the layer already uses, and both transposes are free: they are folded into the
// packing routines of the blocked GEMM.
// -----------------------------------------------------------------------------

// A row-major matrix over its own buffer.
//
// Every matrix owns a separate allocation on purpose. The GEMM rejects a call when the address range
// of C intersects that of A or B, and the test is a bounding box over the whole matrix, not an
// element-exact comparison. Two column ranges carved out of one shared buffer would therefore be
// rejected even though they share no element.
struct MlpMatrix {
    std::vector<double> data{};
    std::size_t rows = 0; // logical rows, never above the row count passed to allocate()
    std::size_t cols = 0;

    void allocate(std::size_t max_rows, std::size_t columns) {
        rows = max_rows;
        cols = columns;
        data.assign(max_rows * columns, 0.0);
    }

    // Shrinks the logical row count for a smaller batch. The buffer keeps its size, so this never
    // allocates - the last mini-batch of an epoch is usually shorter than the others.
    void set_rows(std::size_t row_count) noexcept {
        rows = row_count;
    }

    [[nodiscard]] double* row(std::size_t index) noexcept {
        return data.data() + index * cols;
    }

    [[nodiscard]] const double* row(std::size_t index) const noexcept {
        return data.data() + index * cols;
    }

    [[nodiscard]] MatrixView<double> view() noexcept {
        return MatrixView<double>{ data.data(), rows, cols, cols };
    }

    // MatrixView<T> is an aggregate without a converting constructor, so the const view has to be
    // built explicitly; there is no implicit MatrixView<double> -> MatrixView<const double>.
    [[nodiscard]] MatrixView<const double> const_view() const noexcept {
        return MatrixView<const double>{ data.data(), rows, cols, cols };
    }
};

// Every GEMM entry point is [[nodiscard]] and reports failure as a status instead of throwing.
// This turns a failure into an exception and names the operation, so a wrong shape or an accidental
// overlap is reported where it happened rather than as a wrong result.
inline void mlp_gemm(SimpleArena& scratch_arena,
    GemmTranspose op_a,
    GemmTranspose op_b,
    const MatrixView<const double>& a,
    const MatrixView<const double>& b,
    const MatrixView<double>& c,
    const char* what) {
    const GemmStatus status = gemm_blocked_avx2<double>(scratch_arena, op_a, op_b, a, b, c);
    if (status != GemmStatus::Ok) {
        throw std::runtime_error(std::string("MLP batch GEMM failed in ") + what + " (status "
            + std::to_string(static_cast<int>(status)) + ")");
    }
}

// Scratch buffers for one mini-batch. Owned by the caller, like the Tape of the scalar path:
// that keeps SimpleMlp copyable and cheap to copy.
//
// prepare() is the only member that allocates. It sizes everything for the largest batch that will
// be used, after which a training step allocates nothing at all.
struct MlpBatchWorkspace {
    MlpMatrix inputs{};                                // X, batch x widths.front()
    MlpMatrix targets{};                               // Y, batch x widths.back()
    std::vector<MlpMatrix> pre_activations{};          // Z[l], batch x out[l]
    std::vector<MlpMatrix> activations{};              // A[l], batch x out[l], after activation and dropout
    std::vector<MlpMatrix> deltas{};                   // dA[l] on entry, dZ[l] after the elementwise step
    std::vector<MlpMatrix> weight_gradients{};         // dW[l], out[l] x in[l]
    std::vector<std::vector<double>> dropout_masks{};  // batch x out[l], empty when the layer does not drop
    std::vector<std::vector<double>> bias_gradients{}; // db[l], out[l]
    std::vector<double> flat_gradients{};              // all parameter gradients, for clipping

    std::size_t max_batch_size = 0;
    std::size_t batch_size = 0;

    // widths holds the layer boundaries: widths[0] is the input width, widths[l + 1] the output
    // width of layer l. dropout_rates holds one rate per layer.
    void prepare(std::span<const std::size_t> widths,
        std::span<const double> dropout_rates,
        std::size_t max_batch) {
        if (widths.size() < 2) {
            throw std::invalid_argument("MlpBatchWorkspace::prepare needs at least one layer.");
        }
        if (dropout_rates.size() + 1 != widths.size()) {
            throw std::invalid_argument("MlpBatchWorkspace::prepare: one dropout rate per layer.");
        }
        if (max_batch == 0) {
            throw std::invalid_argument("MlpBatchWorkspace::prepare needs a positive batch size.");
        }

        const std::size_t layer_count = widths.size() - 1;
        max_batch_size = max_batch;

        inputs.allocate(max_batch, widths.front());
        targets.allocate(max_batch, widths.back());

        pre_activations.assign(layer_count, MlpMatrix{});
        activations.assign(layer_count, MlpMatrix{});
        deltas.assign(layer_count, MlpMatrix{});
        weight_gradients.assign(layer_count, MlpMatrix{});
        dropout_masks.assign(layer_count, std::vector<double>{});
        bias_gradients.assign(layer_count, std::vector<double>{});

        std::size_t parameter_count = 0;
        for (std::size_t layer = 0; layer < layer_count; ++layer) {
            const std::size_t in_width = widths[layer];
            const std::size_t out_width = widths[layer + 1];

            pre_activations[layer].allocate(max_batch, out_width);
            activations[layer].allocate(max_batch, out_width);
            deltas[layer].allocate(max_batch, out_width);
            weight_gradients[layer].allocate(out_width, in_width);
            bias_gradients[layer].assign(out_width, 0.0);

            if (dropout_rates[layer] > 0.0) {
                dropout_masks[layer].assign(max_batch * out_width, 0.0);
            }
            parameter_count += out_width * in_width + out_width;
        }

        flat_gradients.assign(parameter_count, 0.0);
        set_batch_size(max_batch);
    }

    // Allocation free: only the logical row counts change.
    void set_batch_size(std::size_t rows) {
        if (rows == 0 || rows > max_batch_size) {
            throw std::invalid_argument("MlpBatchWorkspace: batch size outside the prepared range.");
        }
        batch_size = rows;
        inputs.set_rows(rows);
        targets.set_rows(rows);
        for (std::size_t layer = 0; layer < pre_activations.size(); ++layer) {
            pre_activations[layer].set_rows(rows);
            activations[layer].set_rows(rows);
            deltas[layer].set_rows(rows);
        }
    }

    [[nodiscard]] bool is_prepared() const noexcept {
        return max_batch_size != 0;
    }
};
