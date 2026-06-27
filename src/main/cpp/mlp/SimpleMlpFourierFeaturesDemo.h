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

namespace simple_mlp_fourier_demo_detail {
    inline constexpr double pi = 3.14159265358979323846264338327950288;

    inline double target_function(double x, double y) {
        return std::sin(pi * x) * std::cos(0.5 * pi * y) + 0.3 * x * y;
    }

    // Instead of: mlp.forward({x, y}) -> Input size 2
    // Try a Fourier Mapping -> Input size 8 (2 raw + 3 sines + 3 cosines)
    std::vector<double> fourier_features(double x, double y) {
        std::vector<double> features;

        // 1. Keep the raw coordinates (helps with the 0.3xy term)
        features.push_back(x);
        features.push_back(y);

        // 2. Add different frequency scales (B_i)
        std::vector<double> frequencies = { 0.5, 1.0, 2.0 };

        for (double b : frequencies) {
            // Map x
            features.push_back(std::sin(b * pi * x));
            features.push_back(std::cos(b * pi * x));

            // Map y
            features.push_back(std::sin(b * pi * y));
            features.push_back(std::cos(b * pi * y));
        }

        return features;
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
                    .input  =   fourier_features(x, y),
                    .target = { target_function(x, y) }
                });
            }
        }

        return samples;
    }

    inline double rmse(SimpleMlp& mlp, const std::vector<MlpSample>& samples) {
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

inline void run_simple_mlp_fourier_demo() {
    using namespace simple_mlp_fourier_demo_detail;

    std::cout << "\n=== Simple MLP demo: 2D nonlinear regression ===\n";
    std::cout << "Target: f(x,y) = sin(pi*x) * cos(0.5*pi*y) + 0.3*x*y\n";

    const std::array<std::size_t, 4> sizes{ 14, 24, 24, 1 };
    const std::array<MlpActivation, 3> activations{
        MlpActivation::Swish,
        MlpActivation::Swish,
        MlpActivation::Linear
    };

    SimpleMlp mlp(sizes, activations, 20260626u);

    std::vector<MlpSample> training_samples = make_regression_grid(25);
    const std::vector<MlpSample> validation_samples = make_regression_grid(26);

    std::mt19937 rng(20260626u);

    const double initial_train_loss = mlp.dataset_loss(training_samples);
    const double initial_valid_rmse = rmse(mlp, validation_samples);

    std::cout << std::fixed << std::setprecision(6);
    std::cout << "Initial train loss: " << initial_train_loss << "\n";
    std::cout << "Initial validation RMSE: " << initial_valid_rmse << "\n";

    constexpr int epochs = 3000;

    // Instantiate global tape for this thread
    Tape<double> training_tape;

    double lr_max = 0.03;   // max learning rate
    double lr_min = 0.0001; // min learning rate
    double max_epochs = epochs;

    double min_valid_rmse = std::numeric_limits<double>::max();
    int min_epoch = std::numeric_limits<int>::max();

    for (int epoch = 1; epoch <= epochs; ++epoch) {
        std::shuffle(training_samples.begin(), training_samples.end(), rng);

        // learning rate cosine decay
        double learning_rate = lr_min + 0.5 * (lr_max - lr_min) * (1.0 + std::cos(pi * epoch / max_epochs));

        const double epoch_loss = mlp.train_epoch(training_samples, learning_rate, training_tape);

        if (epoch == 1 || epoch % 20 == 0 || epoch == epochs) {
            const double valid_rmse = rmse(mlp, validation_samples);
            if (valid_rmse < min_valid_rmse) {
                min_valid_rmse = valid_rmse;
                min_epoch = epoch;
            }
            std::cout << "Epoch " << std::setw(4) << epoch
                      << "  lr=" << std::fixed << std::setprecision(5) << learning_rate
                      << "  train_loss=" << epoch_loss
                      << "  valid_rmse=" << valid_rmse
                      << "  min_valid_rmse=" << min_valid_rmse
                      << "  min_epoch=" << min_epoch << "\n";;
        }
    }

    std::cout << "\nSample predictions:\n";
    const std::array<std::array<double, 2>, 6> probe_points{{
        { -0.90, -0.60 },
        { -0.35,  0.80 },
        {  0.00,  0.00 },
        {  0.45, -0.25 },
        {  0.70,  0.55 },
        {  0.95, -0.90 }
    }};

    for (const auto& point : probe_points) {
        const auto prediction = mlp.predict(fourier_features(point[0], point[1]));
        const double expected = target_function(point[0], point[1]);
        std::cout << "x=" << std::setw(6) << point[0]
                  << "  y=" << std::setw(6) << point[1]
                  << "  pred=" << std::setw(10) << prediction[0]
                  << "  target=" << std::setw(10) << expected
                  << "  abs_err=" << std::setw(10) << std::abs(prediction[0] - expected)
                  << "\n";
    }

//   Sample predictions :
//   x = -0.90000  y = -0.60000  pred = -0.01842  target = -0.01964  abs_err = 0.00122
//   x = -0.35000  y = 0.80000  pred = -0.35993  target = -0.35934  abs_err = 0.00060
//   x = 0.00000  y = 0.00000  pred = 0.00090  target = 0.00000  abs_err = 0.00090
//   x = 0.45000  y = -0.25000  pred = 0.87838  target = 0.87876  abs_err = 0.00038
//   x = 0.70000  y = 0.55000  pred = 0.64144  target = 0.64091  abs_err = 0.00052
//   x = 0.95000  y = -0.90000  pred = -0.23265  target = -0.23203  abs_err = 0.00062

    const double final_train_loss = mlp.dataset_loss(training_samples);
    const double final_valid_rmse = rmse(mlp, validation_samples);
    std::cout << "\nFinal train loss: " << final_train_loss << "\n";
    std::cout << "Final validation RMSE: " << final_valid_rmse << "\n";
}
