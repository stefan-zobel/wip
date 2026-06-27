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

struct MlpSample {
    std::vector<double> input{};
    std::vector<double> target{};
};

struct DenseLayer {
    std::size_t input_size{};
    std::size_t output_size{};
    MlpActivation activation{ MlpActivation::Tanh };
    std::vector<double> weights{};
    std::vector<double> biases{};

    // for momentum SGD
    std::vector<double> weight_velocities;
    std::vector<double> bias_velocities;

    DenseLayer() = default;

    DenseLayer(std::size_t in, std::size_t out, MlpActivation act)
        : input_size(in), output_size(out), activation(act),
          weights(in * out, 0.0), biases(out, 0.0),
          weight_velocities(in* out, 0.0), bias_velocities(out, 0.0) {
    }

    void initialize(std::mt19937& rng) {
        const double bound = std::sqrt(6.0 / static_cast<double>(input_size + output_size));
        std::uniform_real_distribution<double> dist(-bound, bound);

        for (double& weight : weights) {
            weight = dist(rng);
        }

        std::fill(biases.begin(), biases.end(), 0.0);
    }
};

class SimpleMlp {
    /*
    struct LayerVars {
        std::size_t input_size{};
        std::size_t output_size{};
        MlpActivation activation{ MlpActivation::Tanh };
        std::vector<Var<double>> weights{};
        std::vector<Var<double>> biases{};
    };
    */
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

    void reset(std::span<const std::size_t> layer_sizes,
               std::span<const MlpActivation> activations,
               std::uint32_t seed = 20260626u) {
        if (layer_sizes.size() < 2) {
            throw std::invalid_argument("SimpleMlp requires at least input and output sizes.");
        }
        if (activations.size() != layer_sizes.size() - 1) {
            throw std::invalid_argument("SimpleMlp needs one activation per dense layer.");
        }

        std::mt19937 rng(seed);
        layers_.clear();
        layers_.reserve(layer_sizes.size() - 1);

        for (std::size_t i = 0; i + 1 < layer_sizes.size(); ++i) {
            layers_.emplace_back(layer_sizes[i], layer_sizes[i + 1], activations[i]);
            layers_.back().initialize(rng);
        }

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

    /*
    double train_step(std::span<const double> input, std::span<const double> target, double learning_rate, Tape<double>& tape) {
        assert_shape(input.size(), input_size(), "input");
        assert_shape(target.size(), output_size(), "target");

        tape.reset();
        tape.reserve(estimate_required_nodes(input.size()));

        std::vector<Var<double>> activations;
        activations.reserve(input.size());
        for (double value : input) {
            activations.push_back(tape.constant(value));
        }

        std::vector<LayerVars> trainable_layers;
        trainable_layers.reserve(layers_.size());

        for (const DenseLayer& layer : layers_) {
            LayerVars vars{};
            vars.input_size = layer.input_size;
            vars.output_size = layer.output_size;
            vars.activation = layer.activation;
            vars.weights.reserve(layer.weights.size());
            vars.biases.reserve(layer.biases.size());

            for (double weight : layer.weights) {
                vars.weights.push_back(tape.input(weight));
            }
            for (double bias : layer.biases) {
                vars.biases.push_back(tape.input(bias));
            }

            activations = forward_layer(vars, activations);
            trainable_layers.push_back(std::move(vars));
        }

        Var<double> loss = tape.constant(0.0);
        for (std::size_t i = 0; i < activations.size(); ++i) {
            const Var<double> diff = activations[i] - target[i];
            loss += 0.5 * diff * diff;
        }

        tape.backward(loss);

        const double momentum_factor = 0.9;

        for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
            DenseLayer& numeric_layer = layers_[layer_idx];
            const LayerVars& ad_layer = trainable_layers[layer_idx];

            for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
//                standard SGD (no momentum)
//                numeric_layer.weights[i] -= learning_rate * ad_layer.weights[i].gradient();

                // momentum SGD
                // v = alpha * v + lr * grad
                numeric_layer.weight_velocities[i] =
                    momentum_factor * numeric_layer.weight_velocities[i]
                        + learning_rate * ad_layer.weights[i].gradient();

                // w = w - v
                numeric_layer.weights[i] -= numeric_layer.weight_velocities[i];
            }
            for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
//                standard SGD (no momentum)
//                numeric_layer.biases[i] -= learning_rate * ad_layer.biases[i].gradient();

                // momentum SGD
                // v = alpha * v + lr * grad
                numeric_layer.bias_velocities[i] =
                    momentum_factor * numeric_layer.bias_velocities[i]
                        + learning_rate * ad_layer.biases[i].gradient();

                // b = b - v
                numeric_layer.biases[i] -= numeric_layer.bias_velocities[i];
            }
        }

        return loss.value();
    }
    */

    // Execution with 0 allocations
    double train_step(std::span<const double> input, std::span<const double> target, double learning_rate, Tape<double>& tape) {
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
            forward_layer_inplace(layer, ad_layer, current_input, current_output);
        }

        // 4. Trace graph down to loss evaluation
        auto& final_activations = (layers_.size() % 2 == 0) ? activation_buffer_a_ : activation_buffer_b_;

        Var<double> loss = tape.constant(0.0);
        for (std::size_t i = 0; i < final_activations.size(); ++i) {
            const Var<double> diff = final_activations[i] - target[i];
            loss += 0.5 * diff * diff;
        }

        tape.backward(loss);

        // 5. Apply weights using Momentum SGD
        const double momentum_factor = 0.9;
        for (std::size_t layer_idx = 0; layer_idx < layers_.size(); ++layer_idx) {
            DenseLayer& numeric_layer = layers_[layer_idx];
            const ReusableLayerVars& ad_layer = reusable_ad_layers_[layer_idx];

            for (std::size_t i = 0; i < numeric_layer.weights.size(); ++i) {
                numeric_layer.weight_velocities[i] =
                    momentum_factor * numeric_layer.weight_velocities[i]
                    + learning_rate * ad_layer.weights[i].gradient();
                numeric_layer.weights[i] -= numeric_layer.weight_velocities[i];
            }
            for (std::size_t i = 0; i < numeric_layer.biases.size(); ++i) {
                numeric_layer.bias_velocities[i] =
                    momentum_factor * numeric_layer.bias_velocities[i]
                    + learning_rate * ad_layer.biases[i].gradient();
                numeric_layer.biases[i] -= numeric_layer.bias_velocities[i];
            }
        }

        return loss.value();
    }

    double train_epoch(std::span<const MlpSample> samples, double learning_rate, Tape<double>& tape) {
        if (samples.empty()) {
            return 0.0;
        }

        double total = 0.0;
        for (const MlpSample& sample : samples) {
            total += train_step(sample.input, sample.target, learning_rate, tape);
        }
        return total / static_cast<double>(samples.size());
    }

private:
    std::vector<DenseLayer> layers_{};

    // Class-level pre-allocated structures
    std::vector<ReusableLayerVars> reusable_ad_layers_{};
    std::vector<Var<double>> activation_buffer_a_{};
    std::vector<Var<double>> activation_buffer_b_{};

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

            // Account for Swish / Sigmoid / Tanh operator overhead
            if (layer.activation == MlpActivation::Swish) {
                nodes += layer.output_size * 5;
            }
            else if (layer.activation != MlpActivation::Linear) {
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

    /*
    static std::vector<Var<double>> forward_layer(const LayerVars& layer, std::span<const Var<double>> input) {
        std::vector<Var<double>> output;
        output.reserve(layer.output_size);

        for (std::size_t row = 0; row < layer.output_size; ++row) {
            Var<double> z = layer.biases[row];
            const std::size_t offset = row * layer.input_size;
            for (std::size_t col = 0; col < layer.input_size; ++col) {
                z += layer.weights[offset + col] * input[col];
            }
            output.push_back(apply_activation(layer.activation, z));
        }

        return output;
    }
    */

    // Inplace forward graph execution
    static void forward_layer_inplace(const DenseLayer& config, const ReusableLayerVars& layer,
        std::span<const Var<double>> input, std::vector<Var<double>>& output) {

        for (std::size_t row = 0; row < config.output_size; ++row) {
            Var<double> z = layer.biases[row];
            const std::size_t offset = row * config.input_size;
            for (std::size_t col = 0; col < config.input_size; ++col) {
                z += layer.weights[offset + col] * input[col];
            }
            output.push_back(apply_activation(config.activation, z));
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
            total += 0.5 * diff * diff;
        }
        return total;
    }
};
