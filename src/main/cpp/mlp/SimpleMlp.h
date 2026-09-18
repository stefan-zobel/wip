#pragma once

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <optional>
#include <random>
#include <span>
#include <string>
#include <stdexcept>
#include <vector>

#include "reverse_mode.h"
#include "MlpBatch.h"

enum class MlpActivation {
    Linear,
    Tanh,
    Sigmoid,
    Swish,
    Relu
};

// Which of the two training paths a caller wants. Both compute the same gradients, see
// mlp_batch_test.batch_of_one_matches_train_step; MiniBatch is roughly thirty times faster.
enum class MlpTrainingMode {
    ScalarTape, // train_step / train_epoch: one reverse-mode tape per sample
    MiniBatch   // train_batch / train_epoch_batched: matrix operations through the blocked GEMM
};

enum class MlpOptimizer {
    MomentumSgd,
    Adam
};

struct MlpSample {
    std::vector<double> input{};
    std::vector<double> target{};
};

struct DenseLayer {
    std::size_t input_size{};
    std::size_t output_size{};
    MlpActivation activation{ MlpActivation::Tanh };
    double dropout_rate{ 0.0 };
    std::vector<double> weights{};
    std::vector<double> biases{};

    // Optimizer state of the optimizer that SimpleMlp::train_step used last (it is reset when the
    // optimizer changes): the velocities for momentum SGD, or Adam's first moment 'm'
    std::vector<double> weight_velocities;
    std::vector<double> bias_velocities;

    // for Adam's second moment 'v'
    std::vector<double> v_weights;
    std::vector<double> v_biases;

    DenseLayer() = default;

    DenseLayer(std::size_t in, std::size_t out, MlpActivation act, double dropout = 0.0)
        : input_size(in), output_size(out), activation(act), dropout_rate(dropout),
          weights(in * out, 0.0), biases(out, 0.0),
          weight_velocities(in* out, 0.0), bias_velocities(out, 0.0),
          v_weights(in* out, 0.0), v_biases(out, 0.0) {
    }

    void initialize(std::mt19937& rng) {
        // He initialization (normal) for ReLU/Swish: std = sqrt(2 / fan_in)
        // Glorot/Xavier uniform for Tanh/Sigmoid/Linear: bound = sqrt(6 / (fan_in + fan_out))
        if (activation == MlpActivation::Relu || activation == MlpActivation::Swish) {
            const double std_dev = std::sqrt(2.0 / static_cast<double>(input_size));
            std::normal_distribution<double> dist(0.0, std_dev);
            for (double& weight : weights) {
                weight = dist(rng);
            }
        } else {
            const double bound = std::sqrt(6.0 / static_cast<double>(input_size + output_size));
            std::uniform_real_distribution<double> dist(-bound, bound);
            for (double& weight : weights) {
                weight = dist(rng);
            }
        }

        std::fill(biases.begin(), biases.end(), 0.0);
        reset_optimizer_state();
    }

    void reset_optimizer_state() {
        std::fill(weight_velocities.begin(), weight_velocities.end(), 0.0);
        std::fill(bias_velocities.begin(), bias_velocities.end(), 0.0);
        std::fill(v_weights.begin(), v_weights.end(), 0.0);
        std::fill(v_biases.begin(), v_biases.end(), 0.0);
    }
};

class SimpleMlp {
    // Structural layout mirroring the old LayerVars but for internal reuse
    struct ReusableLayerVars {
        std::vector<Var<double>> weights;
        std::vector<Var<double>> biases;
    };

public:
    SimpleMlp() = default;

    SimpleMlp(std::span<const std::size_t> layer_sizes,
              std::span<const MlpActivation> activations,
              std::uint32_t seed = 20260626u) {
        reset(layer_sizes, activations, seed);
    }

    SimpleMlp(std::span<const std::size_t> layer_sizes,
              std::span<const MlpActivation> activations,
              std::span<const double> dropout_rates,
              std::uint32_t seed = 20260626u) {
        reset(layer_sizes, activations, dropout_rates, seed);
    }

    void reset(std::span<const std::size_t> layer_sizes,
               std::span<const MlpActivation> activations,
               std::uint32_t seed = 20260626u) {
        reset(layer_sizes, activations, {}, seed);
    }

    void reset(std::span<const std::size_t> layer_sizes,
               std::span<const MlpActivation> activations,
               std::span<const double> dropout_rates,
               std::uint32_t seed = 20260626u) {
        if (layer_sizes.size() < 2) {
            throw std::invalid_argument("SimpleMlp requires at least input and output sizes.");
        }
        if (activations.size() != layer_sizes.size() - 1) {
            throw std::invalid_argument("SimpleMlp needs one activation per dense layer.");
        }
        if (!dropout_rates.empty() && dropout_rates.size() != activations.size()) {
            throw std::invalid_argument("SimpleMlp needs either zero dropout rates or one dropout rate per dense layer.");
        }

        std::mt19937 rng(seed);
        dropout_rng_.seed(seed ^ 0x9E3779B9u);
        layers_.clear();
        layers_.reserve(layer_sizes.size() - 1);

        for (std::size_t i = 0; i + 1 < layer_sizes.size(); ++i) {
            const double dropout_rate = dropout_rates.empty() ? 0.0 : dropout_rates[i];
            if (dropout_rate < 0.0 || dropout_rate >= 1.0) {
                throw std::invalid_argument("Dropout rates must be in the range [0, 1). ");
            }
            layers_.emplace_back(layer_sizes[i], layer_sizes[i + 1], activations[i], dropout_rate);
            layers_.back().initialize(rng);
        }

        // Reset Adam step counter whenever the network is rebuilt
        adam_step_ = 0;
        last_optimizer_.reset();

        // Allocate memory arenas for runtime reuse
        initialize_reusable_buffers();
    }

    [[nodiscard]] std::size_t input_size() const {
        return layers_.empty() ? 0 : layers_.front().input_size;
    }

    [[nodiscard]] std::size_t output_size() const {
        return layers_.empty() ? 0 : layers_.back().output_size;
    }

    [[nodiscard]] const std::vector<DenseLayer>& layers() const {
        return layers_;
    }

    [[nodiscard]] std::vector<double> predict(std::span<const double> input) const {
        assert_shape(input.size(), input_size(), "input");

        std::vector<double> activations(input.begin(), input.end());
        for (const DenseLayer& layer : layers_) {
            activations = forward_layer(layer, activations);
        }
        return activations;
    }

    [[nodiscard]] double sample_loss(std::span<const double> input, std::span<const double> target) const {
        const std::vector<double> prediction = predict(input);
        return mse_loss(prediction, target);
    }

    [[nodiscard]] double dataset_loss(std::span<const MlpSample> samples) const {
        if (samples.empty()) {
            return 0.0;
        }

        double total = 0.0;
        for (const MlpSample& sample : samples) {
            total += sample_loss(sample.input, sample.target);
        }
        return total / static_cast<double>(samples.size());
    }

    // Execution with 0 allocations supporting dual optimizers. Training is per sample, so the
    // gradient clipping below also applies per sample. Switching the optimizer resets the optimizer
    // state (momentum SGD and Adam share the moment vectors).
    double train_step(std::span<const double> input,
        std::span<const double> target,
        double learning_rate,
        Tape<double>& tape,
        MlpOptimizer optimizer,
        double weight_decay) {
        assert_shape(input.size(), input_size(), "input");
        assert_shape(target.size(), output_size(), "target");

        if (last_optimizer_ != optimizer) {
            for (DenseLayer& layer : layers_) {
                layer.reset_optimizer_state();
            }
            adam_step_ = 0;
            last_optimizer_ = optimizer;
        }

        // 1. Reset tape memory
        tape.reset();
        tape.reserve(estimate_required_nodes(input.size()));

        // 2. Prepare raw inputs
        activation_buffer_a_.clear();
        for (double value : input) {
            activation_buffer_a_.push_back(tape.constant(value));
        }

        // 3. Build the layer execution graph inplace
        for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
            const DenseLayer& layer = layers_[layer_idx];
            ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];

            ad_layer.weights.clear();
            ad_layer.biases.clear();

            for (double weight : layer.weights) {
                ad_layer.weights.push_back(tape.input(weight));
            }
            for (double bias : layer.biases) {
                ad_layer.biases.push_back(tape.input(bias));
            }

            // Ping-pong buffer swap allocation assignment
            auto& current_input = (layer_idx % 2 == 0) ? activation_buffer_a_ : activation_buffer_b_;
            auto& current_output = (layer_idx % 2 == 0) ? activation_buffer_b_ : activation_buffer_a_;

            current_output.clear();
            forward_layer_inplace(layer, ad_layer, current_input, current_output, dropout_rng_);
        }

        // 4. Trace graph down to loss evaluation
        auto& final_activations = (layers_.size() % 2 == 0) ? activation_buffer_a_ : activation_buffer_b_;

        Var<double> loss = tape.constant(0.0);
        for (std::size_t i = 0; i < final_activations.size(); ++i) {
            const Var<double> diff = final_activations[i] - target[i];
            loss += diff * diff;
        }
        if (!final_activations.empty()) {
            loss = loss / static_cast<double>(final_activations.size());
        }

        tape.backward(loss);

        // 5. Collect the parameter gradients into one flat array and hand them to the shared
        //    optimizer. train_batch fills the same array from the GEMM results, so clipping,
        //    momentum SGD and Adam exist exactly once and cannot drift between the two paths.
        {
            std::size_t next = 0;
            for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
                const ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];
                for (const Var<double>& w : ad_layer.weights) {
                    flat_gradients_[next++] = w.gradient();
                }
                for (const Var<double>& b : ad_layer.biases) {
                    flat_gradients_[next++] = b.gradient();
                }
            }
        }
        apply_parameter_update(flat_gradients_, learning_rate, optimizer, weight_decay);

        return loss.value();
    }

    // Sizes a workspace for this topology and the largest mini-batch that will be used. Call it once;
    // forward_batch and train_batch then allocate nothing.
    void prepare_workspace(MlpBatchWorkspace& workspace, std::size_t max_batch_size) const {
        std::vector<std::size_t> widths;
        std::vector<double> dropout_rates;
        widths.reserve(layers_.size() + 1);
        dropout_rates.reserve(layers_.size());

        widths.push_back(input_size());
        for (const DenseLayer& layer : layers_) {
            widths.push_back(layer.output_size);
            dropout_rates.push_back(layer.dropout_rate);
        }
        workspace.prepare(widths, dropout_rates, max_batch_size);
    }

    // Forward pass over a whole mini-batch, reading workspace.inputs and filling the per-layer
    // pre-activations and activations. With training == false no dropout is applied, which makes
    // the result match predict() row by row (up to the summation order of the GEMM).
    // Not const: with training == true the dropout draws advance dropout_rng_.
    void forward_batch(MlpBatchWorkspace& workspace, SimpleArena& scratch_arena, bool training) {
        require_prepared(workspace);

        const MlpMatrix* layer_input = &workspace.inputs;
        for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
            const DenseLayer& layer = layers_[layer_idx];
            MlpMatrix& pre_activation = workspace.pre_activations[layer_idx];
            MlpMatrix& activation = workspace.activations[layer_idx];

            // Z = X * W^T. W is stored (output_size x input_size), so op(B) = W^T is (in x out).
            mlp_gemm(scratch_arena, GemmTranspose::NoTrans, GemmTranspose::Trans,
                layer_input->const_view(), weight_view(layer), pre_activation.view(),
                "Z = X * W^T");

            const bool drops = training && layer.dropout_rate > 0.0;
            std::vector<double>& mask = workspace.dropout_masks[layer_idx];

            for (std::size_t row = 0; row < pre_activation.rows; ++row) {
                double* z_row = pre_activation.row(row);
                double* a_row = activation.row(row);
                double* mask_row = drops ? mask.data() + row * layer.output_size : nullptr;

                for (std::size_t col = 0; col < layer.output_size; ++col) {
                    // The GEMM does not add the bias, so it goes in here, before the activation.
                    z_row[col] += layer.biases[col];
                    double value = apply_activation(layer.activation, z_row[col]);
                    if (mask_row) {
                        const double scale = draw_dropout_scale(layer.dropout_rate, dropout_rng_);
                        mask_row[col] = scale;
                        value *= scale;
                    }
                    a_row[col] = value;
                }
            }

            layer_input = &activation;
        }
    }

    // One optimizer step on a whole mini-batch. The caller fills workspace.inputs and
    // workspace.targets and calls set_batch_size first; see train_epoch_batched.
    //
    // Same arithmetic as train_step, only over B rows at once: the loss is the mean squared error
    // over batch and outputs, clipping uses the global norm of the batch gradient, and Adam counts
    // one step per call. With B == 1 every one of those reduces to what train_step does, which is
    // what mlp_batch_test.batch_of_one_matches_train_step pins down.
    double train_batch(double learning_rate,
        MlpBatchWorkspace& workspace,
        SimpleArena& scratch_arena,
        MlpOptimizer optimizer,
        double weight_decay) {
        require_prepared(workspace);

        if (last_optimizer_ != optimizer) {
            for (DenseLayer& layer : layers_) {
                layer.reset_optimizer_state();
            }
            adam_step_ = 0;
            last_optimizer_ = optimizer;
        }

        forward_batch(workspace, scratch_arena, /*training=*/true);

        const std::size_t batch = workspace.batch_size;
        const std::size_t last = layers_.size() - 1;
        const std::size_t out_width = layers_[last].output_size;
        const double sample_scale = 1.0 / static_cast<double>(batch);

        // Loss and the seed of the backward pass. mse per row is sum((a - y)^2) / out_width, and
        // the batch loss is the mean over rows; the derivative carries both divisors.
        double loss = 0.0;
        {
            MlpMatrix& prediction = workspace.activations[last];
            MlpMatrix& delta = workspace.deltas[last];
            const double grad_scale = 2.0 / static_cast<double>(batch * out_width);

            for (std::size_t row = 0; row < batch; ++row) {
                const double* a_row = prediction.row(row);
                const double* y_row = workspace.targets.row(row);
                double* d_row = delta.row(row);
                double row_loss = 0.0;

                for (std::size_t col = 0; col < out_width; ++col) {
                    const double diff = a_row[col] - y_row[col];
                    row_loss += diff * diff;
                    d_row[col] = grad_scale * diff;
                }
                loss += row_loss / static_cast<double>(out_width);
            }
            loss *= sample_scale;
        }

        // Backward through the chain. deltas[l] holds dA on entry and becomes dZ in place.
        for (std::size_t layer_idx = layers_.size(); layer_idx-- > 0;) {
            const DenseLayer& layer = layers_[layer_idx];
            MlpMatrix& delta = workspace.deltas[layer_idx];
            const MlpMatrix& pre_activation = workspace.pre_activations[layer_idx];
            const MlpMatrix& activation = workspace.activations[layer_idx];
            const std::vector<double>& mask = workspace.dropout_masks[layer_idx];
            const bool drops = !mask.empty();

            // dZ = dA (*) mask (*) act'(Z). Dropout scales the activation output, so its factor
            // multiplies the incoming gradient before the activation derivative is applied.
            for (std::size_t row = 0; row < batch; ++row) {
                double* d_row = delta.row(row);
                const double* z_row = pre_activation.row(row);
                const double* a_row = activation.row(row);
                const double* mask_row = drops ? mask.data() + row * layer.output_size : nullptr;

                for (std::size_t col = 0; col < layer.output_size; ++col) {
                    double gradient = d_row[col];
                    double activation_value = a_row[col];
                    if (mask_row) {
                        gradient *= mask_row[col];
                        // Undo the inverted-dropout scaling to recover act(z) for the derivative.
                        if (mask_row[col] != 0.0) {
                            activation_value /= mask_row[col];
                        }
                        else {
                            activation_value = apply_activation(layer.activation, z_row[col]);
                        }
                    }
                    d_row[col] = gradient * activation_derivative(layer.activation, z_row[col], activation_value);
                }
            }

            // db = column sums of dZ
            std::vector<double>& bias_gradient = workspace.bias_gradients[layer_idx];
            for (std::size_t col = 0; col < layer.output_size; ++col) {
                bias_gradient[col] = 0.0;
            }
            for (std::size_t row = 0; row < batch; ++row) {
                const double* d_row = delta.row(row);
                for (std::size_t col = 0; col < layer.output_size; ++col) {
                    bias_gradient[col] += d_row[col];
                }
            }

            // dW = dZ^T * X, which lands in exactly the layout of DenseLayer::weights.
            const MlpMatrix& layer_input =
                (layer_idx == 0) ? workspace.inputs : workspace.activations[layer_idx - 1];
            mlp_gemm(scratch_arena, GemmTranspose::Trans, GemmTranspose::NoTrans,
                delta.const_view(), layer_input.const_view(),
                workspace.weight_gradients[layer_idx].view(), "dW = dZ^T * X");

            // dA of the layer below = dZ * W. Not needed for the first layer: nothing backpropagates
            // into the network input.
            if (layer_idx > 0) {
                mlp_gemm(scratch_arena, GemmTranspose::NoTrans, GemmTranspose::NoTrans,
                    delta.const_view(), weight_view(layer),
                    workspace.deltas[layer_idx - 1].view(), "dA = dZ * W");
            }
        }

        // Same flat layout and the same shared optimizer as train_step.
        {
            std::size_t next = 0;
            for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
                const std::vector<double>& weight_gradient = workspace.weight_gradients[layer_idx].data;
                for (double g : weight_gradient) {
                    flat_gradients_[next++] = g;
                }
                for (double g : workspace.bias_gradients[layer_idx]) {
                    flat_gradients_[next++] = g;
                }
            }
        }
        apply_parameter_update(flat_gradients_, learning_rate, optimizer, weight_decay);

        return loss;
    }

    // Counterpart of train_epoch: walks the samples in order in mini-batches of at most
    // batch_size rows and returns the mean loss over the batches.
    double train_epoch_batched(std::span<const MlpSample> samples,
        std::size_t batch_size,
        double learning_rate,
        MlpBatchWorkspace& workspace,
        SimpleArena& scratch_arena,
        MlpOptimizer optimizer,
        double weight_decay = 0.0) {
        if (samples.empty() || batch_size == 0) {
            return 0.0;
        }

        double total = 0.0;
        std::size_t batches = 0;
        for (std::size_t first = 0; first < samples.size(); first += batch_size) {
            const std::size_t rows = std::min(batch_size, samples.size() - first);
            workspace.set_batch_size(rows);

            for (std::size_t row = 0; row < rows; ++row) {
                const MlpSample& sample = samples[first + row];
                assert_shape(sample.input.size(), input_size(), "input");
                assert_shape(sample.target.size(), output_size(), "target");
                std::copy(sample.input.begin(), sample.input.end(), workspace.inputs.row(row));
                std::copy(sample.target.begin(), sample.target.end(), workspace.targets.row(row));
            }

            total += train_batch(learning_rate, workspace, scratch_arena, optimizer, weight_decay);
            ++batches;
        }
        return total / static_cast<double>(batches);
    }

    double train_epoch(std::span<const MlpSample> samples, double learning_rate, Tape<double>& tape, MlpOptimizer optimizer, double weight_decay = 0.0) {
        if (samples.empty()) {
            return 0.0;
        }

        double total = 0.0;
        for (const MlpSample& sample : samples) {
            total += train_step(sample.input, sample.target, learning_rate, tape, optimizer, weight_decay);
        }
        return total / static_cast<double>(samples.size());
    }

private:
    std::vector<DenseLayer> layers_{};

    // Class-level pre-allocated structures
    std::vector<ReusableLayerVars> reusable_ad_layers_{};
    std::vector<Var<double>> activation_buffer_a_{};
    std::vector<Var<double>> activation_buffer_b_{};
    std::mt19937 dropout_rng_{};

    // Per-update step counter for correct Adam bias correction (not per-epoch)
    long long adam_step_ = 0;
    // Optimizer of the last train_step; a different one resets the optimizer state
    std::optional<MlpOptimizer> last_optimizer_{};
    // Gradient clipping scale factor, recomputed each update before parameter updates
    double clip_scale_ = 1.0;
    // All parameter gradients of one update, laid out per layer as weights then biases. Filled from
    // the tape by train_step and from the GEMM results by train_batch.
    std::vector<double> flat_gradients_{};

    // Gradient clipping and the parameter update. 'gradients' holds, layer by layer, first every
    // weight gradient in the layout of DenseLayer::weights, then every bias gradient - the order in
    // which both train_step and train_batch fill the array.
    void apply_parameter_update(std::span<const double> gradients,
        double learning_rate,
        MlpOptimizer optimizer,
        double weight_decay) {
        // Rescale all parameter gradients so their global L2 norm stays at or below max_grad_norm.
        constexpr double max_grad_norm = 5.0;
        {
            double sq_norm = 0.0;
            for (double g : gradients) {
                sq_norm += g * g;
            }
            clip_scale_ = (sq_norm > max_grad_norm * max_grad_norm)
                ? max_grad_norm / std::sqrt(sq_norm)
                : 1.0;
        }

        if (optimizer == MlpOptimizer::MomentumSgd) {
            const double momentum_factor = 0.9;
            std::size_t next = 0;
            for (DenseLayer& numeric_layer : layers_) {
                // Weights update with weight decay
                for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
                    // Regularized gradient: grad = raw_grad + lambda * weight
                    const double grad = clip_scale_ * gradients[next++] + weight_decay * numeric_layer.weights[i];

                    numeric_layer.weight_velocities[i] = momentum_factor * numeric_layer.weight_velocities[i]
                        + learning_rate * grad;
                    numeric_layer.weights[i] -= numeric_layer.weight_velocities[i];
                }
                // Biases update (no weight decay)
                for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
                    numeric_layer.bias_velocities[i] = momentum_factor * numeric_layer.bias_velocities[i]
                        + learning_rate * clip_scale_ * gradients[next++];
                    numeric_layer.biases[i] -= numeric_layer.bias_velocities[i];
                }
            }
        }
        else if (optimizer == MlpOptimizer::Adam) {
            constexpr double beta1 = 0.9;
            constexpr double beta2 = 0.999;
            constexpr double epsilon = 1e-8;

            // Increment per-update step counter (correct Adam bias correction)
            ++adam_step_;
            const double bias_correction1 = 1.0 - std::pow(beta1, adam_step_);
            const double bias_correction2 = 1.0 - std::pow(beta2, adam_step_);

            std::size_t next = 0;
            for (DenseLayer& numeric_layer : layers_) {
                // Adam weight updates with weight decay (AdamW style variant)
                for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
                    // 1. Clipped gradient (no weight decay term in moment estimates)
                    const double grad = clip_scale_ * gradients[next++];

                    // 2. Update Adam moments exactly as normal
                    numeric_layer.weight_velocities[i] = beta1 * numeric_layer.weight_velocities[i] + (1.0 - beta1) * grad;
                    numeric_layer.v_weights[i] = beta2 * numeric_layer.v_weights[i] + (1.0 - beta2) * grad * grad;

                    const double m_hat = numeric_layer.weight_velocities[i] / bias_correction1;
                    const double v_hat = numeric_layer.v_weights[i] / bias_correction2;

                    // 3. Apply the standard Adam update step
                    numeric_layer.weights[i] -= (learning_rate / (std::sqrt(v_hat) + epsilon)) * m_hat;

                    // 4. Decoupled weight decay (AdamW step):
                    // Directly shrink the weight proportional to the current learning rate
                    numeric_layer.weights[i] -= learning_rate * weight_decay * numeric_layer.weights[i];
                }

                // Adam bias updates (no weight decay)
                for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
                    const double grad = clip_scale_ * gradients[next++];

                    // Re-use bias_velocities vector array for Adam's first moment (m)
                    numeric_layer.bias_velocities[i] = beta1 * numeric_layer.bias_velocities[i] + (1.0 - beta1) * grad;
                    numeric_layer.v_biases[i] = beta2 * numeric_layer.v_biases[i] + (1.0 - beta2) * grad * grad;

                    const double m_hat = numeric_layer.bias_velocities[i] / bias_correction1;
                    const double v_hat = numeric_layer.v_biases[i] / bias_correction2;

                    numeric_layer.biases[i] -= (learning_rate / (std::sqrt(v_hat) + epsilon)) * m_hat;
                }
            }
        }
    }

    // Derivative of the activation at the pre-activation z. 'a' is the activation output, which
    // Tanh and Sigmoid can reuse instead of recomputing the transcendental.
    static double activation_derivative(MlpActivation activation, double z, double a) {
        switch (activation) {
        case MlpActivation::Linear:
            return 1.0;
        case MlpActivation::Tanh:
            return 1.0 - a * a;
        case MlpActivation::Sigmoid:
            return a * (1.0 - a);
        case MlpActivation::Swish: {
            const double s = apply_activation(MlpActivation::Sigmoid, z);
            return s + z * s * (1.0 - s);
        }
        case MlpActivation::Relu:
            // autodiff_max(x, 0.0) selects the constant branch at exactly 0, so the scalar path
            // reports a derivative of 0 there. Match that.
            return z > 0.0 ? 1.0 : 0.0;
        }
        return 1.0;
    }

    // W as the GEMM sees it: (output_size x input_size) row-major, stride == input_size.
    [[nodiscard]] static MatrixView<const double> weight_view(const DenseLayer& layer) noexcept {
        return MatrixView<const double>{ layer.weights.data(), layer.output_size, layer.input_size,
                                         layer.input_size };
    }

    void require_prepared(const MlpBatchWorkspace& workspace) const {
        if (!workspace.is_prepared() || workspace.pre_activations.size() != layers_.size()) {
            throw std::invalid_argument("MlpBatchWorkspace was not prepared for this network.");
        }
    }

    static void assert_shape(std::size_t actual, std::size_t expected, const char* name) {
        if (actual != expected) {
            throw std::invalid_argument(std::string(name) + " dimension mismatch.");
        }
    }

    // dynamic sizing based on the instantiated topology
    void initialize_reusable_buffers() {
        reusable_ad_layers_.resize(layers_.size());
        std::size_t max_layer_width = input_size();

        for (std::size_t i = 0; i < layers_.size(); ++i) {
            reusable_ad_layers_[i].weights.reserve(layers_[i].weights.size());
            reusable_ad_layers_[i].biases.reserve(layers_[i].biases.size());
            max_layer_width = std::max(max_layer_width, layers_[i].output_size);
        }

        // Ensure ping-pong buffers can fit the widest layer layer outputs without resizing
        activation_buffer_a_.reserve(max_layer_width);
        activation_buffer_b_.reserve(max_layer_width);

        std::size_t parameter_count = 0;
        for (const DenseLayer& layer : layers_) {
            parameter_count += layer.weights.size() + layer.biases.size();
        }
        flat_gradients_.assign(parameter_count, 0.0);
    }

    [[nodiscard]] std::size_t estimate_required_nodes(std::size_t input_count) const {
        std::size_t nodes = input_count + 1;
        std::size_t current_width = input_count;

        for (const DenseLayer& layer : layers_) {
            nodes += layer.weights.size();
            nodes += layer.biases.size();
            nodes += layer.output_size * (2 * current_width + 1);

            // Account for activation operator overhead (nodes beyond the pre-activation z)
            if (layer.activation == MlpActivation::Swish) {
                // Swish = x * sigmoid(x): sigmoid itself costs ~6 nodes, plus 1 Mul = 7 extra
                nodes += layer.output_size * 7;
            }
            else if (layer.activation == MlpActivation::Sigmoid) {
                // 1/(1+exp(-x)): Neg + Exp + Constant(1) + Add + Constant(1) + Div = 6 extra
                nodes += layer.output_size * 6;
            }
            else if (layer.activation != MlpActivation::Linear) {
                // Tanh: 1 op; ReLU: 1 op (autodiff_max adds a Constant + if_else path)
                nodes += layer.output_size * 2;
            }

            if (layer.dropout_rate > 0.0) {
                // Inverted dropout is implemented as a scalar multiply by either 0 or 1 / keep_prob.
                nodes += layer.output_size * 2;
            }

            current_width = layer.output_size;
        }

        nodes += 4 * output_size();
        return nodes;
    }

    static double apply_activation(MlpActivation activation, double x) {
        switch (activation) {
        case MlpActivation::Linear:
            return x;
        case MlpActivation::Tanh:
            return std::tanh(x);
        case MlpActivation::Sigmoid:
            return 1.0 / (1.0 + std::exp(-x));
        case MlpActivation::Swish:
            return x * apply_activation(MlpActivation::Sigmoid, x);
        case MlpActivation::Relu:
            return std::max(0.0, x);
        }
        return x;
    }

    // One Bernoulli draw for one unit, inverted dropout. Both the scalar and the batched path go
    // through here, so a batch of one consumes the random stream in exactly the same order.
    static double draw_dropout_scale(double dropout_rate, std::mt19937& rng) {
        const double keep_prob = 1.0 - dropout_rate;
        std::bernoulli_distribution keep_dist(keep_prob);
        return keep_dist(rng) ? (1.0 / keep_prob) : 0.0;
    }

    static Var<double> apply_dropout(Var<double> x, double dropout_rate, std::mt19937& rng) {
        if (dropout_rate <= 0.0) {
            return x;
        }

        return x * draw_dropout_scale(dropout_rate, rng);
    }

    static Var<double> apply_activation(MlpActivation activation, Var<double> x) {
        switch (activation) {
        case MlpActivation::Linear:
            return x;
        case MlpActivation::Tanh:
            return autodiff_tanh(x);
        case MlpActivation::Sigmoid:
            return 1.0 / (1.0 + autodiff_exp(-x));
        case MlpActivation::Swish:
            return autodiff_swish(x);
        case MlpActivation::Relu:
            return autodiff_max(x, 0.0);
        }
        return x;
    }

    static std::vector<double> forward_layer(const DenseLayer& layer, std::span<const double> input) {
        std::vector<double> output(layer.output_size, 0.0);

        for (std::size_t row = 0; row < layer.output_size; ++row) {
            double z = layer.biases[row];
            const std::size_t offset = row * layer.input_size;
            for (std::size_t col = 0; col < layer.input_size; ++col) {
                z += layer.weights[offset + col] * input[col];
            }
            output[row] = apply_activation(layer.activation, z);
        }

        return output;
    }

    // Inplace forward graph execution
    static void forward_layer_inplace(const DenseLayer& config, const ReusableLayerVars& layer,
        std::span<const Var<double>> input, std::vector<Var<double>>& output, std::mt19937& dropout_rng) {

        for (std::size_t row = 0; row < config.output_size; ++row) {
            Var<double> z = layer.biases[row];
            const std::size_t offset = row * config.input_size;
            for (std::size_t col = 0; col < config.input_size; ++col) {
                z += layer.weights[offset + col] * input[col];
            }
            Var<double> a = apply_activation(config.activation, z);
            output.push_back(apply_dropout(a, config.dropout_rate, dropout_rng));
        }
    }

    static double mse_loss(std::span<const double> prediction, std::span<const double> target) {
        assert(prediction.size() == target.size());
        if (prediction.empty()) {
            return 0.0;
        }

        double total = 0.0;
        for (std::size_t i = 0; i < prediction.size(); ++i) {
            const double diff = prediction[i] - target[i];
            total += diff * diff;
        }
        return total / static_cast<double>(prediction.size());
    }
};
