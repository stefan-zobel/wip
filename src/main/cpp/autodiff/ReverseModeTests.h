#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <iostream>
#include <random>
#include <string>
#include <string_view>

#include "reverse_mode.h"

struct ReverseModeTestContext {
    int failures = 0;

    void check_near(std::string_view name, double actual, double expected, double tol = 1e-10) {
        const bool ok = std::abs(actual - expected) <= tol;
        std::cout << (ok ? "[PASS] " : "[FAIL] ") << name
                  << "  actual=" << actual << "  expected=" << expected << "\n";
        if (!ok) ++failures;
    }

    void check_array2(std::string_view name, const std::array<double, 2>& actual,
                      const std::array<double, 2>& expected, double tol = 1e-10) {
        check_near(std::string(name) + "[0]", actual[0], expected[0], tol);
        check_near(std::string(name) + "[1]", actual[1], expected[1], tol);
    }

    void check_matrix2x2(std::string_view name, const std::array<std::array<double, 2>, 2>& actual,
                         const std::array<std::array<double, 2>, 2>& expected, double tol = 1e-10) {
        check_near(std::string(name) + "[0][0]", actual[0][0], expected[0][0], tol);
        check_near(std::string(name) + "[0][1]", actual[0][1], expected[0][1], tol);
        check_near(std::string(name) + "[1][0]", actual[1][0], expected[1][0], tol);
        check_near(std::string(name) + "[1][1]", actual[1][1], expected[1][1], tol);
    }
};

inline int run_reverse_mode_tests() {
    ReverseModeTestContext ctx{};

    auto finite_difference_gradient = [](const auto& func, std::array<double, 2> x) {
        std::array<double, 2> grad{};
        for (std::size_t i = 0; i < 2; ++i) {
            const double h = 1e-6 * std::max(1.0, std::abs(x[i]));
            auto xp = x;
            auto xm = x;
            xp[i] += h;
            xm[i] -= h;
            grad[i] = (func(xp) - func(xm)) / (2.0 * h);
        }
        return grad;
    };

    auto finite_difference_jacobian = [](const auto& func, std::array<double, 2> x) {
        std::array<std::array<double, 2>, 2> J{};
        for (std::size_t i = 0; i < 2; ++i) {
            const double h = 1e-6 * std::max(1.0, std::abs(x[i]));
            auto xp = x;
            auto xm = x;
            xp[i] += h;
            xm[i] -= h;
            const auto yp = func(xp);
            const auto ym = func(xm);
            for (std::size_t k = 0; k < 2; ++k) {
                J[i][k] = (yp[k] - ym[k]) / (2.0 * h);
            }
        }
        return J;
    };

    auto reverse_demo = []<typename Scalar>(std::array<Var<Scalar>, 2> xy) -> Var<Scalar> {
        return xy[0] * xy[0] * xy[1] + autodiff_exp(xy[1]);
    };

    auto reverse_vector_demo = []<typename Scalar>(std::array<Var<Scalar>, 2> xy) -> std::array<Var<Scalar>, 2> {
        return { xy[0] * xy[1], autodiff_sin(xy[0]) + xy[1] };
    };

    auto reverse_random_scalar = []<typename Scalar>(std::array<Var<Scalar>, 2> xy) -> Var<Scalar> {
        return autodiff_sin(xy[0]) * autodiff_exp(xy[1])
             + xy[0] * xy[0] * xy[1]
             + autodiff_log(xy[0] + 2.5)
             + autodiff_sqrt(xy[1] + 3.0);
    };

    auto scalar_reference = [](const std::array<double, 2>& xy) {
        return std::sin(xy[0]) * std::exp(xy[1])
             + xy[0] * xy[0] * xy[1]
             + std::log(xy[0] + 2.5)
             + std::sqrt(xy[1] + 3.0);
    };

    auto vector_reference = [](const std::array<double, 2>& xy) {
        return std::array<double, 2>{
            xy[0] * xy[1] + std::sin(xy[0]),
            std::exp(xy[1]) + xy[0] / (xy[1] + 3.0)
        };
    };

    auto reverse_random_vector = []<typename Scalar>(std::array<Var<Scalar>, 2> xy) -> std::array<Var<Scalar>, 2> {
        return {
            xy[0] * xy[1] + autodiff_sin(xy[0]),
            autodiff_exp(xy[1]) + xy[0] / (xy[1] + 3.0)
        };
    };

    std::cout << "\n=== Reverse-mode AD tests ===\n";

    Tape<> tape;

    {
        tape.reset();
        Var x = tape.input(3.0);
        Var y = tape.input(5.0);
        Var result = x * y + x;
        tape.backward(result);
        ctx.check_near("reverse scalar value", result.value(), 18.0);
        ctx.check_near("reverse scalar d/dx", x.gradient(), 6.0);
        ctx.check_near("reverse scalar d/dy", y.gradient(), 3.0);
    }

    {
        tape.reset();
        Var x = tape.input(2.0);
        Var y = tape.input(1.0);
        Var result = x * x + autodiff_sin(x) + autodiff_exp(y);
        tape.backward(result);
        ctx.check_near("reverse nonlinear d/dx", x.gradient(), 4.0 + std::cos(2.0));
        ctx.check_near("reverse nonlinear d/dy", y.gradient(), std::exp(1.0));
    }

    {
        tape.reset();
        Var x = tape.input(5.0);
        Var result = 3.0 * x - 1.0;
        tape.backward(result);
        ctx.check_near("reverse scalar overload value", result.value(), 14.0);
        ctx.check_near("reverse scalar overload grad", x.gradient(), 3.0);
    }

    {
        tape.reset();
        Var x = tape.input(2.0);
        Var r = x * x + autodiff_sqrt(x);
        tape.backward(r);
        ctx.check_near("reverse sqrt grad", x.gradient(), 4.0 + 0.5 / std::sqrt(2.0));
    }

    {
        tape.reset();
        Var x = tape.input(4.0);
        auto p = tape.parameter(3.0);
        Var result = p * x + 1.0;
        tape.backward(result);
        ctx.check_near("parameter value", result.value(), 13.0);
        ctx.check_near("parameter grad", x.gradient(), 3.0);
    }

    {
        tape.reset();
        Var x = tape.input(2.0);
        Var y = tape.input(7.0);
        Var loss = x * y;
        Var unused = autodiff_exp(x) + y;
        (void)unused;
        tape.backward(loss);
        ctx.check_near("non-terminal loss value", loss.value(), 14.0);
        ctx.check_near("non-terminal loss d/dx", x.gradient(), 7.0);
        ctx.check_near("non-terminal loss d/dy", y.gradient(), 2.0);
    }

    {
        const auto grad_rev = reverse_gradient<2>(reverse_demo, std::array{ 3.0, 1.0 });
        const auto H = reverse_hessian<2>(reverse_demo, std::array{ 3.0, 1.0 });
        ctx.check_array2("reverse gradient helper", grad_rev, std::array<double, 2>{ 6.0, 9.0 + std::exp(1.0) });
        ctx.check_matrix2x2("reverse hessian", H,
            std::array<std::array<double, 2>, 2>{ { { 2.0, 6.0 }, { 6.0, std::exp(1.0) } } }, 1e-9);
    }

    {
        const auto Jrev = reverse_jacobian<2, 2>(reverse_vector_demo, std::array{ 2.0, 3.0 });
        ctx.check_matrix2x2("reverse jacobian", Jrev,
            std::array<std::array<double, 2>, 2>{ { { 3.0, std::cos(2.0) }, { 2.0, 1.0 } } }, 1e-9);
    }

    {
        std::mt19937 rng(20260625u);
        std::uniform_real_distribution<double> dist_x(-1.0, 1.0);
        std::uniform_real_distribution<double> dist_y(-0.75, 1.25);

        for (int sample = 0; sample < 5; ++sample) {
            const std::array<double, 2> point{ dist_x(rng), dist_y(rng) };

            const auto grad_ad = reverse_gradient<2>(reverse_random_scalar, point);
            const auto grad_fd = finite_difference_gradient(scalar_reference, point);
            ctx.check_array2(std::string("fd gradient sample ") + std::to_string(sample), grad_ad, grad_fd, 2e-6);

            const auto jac_ad = reverse_jacobian<2, 2>(reverse_random_vector, point);
            const auto jac_fd = finite_difference_jacobian(vector_reference, point);
            ctx.check_matrix2x2(std::string("fd jacobian sample ") + std::to_string(sample), jac_ad, jac_fd, 2e-6);
        }
    }

    {
        tape.reset();
        Var x = tape.input(0.25);
        Var y = tape.input(2.0);

        Var result = autodiff_pow(y, x)
                   + autodiff_asin(x)
                   + autodiff_acos(x)
                   + autodiff_atan(x)
                   + autodiff_atan2(y, x)
                   + autodiff_sinh(x)
                   + autodiff_cosh(x)
                   + autodiff_tanh(x)
                   + autodiff_abs(x - 1.0);

        tape.backward(result);

        const double expected_dx = std::pow(2.0, 0.25) * std::log(2.0)
                                 + 1.0 / (1.0 + 0.25 * 0.25)
                                 - 2.0 / (0.25 * 0.25 + 2.0 * 2.0)
                                 + std::cosh(0.25)
                                 + std::sinh(0.25)
                                 + (1.0 - std::tanh(0.25) * std::tanh(0.25))
                                 - 1.0;
        const double expected_dy = 0.25 * std::pow(2.0, -0.75)
                                 + 0.25 / (0.25 * 0.25 + 2.0 * 2.0);
        ctx.check_near("extended ops d/dx", x.gradient(), expected_dx, 1e-9);
        ctx.check_near("extended ops d/dy", y.gradient(), expected_dy, 1e-9);
    }

    {
        constexpr double eps = 1e-8;

        tape.reset();
        {
            Var x = tape.input(eps);
            Var loss = autodiff_log(x);
            tape.backward(loss);
            ctx.check_near("edge log value", loss.value(), std::log(eps), 1e-12);
            ctx.check_near("edge log grad", x.gradient(), 1.0 / eps, 1e-2);
        }

        tape.reset();
        {
            Var x = tape.input(eps);
            Var loss = autodiff_sqrt(x);
            tape.backward(loss);
            ctx.check_near("edge sqrt value", loss.value(), std::sqrt(eps), 1e-12);
            ctx.check_near("edge sqrt grad", x.gradient(), 0.5 / std::sqrt(eps), 1e-6);
        }

        tape.reset();
        {
            Var x = tape.input(eps);
            Var loss = autodiff_pow(x, 0.5);
            tape.backward(loss);
            ctx.check_near("edge pow value", loss.value(), std::sqrt(eps), 1e-12);
            ctx.check_near("edge pow grad", x.gradient(), 0.5 / std::sqrt(eps), 1e-6);
        }
    }

    {
        tape.reset();
        const auto vars = tape.inputs(std::array{ 2.0, -0.5 });
        const auto params = tape.parameters(std::array{ 3.0, 1.5 });

        const Var x = vars[0];
        const Var y = vars[1];
        const auto gain = params[0];
        const auto bias = params[1];

        const Var branch = if_else(primal_greater(y, 0.0), x * y, autodiff_abs(y));
        const Var bounded = autodiff_clamp(gain * x + bias, -10.0, 10.0);
        const Var loss = branch + bounded;

        const auto result = tape.evaluate(loss, vars);
        ctx.check_near("branching value", result.value, 8.0);
        ctx.check_array2("branching gradient", result.gradients, std::array<double, 2>{ 3.0, -1.0 });
    }

    {
        constexpr double eps = 1e-8;

        auto branch_test = [&ctx](double x0) {
            Tape<> local_tape;
            Var x = local_tape.input(x0);
            Var loss = if_else(primal_greater(x, 0.0), x * x, -x);
            local_tape.backward(loss);
            const double expected_grad = (x0 > 0.0) ? (2.0 * x0) : -1.0;
            ctx.check_near("branch boundary grad", x.gradient(), expected_grad, 1e-10);
            ctx.check_near("branch boundary value", loss.value(), (x0 > 0.0) ? (x0 * x0) : (-x0), 1e-12);
        };

        branch_test(-eps);
        branch_test(0.0);
        branch_test(+eps);

        auto clamp_test = [&ctx](double x0, double expected_value, double expected_grad) {
            Tape<> local_tape;
            Var x = local_tape.input(x0);
            Var loss = autodiff_clamp(x, -1.0, 1.0);
            local_tape.backward(loss);
            ctx.check_near("clamp boundary value", loss.value(), expected_value, 1e-12);
            ctx.check_near("clamp boundary grad", x.gradient(), expected_grad, 1e-12);
        };

        clamp_test(-1.5, -1.0, 0.0);
        clamp_test(-1.0, -1.0, 0.0);
        clamp_test(-0.5, -0.5, 1.0);
        clamp_test(1.0, 1.0, 0.0);
        clamp_test(1.5, 1.0, 0.0);
    }

    std::cout << (ctx.failures == 0 ? "\nAll reverse-mode autodiff checks passed.\n"
                                    : "\nReverse-mode autodiff checks failed.\n");
    return ctx.failures == 0 ? 0 : 1;
}
