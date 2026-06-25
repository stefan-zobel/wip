
#pragma once

#include <array>
#include <cmath>
#include <concepts>
#include <cstddef>
#include <ostream>

// Type constraint: only floating-point types support differentiation
template<typename T>
concept DifferentiableType = std::floating_point<T>;

// A dual number x = val + der*eps, where eps^2 = 0.
// In forward-mode AD, seed der=1 for the variable you differentiate w.r.t.;
// after one evaluation pass, val holds f(x) and der holds f'(x).
template<DifferentiableType T = double>
struct Dual {
    T val{};  // Primal part  : function value
    T der{};  // Dual part    : directional derivative w.r.t. the seeded variable

    // Compares both val and der - algebraically correct for dual number equality
    auto operator<=>(const Dual&) const = default;
};

// ---------------------------------------------------------------------------
// Unary negation
// ---------------------------------------------------------------------------

template<DifferentiableType T>
constexpr Dual<T> operator-(const Dual<T>& u) {
    return Dual<T>{.val = -u.val, .der = -u.der};
}

// ---------------------------------------------------------------------------
// Addition:  (u + v)' = u' + v'
// ---------------------------------------------------------------------------

template<DifferentiableType T>
constexpr Dual<T> operator+(const Dual<T>& u, const Dual<T>& v) {
    return Dual<T>{.val = u.val + v.val, .der = u.der + v.der};
}
template<DifferentiableType T>
constexpr Dual<T> operator+(const Dual<T>& u, T c) {
    return Dual<T>{.val = u.val + c, .der = u.der};
}
template<DifferentiableType T>
constexpr Dual<T> operator+(T c, const Dual<T>& u) {
    return Dual<T>{.val = c + u.val, .der = u.der};
}

// ---------------------------------------------------------------------------
// Subtraction:  (u - v)' = u' - v'
// ---------------------------------------------------------------------------

template<DifferentiableType T>
constexpr Dual<T> operator-(const Dual<T>& u, const Dual<T>& v) {
    return Dual<T>{.val = u.val - v.val, .der = u.der - v.der};
}
template<DifferentiableType T>
constexpr Dual<T> operator-(const Dual<T>& u, T c) {
    return Dual<T>{.val = u.val - c, .der = u.der};
}
template<DifferentiableType T>
constexpr Dual<T> operator-(T c, const Dual<T>& u) {
    return Dual<T>{.val = c - u.val, .der = -u.der};
}

// ---------------------------------------------------------------------------
// Multiplication:  (u * v)' = u*v' + v*u'
// ---------------------------------------------------------------------------

template<DifferentiableType T>
constexpr Dual<T> operator*(const Dual<T>& u, const Dual<T>& v) {
    return Dual<T>{.val = u.val * v.val, .der = u.val * v.der + v.val * u.der};
}
template<DifferentiableType T>
constexpr Dual<T> operator*(const Dual<T>& u, T c) {
    return Dual<T>{.val = u.val * c, .der = u.der * c};
}
template<DifferentiableType T>
constexpr Dual<T> operator*(T c, const Dual<T>& u) {
    return Dual<T>{.val = c * u.val, .der = c * u.der};
}

// ---------------------------------------------------------------------------
// Division:  (u / v)' = (u'*v - u*v') / v^2
// ---------------------------------------------------------------------------

template<DifferentiableType T>
constexpr Dual<T> operator/(const Dual<T>& u, const Dual<T>& v) {
    return Dual<T>{.val = u.val / v.val,
                   .der = (u.der * v.val - u.val * v.der) / (v.val * v.val)};
}
template<DifferentiableType T>
constexpr Dual<T> operator/(const Dual<T>& u, T c) {
    return Dual<T>{.val = u.val / c, .der = u.der / c};
}
template<DifferentiableType T>
constexpr Dual<T> operator/(T c, const Dual<T>& u) {
    return Dual<T>{.val = c / u.val, .der = -c * u.der / (u.val * u.val)};
}

// ---------------------------------------------------------------------------
// Compound assignment
// ---------------------------------------------------------------------------

template<DifferentiableType T> constexpr Dual<T>& operator+=(Dual<T>& u, const Dual<T>& v) { return u = u + v; }
template<DifferentiableType T> constexpr Dual<T>& operator-=(Dual<T>& u, const Dual<T>& v) { return u = u - v; }
template<DifferentiableType T> constexpr Dual<T>& operator*=(Dual<T>& u, const Dual<T>& v) { return u = u * v; }
template<DifferentiableType T> constexpr Dual<T>& operator/=(Dual<T>& u, const Dual<T>& v) { return u = u / v; }

// ---------------------------------------------------------------------------
// Math functions - all apply the chain rule: d/dx f(u) = f'(u) * u'
// (std::sin/cos/... are not constexpr in C++20, so these cannot be constexpr)
// ---------------------------------------------------------------------------

// d/dx sin(u) = cos(u) * u'
template<DifferentiableType T>
Dual<T> autodiff_sin(const Dual<T>& u) {
    return Dual<T>{.val = std::sin(u.val), .der = std::cos(u.val) * u.der};
}

// d/dx cos(u) = -sin(u) * u'
template<DifferentiableType T>
Dual<T> autodiff_cos(const Dual<T>& u) {
    return Dual<T>{.val = std::cos(u.val), .der = -std::sin(u.val) * u.der};
}

// d/dx tan(u) = u' / cos^2(u)
template<DifferentiableType T>
Dual<T> autodiff_tan(const Dual<T>& u) {
    const T c = std::cos(u.val);
    return Dual<T>{.val = std::tan(u.val), .der = u.der / (c * c)};
}

// d/dx exp(u) = exp(u) * u'
template<DifferentiableType T>
Dual<T> autodiff_exp(const Dual<T>& u) {
    const T e = std::exp(u.val);
    return Dual<T>{.val = e, .der = e * u.der};
}

// d/dx log(u) = u' / u    (natural log; u > 0)
template<DifferentiableType T>
Dual<T> autodiff_log(const Dual<T>& u) {
    return Dual<T>{.val = std::log(u.val), .der = u.der / u.val};
}

// d/dx sqrt(u) = u' / (2 * sqrt(u))    (u > 0)
template<DifferentiableType T>
Dual<T> autodiff_sqrt(const Dual<T>& u) {
    const T s = std::sqrt(u.val);
    return Dual<T>{.val = s, .der = u.der / (T{2} * s)};
}

// d/dx u^n = n * u^(n-1) * u'    (real exponent n, u > 0 for non-integer n)
template<DifferentiableType T>
Dual<T> autodiff_pow(const Dual<T>& u, T n) {
    return Dual<T>{.val = std::pow(u.val, n),
                   .der = n * std::pow(u.val, n - T{1}) * u.der};
}

// d/dx u^v where both u and v are Dual:  (u^v)' = u^(v-1) * (v*u' + u*ln(u)*v')
template<DifferentiableType T>
Dual<T> autodiff_pow(const Dual<T>& u, const Dual<T>& v) {
    const T p = std::pow(u.val, v.val);
    return Dual<T>{.val = p,
                   .der = p * (v.val * u.der / u.val + std::log(u.val) * v.der)};
}

// ---------------------------------------------------------------------------
// Inverse trigonometric functions
// ---------------------------------------------------------------------------

// d/dx asin(u) = u' / sqrt(1 - u²)    (|u| < 1)
template<DifferentiableType T>
Dual<T> autodiff_asin(const Dual<T>& u) {
    return Dual<T>{.val = std::asin(u.val),
                   .der = u.der / std::sqrt(T{1} - u.val * u.val)};
}

// d/dx acos(u) = -u' / sqrt(1 - u²)    (|u| < 1)
template<DifferentiableType T>
Dual<T> autodiff_acos(const Dual<T>& u) {
    return Dual<T>{.val = std::acos(u.val),
                   .der = -u.der / std::sqrt(T{1} - u.val * u.val)};
}

// d/dx atan(u) = u' / (1 + u²)
template<DifferentiableType T>
Dual<T> autodiff_atan(const Dual<T>& u) {
    return Dual<T>{.val = std::atan(u.val),
                   .der = u.der / (T{1} + u.val * u.val)};
}

// d/dx atan2(y, x) via chain rule: (x*y' - y*x') / (x² + y²)
template<DifferentiableType T>
Dual<T> autodiff_atan2(const Dual<T>& y, const Dual<T>& x) {
    const T r2 = x.val * x.val + y.val * y.val;
    return Dual<T>{.val = std::atan2(y.val, x.val),
                   .der = (x.val * y.der - y.val * x.der) / r2};
}

// ---------------------------------------------------------------------------
// Hyperbolic functions
// ---------------------------------------------------------------------------

// d/dx sinh(u) = cosh(u) * u'
template<DifferentiableType T>
Dual<T> autodiff_sinh(const Dual<T>& u) {
    return Dual<T>{.val = std::sinh(u.val), .der = std::cosh(u.val) * u.der};
}

// d/dx cosh(u) = sinh(u) * u'
template<DifferentiableType T>
Dual<T> autodiff_cosh(const Dual<T>& u) {
    return Dual<T>{.val = std::cosh(u.val), .der = std::sinh(u.val) * u.der};
}

// d/dx tanh(u) = u' * (1 - tanh²(u))
template<DifferentiableType T>
Dual<T> autodiff_tanh(const Dual<T>& u) {
    const T t = std::tanh(u.val);
    return Dual<T>{.val = t, .der = u.der * (T{1} - t * t)};
}

// ---------------------------------------------------------------------------
// Absolute value
// ---------------------------------------------------------------------------

// d/dx |u| = sign(u) * u'   (subdifferential at u=0 is conventionally 0)
template<DifferentiableType T>
Dual<T> autodiff_abs(const Dual<T>& u) {
    const T s = (u.val > T{0}) ? T{1} : (u.val < T{0}) ? T{-1} : T{0};
    return Dual<T>{.val = std::abs(u.val), .der = s * u.der};
}

// ---------------------------------------------------------------------------
// Stream output
// ---------------------------------------------------------------------------

template<DifferentiableType T>
std::ostream& operator<<(std::ostream& os, const Dual<T>& d) {
    return os << "Dual{val=" << d.val << ", der=" << d.der << "}";
}

// ---------------------------------------------------------------------------
// Helper factories
// ---------------------------------------------------------------------------

// Single-variable case: create the one variable to differentiate w.r.t. (der=1)
template<DifferentiableType T>
[[nodiscard]] constexpr Dual<T> make_var(T val) noexcept {
    return Dual<T>{.val = val, .der = T{1}};
}

// Treat a value as a constant in an expression (der=0)
template<DifferentiableType T>
[[nodiscard]] constexpr Dual<T> make_const(T val) noexcept {
    return Dual<T>{.val = val, .der = T{0}};
}

// ---------------------------------------------------------------------------
// Gradient and Jacobian — compile-time sizes, zero heap allocation
// ---------------------------------------------------------------------------

// gradient: makes N seeded passes over f: R^N -> R
// Returns {df/dx_0, ..., df/dx_{N-1}} at the point x.
// f must be callable as: Dual<T> f(std::array<Dual<T>, N>)
template<std::size_t N, DifferentiableType T, typename Func>
    requires std::invocable<Func, std::array<Dual<T>, N>>
[[nodiscard]] std::array<T, N> gradient(Func&& f, const std::array<T, N>& x) {
    std::array<T, N> grad{};
    for (std::size_t i = 0; i < N; ++i) {
        std::array<Dual<T>, N> xd;
        for (std::size_t j = 0; j < N; ++j)
            xd[j] = Dual<T>{.val = x[j], .der = (i == j) ? T{1} : T{0}};
        grad[i] = f(xd).der;
    }
    return grad;
}

// jacobian: makes N seeded passes over f: R^N -> R^M
// Returns the N×M matrix J where J[i][k] = df_k/dx_i at the point x.
// f must be callable as: std::array<Dual<T>, M> f(std::array<Dual<T>, N>)
template<std::size_t N, std::size_t M, DifferentiableType T, typename Func>
    requires std::invocable<Func, std::array<Dual<T>, N>>
[[nodiscard]] std::array<std::array<T, M>, N> jacobian(Func&& f, const std::array<T, N>& x) {
    std::array<std::array<T, M>, N> J{};
    for (std::size_t i = 0; i < N; ++i) {
        std::array<Dual<T>, N> xd;
        for (std::size_t j = 0; j < N; ++j)
            xd[j] = Dual<T>{.val = x[j], .der = (i == j) ? T{1} : T{0}};
        const auto yd = f(xd);
        for (std::size_t k = 0; k < M; ++k)
            J[i][k] = yd[k].der;
    }
    return J;
}
