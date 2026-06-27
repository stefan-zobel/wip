#pragma once

#include <array>
#include <cmath>
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

#include "SimpleMlp.h"

struct SimpleMlpTestContext {
    int failures = 0;

    void check_true(std::string_view name, bool condition) {
        std::cout << (condition ? "[PASS] " : "[FAIL] ") << name << "\n";
        if (!condition) {
            ++failures;
        }
    }

    void check_near(std::string_view name, double actual, double expected, double tol = 1e-6) {
        const bool ok = std::abs(actual - expected) <= tol;
        std::cout << (ok ? "[PASS] " : "[FAIL] ") << name
                  << "  actual=" << actual << "  expected=" << expected << "\n";
        if (!ok) {
            ++failures;
        }
    }
};

inline int run_simple_mlp_tests() {
    SimpleMlpTestContext ctx{};

    std::cout << "\n=== Simple MLP tests ===\n";

    {
        const std::array<std::size_t, 3> sizes{ 2, 4, 1 };
        const std::array<MlpActivation, 2> activations{ MlpActivation::Tanh, MlpActivation::Tanh };
        SimpleMlp mlp(sizes, activations, 7u);

        Tape<double> training_tape;

        const std::vector<MlpSample> xor_samples{
            { { -1.0, -1.0 }, { -1.0 } },
            { { -1.0,  1.0 }, {  1.0 } },
            { {  1.0, -1.0 }, {  1.0 } },
            { {  1.0,  1.0 }, { -1.0 } } }
        ;

        const double initial_loss = mlp.dataset_loss(xor_samples);
        double epoch_loss = initial_loss;

        for (int epoch = 0; epoch < 4000; ++epoch) {
            epoch_loss = mlp.train_epoch(xor_samples, 0.1, training_tape, epoch, MlpOptimizer::MomentumSgd);
        }

        const double final_loss = mlp.dataset_loss(xor_samples);
        ctx.check_true("mlp xor loss decreases", final_loss < initial_loss * 0.2);
        ctx.check_true("mlp xor epoch loss small", epoch_loss < 0.05);

        for (std::size_t i = 0; i < xor_samples.size(); ++i) {
            const auto prediction = mlp.predict(xor_samples[i].input);
            const double predicted_sign = prediction[0] >= 0.0 ? 1.0 : -1.0;
            ctx.check_near(std::string("mlp xor sample ") + std::to_string(i), predicted_sign, xor_samples[i].target[0], 1e-12);
        }
    }

    {
        const std::array<std::size_t, 2> sizes{ 1, 1 };
        const std::array<MlpActivation, 1> activations{ MlpActivation::Linear };
        SimpleMlp mlp(sizes, activations, 13u);

        Tape<double> training_tape;

        const std::vector<MlpSample> regression_samples{
            { { -1.0 }, { -1.0 } },
            { {  0.0 }, {  1.0 } },
            { {  1.0 }, {  3.0 } } }
        ;

        for (int epoch = 0; epoch < 400; ++epoch) {
            mlp.train_epoch(regression_samples, 0.05, training_tape, epoch, MlpOptimizer::MomentumSgd);
        }

        const auto pred_a = mlp.predict(std::array{ -0.5 });
        const auto pred_b = mlp.predict(std::array{  2.0 });
        ctx.check_near("mlp regression prediction -0.5", pred_a[0], 0.0, 1e-2);
        ctx.check_near("mlp regression prediction 2.0", pred_b[0], 5.0, 1e-2);
    }

    return ctx.failures;
}
