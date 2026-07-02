#pragma once

#include <algorithm>
#include <cassert>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <random>
#include <span>
#include <string>
#include <stdexcept>
#include <vector>

#include "reverse_mode.h"

enum class MlpActivation {
    Linear,
    Tanh,
    Sigmoid,
    Swish,
    Relu
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

    // for momentum SGD (or Adam's first moment 'm')
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

    void initialize(std::mt19937& rng, MlpActivation activation) {
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
            layers_.back().initialize(rng, activations[i]);
        }

        // Reset Adam step counter whenever the network is rebuilt
        adam_step_ = 0;

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

    // Execution with 0 allocations supporting dual optimizers
    double train_step(std::span<const double> input,
        std::span<const double> target,
        double learning_rate,
        Tape<double>& tape,
        MlpOptimizer optimizer,
        double weight_decay) {
        assert_shape(input.size(), input_size(), "input");
        assert_shape(target.size(), output_size(), "target");

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

        // 5a. Gradient clipping: rescale all parameter gradients so their global L2 norm <= max_grad_norm
        constexpr double max_grad_norm = 5.0;
        {
            double sq_norm = 0.0;
            for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
                const ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];
                for (const Var<double>& w : ad_layer.weights) {
                    const double g = w.gradient();
                    sq_norm += g * g;
                }
                for (const Var<double>& b : ad_layer.biases) {
                    const double g = b.gradient();
                    sq_norm += g * g;
                }
            }
            clip_scale_ = (sq_norm > max_grad_norm * max_grad_norm)
                ? max_grad_norm / std::sqrt(sq_norm)
                : 1.0;
        }

        // 5b. Select Optimization Path (Parameter updates with inline L2 Regularization)
        if (optimizer == MlpOptimizer::MomentumSgd) {
            const double momentum_factor = 0.9;
            for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
                DenseLayer& numeric_layer = layers_[layer_idx];
                const ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];

                // Weights update with weight Decay
                for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
                    // Regularized Gradient: grad = raw_grad + lambda * weight
                    const double grad = clip_scale_ * ad_layer.weights[i].gradient() + weight_decay * numeric_layer.weights[i];

                    numeric_layer.weight_velocities[i] = momentum_factor * numeric_layer.weight_velocities[i]
                        + learning_rate * grad;
                    numeric_layer.weights[i] -= numeric_layer.weight_velocities[i];
                }
                // Biases update (no weight decay)
                for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
                    numeric_layer.bias_velocities[i] = momentum_factor * numeric_layer.bias_velocities[i]
                        + learning_rate * clip_scale_ * ad_layer.biases[i].gradient();
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

            for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
                DenseLayer& numeric_layer = layers_[layer_idx];
                const ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];

                // Adam Weight Updates with weight Decay (AdamW style variant)
                for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
                    // 1. Clipped gradient (no weight decay term in moment estimates)
                    const double grad = clip_scale_ * ad_layer.weights[i].gradient();

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

                // Adam Bias Updates (no weight decay)
                for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
                    const double grad = clip_scale_ * ad_layer.biases[i].gradient();

                    // Re-use bias_velocities vector array for Adam's first moment (m)
                    numeric_layer.bias_velocities[i] = beta1 * numeric_layer.bias_velocities[i] + (1.0 - beta1) * grad;
                    numeric_layer.v_biases[i] = beta2 * numeric_layer.v_biases[i] + (1.0 - beta2) * grad * grad;

                    const double m_hat = numeric_layer.bias_velocities[i] / bias_correction1;
                    const double v_hat = numeric_layer.v_biases[i] / bias_correction2;

                    numeric_layer.biases[i] -= (learning_rate / (std::sqrt(v_hat) + epsilon)) * m_hat;
                }
            }
        }

        return loss.value();
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
    // Gradient clipping scale factor, recomputed each train_step before parameter updates
    double clip_scale_ = 1.0;

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

    static Var<double> apply_dropout(Var<double> x, double dropout_rate, std::mt19937& rng) {
        if (dropout_rate <= 0.0) {
            return x;
        }

        const double keep_prob = 1.0 - dropout_rate;
        std::bernoulli_distribution keep_dist(keep_prob);
        const double scale = keep_dist(rng) ? (1.0 / keep_prob) : 0.0;
        return x * scale;
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
