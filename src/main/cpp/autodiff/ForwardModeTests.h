#pragma once

#include <algorithm>
#include <array>
#include <cmath>
#include <iostream>
#include <random>
#include <string>
#include <string_view>

#include "Dual.h"

struct ForwardModeTestContext {
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

inline int run_forward_mode_tests() {
    ForwardModeTestContext ctx{};

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

    auto scalar_test = [](const Dual<double>& x) {
        return x * x + autodiff_sin(x);
    };

    auto multivar_test = [](const Dual<double>& x, const Dual<double>& y) {
        return x * x * y + autodiff_exp(y);
    };

    auto gradient_demo = [](std::array<Dual<double>, 2> xy) -> Dual<double> {
        return xy[0] * xy[0] + 2.0 * xy[1];
    };

    auto jacobian_demo = [](std::array<Dual<double>, 2> xy) -> std::array<Dual<double>, 2> {
        return { xy[0] * xy[1], xy[0] + xy[1] };
    };

    auto extended_demo = [](const Dual<double>& x, const Dual<double>& y) {
        return autodiff_pow(y, x)
             + autodiff_asin(x)
             + autodiff_acos(x)
             + autodiff_atan(x)
             + autodiff_atan2(y, x)
             + autodiff_sinh(x)
             + autodiff_cosh(x)
             + autodiff_tanh(x)
             + autodiff_abs(x - 1.0);
    };

    auto random_scalar_dual = [](std::array<Dual<double>, 2> xy) -> Dual<double> {
        return autodiff_sin(xy[0]) * autodiff_exp(xy[1])
             + xy[0] * xy[0] * xy[1]
             + autodiff_log(xy[0] + 2.5)
             + autodiff_sqrt(xy[1] + 3.0);
    };

    auto random_scalar_reference = [](const std::array<double, 2>& xy) {
        return std::sin(xy[0]) * std::exp(xy[1])
             + xy[0] * xy[0] * xy[1]
             + std::log(xy[0] + 2.5)
             + std::sqrt(xy[1] + 3.0);
    };

    auto random_vector_dual = [](std::array<Dual<double>, 2> xy) -> std::array<Dual<double>, 2> {
        return {
            xy[0] * xy[1] + autodiff_sin(xy[0]),
            autodiff_exp(xy[1]) + xy[0] / (xy[1] + 3.0)
        };
    };

    auto random_vector_reference = [](const std::array<double, 2>& xy) {
        return std::array<double, 2>{
            xy[0] * xy[1] + std::sin(xy[0]),
            std::exp(xy[1]) + xy[0] / (xy[1] + 3.0)
        };
    };

    auto branch_dual = []<typename T>(const Dual<T>& x) {
        return (x.val > T{ 0 }) ? x * x : -x;
    };

    auto clamp_dual = []<typename T>(const Dual<T>& x, T lo, T hi) {
        if (x.val < lo) return Dual<T>{ .val = lo, .der = T{ 0 } };
        if (x.val > hi) return Dual<T>{ .val = hi, .der = T{ 0 } };
        return x;
    };

    std::cout << "\n=== Forward-mode AD tests ===\n";

    {
        const auto r = scalar_test(make_var(2.0));
        ctx.check_near("forward scalar value", r.val, 4.0 + std::sin(2.0));
        ctx.check_near("forward scalar derivative", r.der, 4.0 + std::cos(2.0));
    }

    {
        const auto rg_dx = multivar_test(Dual<double>{ 3.0, 1.0 }, Dual<double>{ 1.0, 0.0 });
        const auto rg_dy = multivar_test(Dual<double>{ 3.0, 0.0 }, Dual<double>{ 1.0, 1.0 });
        ctx.check_near("forward multivar value", rg_dx.val, 9.0 + std::exp(1.0));
        ctx.check_near("forward multivar d/dx", rg_dx.der, 6.0);
        ctx.check_near("forward multivar d/dy", rg_dy.der, 9.0 + std::exp(1.0));
    }

    {
        const auto grad = gradient<2>(gradient_demo, std::array{ 3.0, 1.0 });
        ctx.check_array2("forward gradient", grad, std::array<double, 2>{ 6.0, 2.0 });
    }

    {
        const auto J = jacobian<2, 2>(jacobian_demo, std::array{ 2.0, 3.0 });
        ctx.check_matrix2x2("forward jacobian", J,
            std::array<std::array<double, 2>, 2>{ { { 3.0, 1.0 }, { 2.0, 1.0 } } });
    }

    {
        const auto result = 3.0 * make_var(5.0) - 1.0;
        ctx.check_near("forward scalar overload value", result.val, 14.0);
        ctx.check_near("forward scalar overload grad", result.der, 3.0);
    }

    {
        const auto x = make_var(2.0);
        const auto r = x * x + autodiff_sqrt(x);
        ctx.check_near("forward sqrt grad", r.der, 4.0 + 0.5 / std::sqrt(2.0));
    }

    {
        const auto x = make_var(4.0);
        const auto p = make_const(3.0);
        const auto result = p * x + 1.0;
        ctx.check_near("forward const-parameter value", result.val, 13.0);
        ctx.check_near("forward const-parameter grad", result.der, 3.0);
    }

    {
        const auto x = Dual<double>{ 0.25, 1.0 };
        const auto y = Dual<double>{ 2.0, 0.0 };
        const auto rx = extended_demo(x, y);
        const double expected_dx = std::pow(2.0, 0.25) * std::log(2.0)
                                 + 1.0 / (1.0 + 0.25 * 0.25)
                                 - 2.0 / (0.25 * 0.25 + 2.0 * 2.0)
                                 + std::cosh(0.25)
                                 + std::sinh(0.25)
                                 + (1.0 - std::tanh(0.25) * std::tanh(0.25))
                                 - 1.0;
        ctx.check_near("forward extended ops d/dx", rx.der, expected_dx, 1e-9);

        const auto x0 = Dual<double>{ 0.25, 0.0 };
        const auto y1 = Dual<double>{ 2.0, 1.0 };
        const auto ry = extended_demo(x0, y1);
        const double expected_dy = 0.25 * std::pow(2.0, -0.75)
                                 + 0.25 / (0.25 * 0.25 + 2.0 * 2.0);
        ctx.check_near("forward extended ops d/dy", ry.der, expected_dy, 1e-9);
    }

    {
        std::mt19937 rng(20260625u);
        std::uniform_real_distribution<double> dist_x(-1.0, 1.0);
        std::uniform_real_distribution<double> dist_y(-0.75, 1.25);

        for (int sample = 0; sample < 5; ++sample) {
            const std::array<double, 2> point{ dist_x(rng), dist_y(rng) };
            const auto grad_ad = gradient<2>(random_scalar_dual, point);
            const auto grad_fd = finite_difference_gradient(random_scalar_reference, point);
            ctx.check_array2(std::string("forward fd gradient sample ") + std::to_string(sample), grad_ad, grad_fd, 2e-6);

            const auto jac_ad = jacobian<2, 2>(random_vector_dual, point);
            const auto jac_fd = finite_difference_jacobian(random_vector_reference, point);
            ctx.check_matrix2x2(std::string("forward fd jacobian sample ") + std::to_string(sample), jac_ad, jac_fd, 2e-6);
        }
    }

    {
        constexpr double eps = 1e-8;
        const auto log_r = autodiff_log(Dual<double>{ eps, 1.0 });
        ctx.check_near("forward edge log value", log_r.val, std::log(eps), 1e-12);
        ctx.check_near("forward edge log grad", log_r.der, 1.0 / eps, 1e-2);

        const auto sqrt_r = autodiff_sqrt(Dual<double>{ eps, 1.0 });
        ctx.check_near("forward edge sqrt value", sqrt_r.val, std::sqrt(eps), 1e-12);
        ctx.check_near("forward edge sqrt grad", sqrt_r.der, 0.5 / std::sqrt(eps), 1e-6);

        const auto pow_r = autodiff_pow(Dual<double>{ eps, 1.0 }, 0.5);
        ctx.check_near("forward edge pow value", pow_r.val, std::sqrt(eps), 1e-12);
        ctx.check_near("forward edge pow grad", pow_r.der, 0.5 / std::sqrt(eps), 1e-6);
    }

    {
        constexpr double eps = 1e-8;

        const auto branch_neg = branch_dual(Dual<double>{ -eps, 1.0 });
        const auto branch_zero = branch_dual(Dual<double>{ 0.0, 1.0 });
        const auto branch_pos = branch_dual(Dual<double>{ eps, 1.0 });
        ctx.check_near("forward branch neg value", branch_neg.val, eps, 1e-12);
        ctx.check_near("forward branch neg grad", branch_neg.der, -1.0, 1e-12);
        ctx.check_near("forward branch zero value", branch_zero.val, 0.0, 1e-12);
        ctx.check_near("forward branch zero grad", branch_zero.der, -1.0, 1e-12);
        ctx.check_near("forward branch pos value", branch_pos.val, eps * eps, 1e-16);
        ctx.check_near("forward branch pos grad", branch_pos.der, 2.0 * eps, 1e-12);

        const auto clamp_lo = clamp_dual(Dual<double>{ -1.5, 1.0 }, -1.0, 1.0);
        const auto clamp_edge_lo = clamp_dual(Dual<double>{ -1.0, 1.0 }, -1.0, 1.0);
        const auto clamp_mid = clamp_dual(Dual<double>{ -0.5, 1.0 }, -1.0, 1.0);
        const auto clamp_edge_hi = clamp_dual(Dual<double>{ 1.0, 1.0 }, -1.0, 1.0);
        const auto clamp_hi = clamp_dual(Dual<double>{ 1.5, 1.0 }, -1.0, 1.0);
        ctx.check_near("forward clamp lo value", clamp_lo.val, -1.0, 1e-12);
        ctx.check_near("forward clamp lo grad", clamp_lo.der, 0.0, 1e-12);
        ctx.check_near("forward clamp edge lo value", clamp_edge_lo.val, -1.0, 1e-12);
        ctx.check_near("forward clamp edge lo grad", clamp_edge_lo.der, 1.0, 1e-12);
        ctx.check_near("forward clamp mid value", clamp_mid.val, -0.5, 1e-12);
        ctx.check_near("forward clamp mid grad", clamp_mid.der, 1.0, 1e-12);
        ctx.check_near("forward clamp edge hi value", clamp_edge_hi.val, 1.0, 1e-12);
        ctx.check_near("forward clamp edge hi grad", clamp_edge_hi.der, 1.0, 1e-12);
        ctx.check_near("forward clamp hi value", clamp_hi.val, 1.0, 1e-12);
        ctx.check_near("forward clamp hi grad", clamp_hi.der, 0.0, 1e-12);
    }

    std::cout << (ctx.failures == 0 ? "\nAll forward-mode autodiff checks passed.\n"
                                    : "\nForward-mode autodiff checks failed.\n");
    return ctx.failures == 0 ? 0 : 1;
}
