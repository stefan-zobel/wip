#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <random>
#include <vector>

#include "SimpleMlp.h"

namespace simple_mlp_demo_detail {
    inline constexpr double pi = 3.14159265358979323846264338327950288;

    inline double target_function(double x, double y) {
        return std::sin(pi * x) * std::cos(0.5 * pi * y) + 0.3 * x * y;
    }

    inline std::vector<MlpSample> make_regression_grid(int resolution) {
        std::vector<MlpSample> samples;
        samples.reserve(static_cast<std::size_t>(resolution * resolution));

        const double denom = static_cast<double>(resolution - 1);
        for (int iy = 0; iy < resolution; ++iy) {
            for (int ix = 0; ix < resolution; ++ix) {
                const double x = -1.0 + 2.0 * static_cast<double>(ix) / denom;
                const double y = -1.0 + 2.0 * static_cast<double>(iy) / denom;
                samples.push_back(MlpSample{
                    .input = { x, y },
                    .target = { target_function(x, y) }
                });
            }
        }

        return samples;
    }

    inline double rmse(const SimpleMlp& mlp, const std::vector<MlpSample>& samples) {
        if (samples.empty()) {
            return 0.0;
        }

        double squared_error_sum = 0.0;
        for (const MlpSample& sample : samples) {
            const auto prediction = mlp.predict(sample.input);
            const double diff = prediction[0] - sample.target[0];
            squared_error_sum += diff * diff;
        }

        return std::sqrt(squared_error_sum / static_cast<double>(samples.size()));
    }
}

inline void run_simple_mlp_demo() {
    using namespace simple_mlp_demo_detail;

    std::cout << "\n=== Simple MLP demo: 2D nonlinear regression ===\n";
    std::cout << "Target: f(x,y) = sin(pi*x) * cos(0.5*pi*y) + 0.3*x*y\n";

    const std::array<std::size_t, 4> sizes{ 2, 32, 32, 1 };
    const std::array<MlpActivation, 3> activations{
        MlpActivation::Swish,
        MlpActivation::Swish,
        MlpActivation::Linear
    };

    SimpleMlp mlp(sizes, activations, 20260626u);
    SimpleMlp best_mlp = mlp;

    std::vector<MlpSample> training_samples = make_regression_grid(32);
    const std::vector<MlpSample> validation_samples = make_regression_grid(33);

    std::mt19937 rng(20260626u);

    const double initial_train_loss = mlp.dataset_loss(training_samples);
    const double initial_valid_rmse = rmse(mlp, validation_samples);

    std::cout << std::fixed << std::setprecision(6);
    std::cout << "Initial train loss: " << initial_train_loss << "\n";
    std::cout << "Initial validation RMSE: " << initial_valid_rmse << "\n";

    constexpr int epochs = 3000;

    // Instantiate global tape for this thread
    Tape<double> training_tape;

    constexpr double weight_decay = 0.0;
    double lr_max = 0.03;   // max learning rate
    double lr_min = 0.0001; // min learning rate
    double max_epochs = epochs;

    double min_valid_rmse = initial_valid_rmse;
    int min_epoch = 0;

    for (int epoch = 1; epoch <= epochs; ++epoch) {
        std::shuffle(training_samples.begin(), training_samples.end(), rng);

        // learning rate cosine decay
        double learning_rate = lr_min + 0.5 * (lr_max - lr_min) * (1.0 + std::cos(pi * epoch / max_epochs));

        const double epoch_loss = mlp.train_epoch(training_samples, learning_rate, training_tape, MlpOptimizer::MomentumSgd, weight_decay);

        if (epoch == 1 || epoch % 20 == 0 || epoch == epochs) {
            const double valid_rmse = rmse(mlp, validation_samples);
            if (valid_rmse < min_valid_rmse) {
                min_valid_rmse = valid_rmse;
                min_epoch = epoch;
                best_mlp = mlp;
            }
            std::cout << "Epoch " << std::setw(4) << epoch
                      << "  lr=" << std::fixed << std::setprecision(5) << learning_rate
                      << "  train_loss=" << std::scientific << epoch_loss << std::fixed
                      << "  valid_rmse=" << valid_rmse
                      << "  min_valid_rmse=" << min_valid_rmse
                      << "  min_epoch=" << min_epoch << "\n";;
        }
    }

    const std::array<std::array<double, 2>, 6> probe_points{{
        { -0.90, -0.60 },
        { -0.35,  0.80 },
        {  0.00,  0.00 },
        {  0.45, -0.25 },
        {  0.70,  0.55 },
        {  0.95, -0.90 }
    }};

    auto print_predictions = [&](const char* title, const SimpleMlp& model) {
        std::cout << "\n" << title << "\n";
        for (const auto& point : probe_points) {
            const auto prediction = model.predict(point);
            const double expected = target_function(point[0], point[1]);
            std::cout << "x=" << std::setw(6) << point[0]
                      << "  y=" << std::setw(6) << point[1]
                      << "  pred=" << std::setw(10) << prediction[0]
                      << "  target=" << std::setw(10) << expected
                      << "  abs_err=" << std::setw(10) << std::abs(prediction[0] - expected)
                      << "\n";
        }
    };

    auto print_summary = [&](const char* title, const SimpleMlp& model, int epoch_tag) {
        const double train_loss = model.dataset_loss(training_samples);
        const double valid_loss = rmse(model, validation_samples);
        std::cout << "\n" << title << "\n"
                  << "  epoch:            " << epoch_tag << "\n"
                  << "  train_mse:        " << std::scientific << train_loss << "\n"
                  << "  validation_rmse:  " << std::fixed << std::setprecision(6) << valid_loss << "\n";
    };

    print_predictions("Sample predictions (last model):", mlp);
    print_predictions("Sample predictions (best validation model):", best_mlp);

//    Sample predictions (24x24, 13, 25):
//    x = -0.90000  y = -0.60000  pred = -0.02714  target = -0.01964  abs_err = 0.00751
//    x = -0.35000  y = 0.80000  pred = -0.36299  target = -0.35934  abs_err = 0.00365
//    x = 0.00000  y = 0.00000  pred = 0.00742  target = 0.00000  abs_err = 0.00742
//    x = 0.45000  y = -0.25000  pred = 0.88152  target = 0.87876  abs_err = 0.00277
//    x = 0.70000  y = 0.55000  pred = 0.64137  target = 0.64091  abs_err = 0.00045
//    x = 0.95000  y = -0.90000  pred = -0.22930  target = -0.23203  abs_err = 0.00272

//    Sample predictions (48x48, 32, 33):
//    x = -0.90000  y = -0.60000  pred = -0.02084  target = -0.01964  abs_err = 0.00120
//    x = -0.35000  y = 0.80000  pred = -0.35713  target = -0.35934  abs_err = 0.00220
//    x = 0.00000  y = 0.00000  pred = 0.00049  target = 0.00000  abs_err = 0.00049
//    x = 0.45000  y = -0.25000  pred = 0.88050  target = 0.87876  abs_err = 0.00174
//    x = 0.70000  y = 0.55000  pred = 0.63954  target = 0.64091  abs_err = 0.00137
//    x = 0.95000  y = -0.90000  pred = -0.23099  target = -0.23203  abs_err = 0.00103

//    Sample predictions (48x48, 32, 33, He-Initialization, gradient clipping):
//    x = -0.90000  y = -0.60000  pred = -0.01967  target = -0.01964  abs_err = 0.00004
//    x = -0.35000  y = 0.80000  pred = -0.35750  target = -0.35934  abs_err = 0.00183
//    x = 0.00000  y = 0.00000  pred = 0.00091  target = 0.00000  abs_err = 0.00091
//    x = 0.45000  y = -0.25000  pred = 0.87959  target = 0.87876  abs_err = 0.00083
//    x = 0.70000  y = 0.55000  pred = 0.64050  target = 0.64091  abs_err = 0.00042
//    x = 0.95000  y = -0.90000  pred = -0.23323  target = -0.23203  abs_err = 0.00120

    print_summary("Summary (last model):", mlp, epochs);
    print_summary("Summary (best validation model):", best_mlp, min_epoch);
}
