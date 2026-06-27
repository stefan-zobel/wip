
#pragma once

#include <array>
#include <cassert>
#include <cmath>
#include <concepts>
#include <cstddef>
#include <functional>
#include <ostream>
#include <span>
#include <type_traits>
#include <utility>
#include <vector>

#include "Dual.h"

// Operations that can be recorded on the Wengert tape
enum class OpType {
    Input,                   // independent leaf variable (no parents)
    Parameter,               // passive leaf that participates in values but not in reported grads
    Constant,                // scalar literal promoted to a leaf node
    Add, Sub, Mul, Div, Pow, Atan2, // binary arithmetic / binary math
    Neg,                     // unary negation
    Sin, Cos, Tan, Asin, Acos, Atan,     // trigonometric
    Sinh, Cosh, Tanh,                    // hyperbolic
    Exp, Log, Sqrt, Abs                  // exponential / logarithmic / absolute value
};

namespace reverse_mode_detail {
    template <typename T>
    [[nodiscard]] constexpr T zero() { return T{ 0 }; }

    template <typename T>
    [[nodiscard]] constexpr T one() { return T{ 1 }; }

    template <typename T>
    [[nodiscard]] constexpr T two() { return T{ 2 }; }

    template <typename T>
    [[nodiscard]] constexpr T minus_one() { return T{ -1 }; }

    template <typename T>
    [[nodiscard]] constexpr const auto& primal_value(const T& x) {
        if constexpr (requires { x.val; }) return primal_value(x.val);
        else return x;
    }

    template <typename T>
    [[nodiscard]] constexpr T sign_of(const T& x) {
        using Primal = std::remove_cvref_t<decltype(primal_value(x))>;
        const auto& p = primal_value(x);
        return (p > zero<Primal>()) ? one<T>() : (p < zero<Primal>()) ? minus_one<T>() : zero<T>();
    }
}

// A single record on the Wengert tape.
// Fixed-size POD - stays cache-friendly in a contiguous vector.
template <typename T = double>
struct Node {
    T           val{ reverse_mode_detail::zero<T>() };   // Primal value  - set during the forward pass
    T           adj{ reverse_mode_detail::zero<T>() };   // Adjoint       - accumulated during backward pass
    std::size_t left_idx  { 0 };     // Index of left  parent; sole operand for unary ops
    std::size_t right_idx { 0 };     // Index of right parent; unused for unary ops
    OpType      op{ OpType::Input };
};

template <typename T = double> struct Tape;  // forward declaration so Var can hold a Tape*

// Lightweight handle: an index into a Tape's node array + a non-owning pointer.
// sizeof(Var) == 16 bytes on 64-bit platforms.
template <typename T = double>
struct Var {
    std::size_t idx  { 0 };
    Tape<T>*       tape { nullptr };

    T value()    const;   // defined below, after Tape is complete
    T gradient() const;
};

template <typename T = double>
struct parameter {
    Var<T> var{};

    parameter() = default;
    explicit parameter(Var<T> value) : var(value) {}

    operator Var<T>() const { return var; }
    T value() const { return var.value(); }
};

namespace reverse_mode_detail {
    template <typename U>
    struct is_var : std::false_type {};

    template <typename T>
    struct is_var<Var<T>> : std::true_type {};

    template <typename U>
    struct is_parameter : std::false_type {};

    template <typename T>
    struct is_parameter<parameter<T>> : std::true_type {};

    template <typename U>
    inline constexpr bool is_tape_object_v =
        is_var<std::remove_cvref_t<U>>::value || is_parameter<std::remove_cvref_t<U>>::value;
}

template <typename T>
struct ReverseValueAndGradients {
    T value{};
    std::vector<T> gradients{};
};

template <typename T, std::size_t N>
struct ReverseArrayResult {
    T value{};
    std::array<T, N> gradients{};
};

template <typename T, std::size_t M, std::size_t N>
struct ReverseVectorArrayResult {
    std::array<T, M> values{};
    std::array<std::array<T, M>, N> jacobian{};
};

// Owns the entire computation graph as a flat, contiguous array of Nodes.
// One Tape per independent computation - naturally thread-safe.
template <typename T = double>
struct Tape {
    std::vector<Node<T>> nodes;

private:
    [[nodiscard]] Var<T> make_leaf(OpType op, const T& value) {
        nodes.push_back(Node<T>{ .val = value, .op = op });
        return Var<T>{ nodes.size() - 1, this };
    }

public:

    // Pre-allocate storage to avoid mid-recording reallocations
    void reserve(std::size_t n) { nodes.reserve(n); }

    // Discard all recorded nodes - start a fresh recording
    void reset() { nodes.clear(); }

    // Register an independent input variable (d(loss)/d(var) will be computed)
    [[nodiscard]] Var<T> input(T value) {
        return make_leaf(OpType::Input, value);
    }

    [[nodiscard]] Var<T> variable(T value) {
        return input(value);
    }

    // Register a passive parameter: participates in the primal evaluation but is not exposed as a variable.
    [[nodiscard]] ::parameter<T> parameter(T value) {
        return ::parameter<T>(make_leaf(OpType::Parameter, value));
    }

    [[nodiscard]] ::parameter<T> passive(T value) {
        return parameter(value);
    }

    // Register a numeric constant (gradient is accumulated but semantically unused)
    [[nodiscard]] Var<T> constant(T value) {
        return make_leaf(OpType::Constant, value);
    }

    template <std::size_t N>
    [[nodiscard]] std::array<Var<T>, N> inputs(const std::array<T, N>& values) {
        std::array<Var<T>, N> vars{};
        for (std::size_t i = 0; i < N; ++i) {
            vars[i] = input(values[i]);
        }
        return vars;
    }

    template <std::size_t N>
    [[nodiscard]] std::array<Var<T>, N> variables(const std::array<T, N>& values) {
        return inputs(values);
    }

    template <std::size_t N>
    [[nodiscard]] std::array<::parameter<T>, N> parameters(const std::array<T, N>& values) {
        std::array<::parameter<T>, N> params{};
        for (std::size_t i = 0; i < N; ++i) {
            params[i] = parameter(values[i]);
        }
        return params;
    }

    static T call_sin(const T& x) { if constexpr (requires { autodiff_sin(x); }) return autodiff_sin(x); else return std::sin(x); }
    static T call_cos(const T& x) { if constexpr (requires { autodiff_cos(x); }) return autodiff_cos(x); else return std::cos(x); }
    static T call_tan(const T& x) { if constexpr (requires { autodiff_tan(x); }) return autodiff_tan(x); else return std::tan(x); }
    static T call_asin(const T& x) { if constexpr (requires { autodiff_asin(x); }) return autodiff_asin(x); else return std::asin(x); }
    static T call_acos(const T& x) { if constexpr (requires { autodiff_acos(x); }) return autodiff_acos(x); else return std::acos(x); }
    static T call_atan(const T& x) { if constexpr (requires { autodiff_atan(x); }) return autodiff_atan(x); else return std::atan(x); }
    static T call_atan2(const T& y, const T& x) { if constexpr (requires { autodiff_atan2(y, x); }) return autodiff_atan2(y, x); else return std::atan2(y, x); }
    static T call_sinh(const T& x) { if constexpr (requires { autodiff_sinh(x); }) return autodiff_sinh(x); else return std::sinh(x); }
    static T call_cosh(const T& x) { if constexpr (requires { autodiff_cosh(x); }) return autodiff_cosh(x); else return std::cosh(x); }
    static T call_tanh(const T& x) { if constexpr (requires { autodiff_tanh(x); }) return autodiff_tanh(x); else return std::tanh(x); }
    static T call_exp(const T& x) { if constexpr (requires { autodiff_exp(x); }) return autodiff_exp(x); else return std::exp(x); }
    static T call_log(const T& x) { if constexpr (requires { autodiff_log(x); }) return autodiff_log(x); else return std::log(x); }
    static T call_sqrt(const T& x) { if constexpr (requires { autodiff_sqrt(x); }) return autodiff_sqrt(x); else return std::sqrt(x); }
    static T call_pow(const T& x, const T& y) { if constexpr (requires { autodiff_pow(x, y); }) return autodiff_pow(x, y); else return std::pow(x, y); }
    static T call_abs(const T& x) { if constexpr (requires { autodiff_abs(x); }) return autodiff_abs(x); else return std::abs(x); }

    // Single O(n) reverse pass: propagates d(loss)/d(every node) across the tape.
    // After this call, var.gradient() returns the partial derivative for any Var.
    void backward(Var<T> loss) {
        const std::array losses{ loss };
        const std::array seeds{ reverse_mode_detail::one<T>() };
        backward(std::span<const Var<T>>(losses), std::span<const T>(seeds));
    }

    void backward(std::span<const Var<T>> losses, std::span<const T> seeds = {}) {
        if (nodes.empty() || losses.empty()) return;
        assert(seeds.empty() || seeds.size() == losses.size());

        for (auto& node : nodes) {
            node.adj = reverse_mode_detail::zero<T>();
        }

        std::vector<bool> active(nodes.size(), false);
        std::vector<std::size_t> stack;
        stack.reserve(losses.size());

        for (std::size_t i = 0; i < losses.size(); ++i) {
            assert(losses[i].tape == this);
            nodes[losses[i].idx].adj += seeds.empty() ? reverse_mode_detail::one<T>() : seeds[i];
            stack.push_back(losses[i].idx);
        }

        while (!stack.empty()) {
            const std::size_t idx = stack.back();
            stack.pop_back();

            if (active[idx]) continue;
            active[idx] = true;

            const Node<T>& nd = nodes[idx];
            switch (nd.op) {
            case OpType::Input:
            case OpType::Parameter:
            case OpType::Constant:
                break;

            case OpType::Neg:
            case OpType::Sin:
            case OpType::Cos:
            case OpType::Tan:
            case OpType::Asin:
            case OpType::Acos:
            case OpType::Atan:
            case OpType::Sinh:
            case OpType::Cosh:
            case OpType::Tanh:
            case OpType::Exp:
            case OpType::Log:
            case OpType::Sqrt:
            case OpType::Abs:
                stack.push_back(nd.left_idx);
                break;

            case OpType::Add:
            case OpType::Sub:
            case OpType::Mul:
            case OpType::Div:
            case OpType::Pow:
            case OpType::Atan2:
                stack.push_back(nd.left_idx);
                stack.push_back(nd.right_idx);
                break;
            }
        }

        for (std::size_t i = nodes.size(); i-- > 0;) {
            if (!active[i]) continue;

            const Node<T>& nd = nodes[i];
            const T a = nd.adj;
            if (a == reverse_mode_detail::zero<T>()) continue;

            switch (nd.op) {
            case OpType::Input:
            case OpType::Parameter:
            case OpType::Constant:
                break;

            case OpType::Neg:
                nodes[nd.left_idx].adj -= a;
                break;

            case OpType::Add:
                nodes[nd.left_idx].adj  += a;
                nodes[nd.right_idx].adj += a;
                break;

            case OpType::Sub:
                nodes[nd.left_idx].adj  += a;
                nodes[nd.right_idx].adj -= a;
                break;

            case OpType::Mul:
                nodes[nd.left_idx].adj  += a * nodes[nd.right_idx].val;
                nodes[nd.right_idx].adj += a * nodes[nd.left_idx].val;
                break;

            case OpType::Div: {
                const T rv = nodes[nd.right_idx].val;
                nodes[nd.left_idx].adj  += a / rv;
                nodes[nd.right_idx].adj -= a * nd.val / rv;
                break;
            }
            case OpType::Pow: {
                const T& lv = nodes[nd.left_idx].val;
                const T& rv = nodes[nd.right_idx].val;
                nodes[nd.left_idx].adj  += a * nd.val * rv / lv;
                nodes[nd.right_idx].adj += a * nd.val * call_log(lv);
                break;
            }
            case OpType::Atan2: {
                const T& y = nodes[nd.left_idx].val;
                const T& x = nodes[nd.right_idx].val;
                const T r2 = x * x + y * y;
                nodes[nd.left_idx].adj  += a * x / r2;
                nodes[nd.right_idx].adj -= a * y / r2;
                break;
            }
            case OpType::Sin:
                nodes[nd.left_idx].adj += a * call_cos(nodes[nd.left_idx].val);
                break;

            case OpType::Cos:
                nodes[nd.left_idx].adj -= a * call_sin(nodes[nd.left_idx].val);
                break;

            case OpType::Tan: {
                const T c = call_cos(nodes[nd.left_idx].val);
                nodes[nd.left_idx].adj += a / (c * c);
                break;
            }
            case OpType::Asin: {
                const T& v = nodes[nd.left_idx].val;
                nodes[nd.left_idx].adj += a / call_sqrt(reverse_mode_detail::one<T>() - v * v);
                break;
            }
            case OpType::Acos: {
                const T& v = nodes[nd.left_idx].val;
                nodes[nd.left_idx].adj -= a / call_sqrt(reverse_mode_detail::one<T>() - v * v);
                break;
            }
            case OpType::Atan: {
                const T& v = nodes[nd.left_idx].val;
                nodes[nd.left_idx].adj += a / (reverse_mode_detail::one<T>() + v * v);
                break;
            }
            case OpType::Sinh:
                nodes[nd.left_idx].adj += a * call_cosh(nodes[nd.left_idx].val);
                break;

            case OpType::Cosh:
                nodes[nd.left_idx].adj += a * call_sinh(nodes[nd.left_idx].val);
                break;

            case OpType::Tanh: {
                const T& t = nd.val;
                nodes[nd.left_idx].adj += a * (reverse_mode_detail::one<T>() - t * t);
                break;
            }
            case OpType::Exp:
                nodes[nd.left_idx].adj += a * nd.val;
                break;

            case OpType::Log:
                nodes[nd.left_idx].adj += a / nodes[nd.left_idx].val;
                break;

            case OpType::Sqrt:
                nodes[nd.left_idx].adj += a / (reverse_mode_detail::two<T>() * nd.val);
                break;

            case OpType::Abs:
                nodes[nd.left_idx].adj += a * reverse_mode_detail::sign_of(nodes[nd.left_idx].val);
                break;
            }
        }
    }

    [[nodiscard]] T derivative(Var<T> loss, Var<T> wrt) {
        assert(loss.tape == this && wrt.tape == this);
        backward(loss);
        return wrt.gradient();
    }

    [[nodiscard]] std::vector<T> gradients(Var<T> loss, std::span<const Var<T>> wrt) {
        backward(loss);
        std::vector<T> grad;
        grad.reserve(wrt.size());
        for (Var<T> var : wrt) {
            assert(var.tape == this);
            grad.push_back(var.gradient());
        }
        return grad;
    }

    template <std::size_t N>
    [[nodiscard]] std::array<T, N> gradients(Var<T> loss, const std::array<Var<T>, N>& wrt) {
        backward(loss);
        std::array<T, N> grad{};
        for (std::size_t i = 0; i < N; ++i) {
            assert(wrt[i].tape == this);
            grad[i] = wrt[i].gradient();
        }
        return grad;
    }

    [[nodiscard]] ReverseValueAndGradients<T> evaluate(Var<T> loss, std::span<const Var<T>> wrt) {
        return ReverseValueAndGradients<T>{ .value = loss.value(), .gradients = gradients(loss, wrt) };
    }

    template <std::size_t N>
    [[nodiscard]] ReverseArrayResult<T, N> evaluate(Var<T> loss, const std::array<Var<T>, N>& wrt) {
        return ReverseArrayResult<T, N>{ .value = loss.value(), .gradients = gradients(loss, wrt) };
    }

    template <std::size_t M, std::size_t N>
    [[nodiscard]] ReverseVectorArrayResult<T, M, N> evaluate(const std::array<Var<T>, M>& outputs,
                                                             const std::array<Var<T>, N>& wrt) {
        ReverseVectorArrayResult<T, M, N> result{};
        for (std::size_t k = 0; k < M; ++k) {
            result.values[k] = outputs[k].value();
            backward(outputs[k]);
            for (std::size_t i = 0; i < N; ++i) {
                assert(wrt[i].tape == this);
                result.jacobian[i][k] = wrt[i].gradient();
            }
        }
        return result;
    }
};

template <typename T> inline T Var<T>::value()    const { assert(tape); return tape->nodes[idx].val; }
template <typename T> inline T Var<T>::gradient() const { assert(tape); return tape->nodes[idx].adj; }

template <typename T>
[[nodiscard]] inline T primal_value(const Var<T>& value) {
    return value.value();
}

template <typename T>
[[nodiscard]] inline T primal_value(const parameter<T>& value) {
    return value.value();
}

template <typename T>
    requires (!reverse_mode_detail::is_tape_object_v<T>)
[[nodiscard]] inline const T& primal_value(const T& value) {
    return value;
}

template <typename T>
[[nodiscard]] inline Var<T> as_var(Var<T> value) {
    return value;
}

template <typename T>
[[nodiscard]] inline Var<T> as_var(parameter<T> value) {
    return static_cast<Var<T>>(value);
}

template <typename T>
[[nodiscard]] inline T gradient_of(const Var<T>& value) {
    return value.gradient();
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_equal(const L& lhs, const R& rhs) {
    return primal_value(lhs) == primal_value(rhs);
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_not_equal(const L& lhs, const R& rhs) {
    return !primal_equal(lhs, rhs);
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_less(const L& lhs, const R& rhs) {
    return primal_value(lhs) < primal_value(rhs);
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_less_equal(const L& lhs, const R& rhs) {
    return primal_value(lhs) <= primal_value(rhs);
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_greater(const L& lhs, const R& rhs) {
    return primal_value(lhs) > primal_value(rhs);
}

template <typename L, typename R>
[[nodiscard]] inline bool primal_greater_equal(const L& lhs, const R& rhs) {
    return primal_value(lhs) >= primal_value(rhs);
}

template <typename T>
    requires (!reverse_mode_detail::is_tape_object_v<T>)
[[nodiscard]] inline T if_else(bool cond, const T& if_true, const T& if_false) {
    return cond ? if_true : if_false;
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, Var<T> if_true, Var<T> if_false) {
    assert(if_true.tape == if_false.tape);
    return cond ? if_true : if_false;
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, parameter<T> if_true, parameter<T> if_false) {
    return if_else(cond, as_var(if_true), as_var(if_false));
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, Var<T> if_true, parameter<T> if_false) {
    return if_else(cond, if_true, as_var(if_false));
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, parameter<T> if_true, Var<T> if_false) {
    return if_else(cond, as_var(if_true), if_false);
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, Var<T> if_true, T if_false) {
    return cond ? if_true : if_true.tape->constant(if_false);
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, T if_true, Var<T> if_false) {
    return cond ? if_false.tape->constant(if_true) : if_false;
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, parameter<T> if_true, T if_false) {
    return if_else(cond, as_var(if_true), if_false);
}

template <typename T>
[[nodiscard]] inline Var<T> if_else(bool cond, T if_true, parameter<T> if_false) {
    return if_else(cond, if_true, as_var(if_false));
}

template <typename L, typename R>
[[nodiscard]] inline auto autodiff_min(const L& lhs, const R& rhs) {
    return if_else(primal_less(lhs, rhs), lhs, rhs);
}

template <typename L, typename R>
[[nodiscard]] inline auto autodiff_max(const L& lhs, const R& rhs) {
    return if_else(primal_greater(lhs, rhs), lhs, rhs);
}

template <typename V, typename L, typename U>
[[nodiscard]] inline auto autodiff_clamp(const V& value, const L& lower, const U& upper) {
    return autodiff_min(autodiff_max(value, lower), upper);
}

// ---------------------------------------------------------------------------
// Arithmetic operators
// ---------------------------------------------------------------------------

template <typename T>
inline Var<T> operator-(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = -u.value(), .left_idx = u.idx, .op = OpType::Neg });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_pow(Var<T> a, Var<T> b) {
    assert(a.tape == b.tape);
    Tape<T>* t = a.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_pow(a.value(), b.value()),
        .left_idx = a.idx, .right_idx = b.idx, .op = OpType::Pow });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> operator-(parameter<T> u) {
    return -static_cast<Var<T>>(u);
}

template <typename T>
inline Var<T> operator+(Var<T> a, Var<T> b) {
    assert(a.tape == b.tape);
    Tape<T>* t = a.tape;
    t->nodes.push_back(Node<T>{ .val = a.value() + b.value(),
        .left_idx = a.idx, .right_idx = b.idx, .op = OpType::Add });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> operator-(Var<T> a, Var<T> b) {
    assert(a.tape == b.tape);
    Tape<T>* t = a.tape;
    t->nodes.push_back(Node<T>{ .val = a.value() - b.value(),
        .left_idx = a.idx, .right_idx = b.idx, .op = OpType::Sub });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> operator*(Var<T> a, Var<T> b) {
    assert(a.tape == b.tape);
    Tape<T>* t = a.tape;
    t->nodes.push_back(Node<T>{ .val = a.value() * b.value(),
        .left_idx = a.idx, .right_idx = b.idx, .op = OpType::Mul });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> operator/(Var<T> a, Var<T> b) {
    assert(a.tape == b.tape);
    Tape<T>* t = a.tape;
    t->nodes.push_back(Node<T>{ .val = a.value() / b.value(),
        .left_idx = a.idx, .right_idx = b.idx, .op = OpType::Div });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T> inline Var<T> operator+(parameter<T> a, Var<T> b) { return static_cast<Var<T>>(a) + b; }
template <typename T> inline Var<T> operator+(Var<T> a, parameter<T> b) { return a + static_cast<Var<T>>(b); }
template <typename T> inline Var<T> operator+(parameter<T> a, parameter<T> b) { return static_cast<Var<T>>(a) + static_cast<Var<T>>(b); }

template <typename T> inline Var<T> operator-(parameter<T> a, Var<T> b) { return static_cast<Var<T>>(a) - b; }
template <typename T> inline Var<T> operator-(Var<T> a, parameter<T> b) { return a - static_cast<Var<T>>(b); }
template <typename T> inline Var<T> operator-(parameter<T> a, parameter<T> b) { return static_cast<Var<T>>(a) - static_cast<Var<T>>(b); }

template <typename T> inline Var<T> operator*(parameter<T> a, Var<T> b) { return static_cast<Var<T>>(a) * b; }
template <typename T> inline Var<T> operator*(Var<T> a, parameter<T> b) { return a * static_cast<Var<T>>(b); }
template <typename T> inline Var<T> operator*(parameter<T> a, parameter<T> b) { return static_cast<Var<T>>(a) * static_cast<Var<T>>(b); }

template <typename T> inline Var<T> operator/(parameter<T> a, Var<T> b) { return static_cast<Var<T>>(a) / b; }
template <typename T> inline Var<T> operator/(Var<T> a, parameter<T> b) { return a / static_cast<Var<T>>(b); }
template <typename T> inline Var<T> operator/(parameter<T> a, parameter<T> b) { return static_cast<Var<T>>(a) / static_cast<Var<T>>(b); }

template <typename T> inline Var<T> autodiff_pow(parameter<T> a, Var<T> b) { return autodiff_pow(static_cast<Var<T>>(a), b); }
template <typename T> inline Var<T> autodiff_pow(Var<T> a, parameter<T> b) { return autodiff_pow(a, static_cast<Var<T>>(b)); }
template <typename T> inline Var<T> autodiff_pow(parameter<T> a, parameter<T> b) { return autodiff_pow(static_cast<Var<T>>(a), static_cast<Var<T>>(b)); }

// Compound assignments
template <typename T> inline Var<T>& operator+=(Var<T>& a, Var<T> b) { return a = a + b; }
template <typename T> inline Var<T>& operator-=(Var<T>& a, Var<T> b) { return a = a - b; }
template <typename T> inline Var<T>& operator*=(Var<T>& a, Var<T> b) { return a = a * b; }
template <typename T> inline Var<T>& operator/=(Var<T>& a, Var<T> b) { return a = a / b; }

// Scalar overloads: promote the constant to a tape node, then use Var-Var ops
template <typename T> inline Var<T> operator+(Var<T> a, T c) { return a + a.tape->constant(c); }
template <typename T> inline Var<T> operator+(T c, Var<T> a) { return a.tape->constant(c) + a; }
template <typename T> inline Var<T> operator-(Var<T> a, T c) { return a - a.tape->constant(c); }
template <typename T> inline Var<T> operator-(T c, Var<T> a) { return a.tape->constant(c) - a; }
template <typename T> inline Var<T> operator*(Var<T> a, T c) { return a * a.tape->constant(c); }
template <typename T> inline Var<T> operator*(T c, Var<T> a) { return a.tape->constant(c) * a; }
template <typename T> inline Var<T> operator/(Var<T> a, T c) { return a / a.tape->constant(c); }
template <typename T> inline Var<T> operator/(T c, Var<T> a) { return a.tape->constant(c) / a; }
template <typename T> inline Var<T> autodiff_pow(Var<T> a, T c) { return autodiff_pow(a, a.tape->constant(c)); }
template <typename T> inline Var<T> autodiff_pow(T c, Var<T> a) { return autodiff_pow(a.tape->constant(c), a); }

// ---------------------------------------------------------------------------
// Math functions — same autodiff_ prefix as Dual.h; overloads resolve by type
// ---------------------------------------------------------------------------

template <typename T>
inline Var<T> autodiff_sin(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_sin(u.value()), .left_idx = u.idx, .op = OpType::Sin });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_cos(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_cos(u.value()), .left_idx = u.idx, .op = OpType::Cos });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_tan(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_tan(u.value()), .left_idx = u.idx, .op = OpType::Tan });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_exp(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_exp(u.value()), .left_idx = u.idx, .op = OpType::Exp });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_sigmoid(Var<T> x) {
    // 1.0 / (1.0 + exp(-x))
    return 1.0 / (1.0 + autodiff_exp(-x));
}

template <typename T>
inline Var<T> autodiff_swish(Var<T> x) {
    // x * sigmoid(x)
    return x * autodiff_sigmoid(x);
}

template <typename T>
inline Var<T> autodiff_asin(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_asin(u.value()), .left_idx = u.idx, .op = OpType::Asin });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_acos(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_acos(u.value()), .left_idx = u.idx, .op = OpType::Acos });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_atan(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_atan(u.value()), .left_idx = u.idx, .op = OpType::Atan });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_atan2(Var<T> y, Var<T> x) {
    assert(y.tape == x.tape);
    Tape<T>* t = y.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_atan2(y.value(), x.value()),
        .left_idx = y.idx, .right_idx = x.idx, .op = OpType::Atan2 });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_sinh(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_sinh(u.value()), .left_idx = u.idx, .op = OpType::Sinh });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_cosh(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_cosh(u.value()), .left_idx = u.idx, .op = OpType::Cosh });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_tanh(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_tanh(u.value()), .left_idx = u.idx, .op = OpType::Tanh });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_log(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_log(u.value()), .left_idx = u.idx, .op = OpType::Log });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_sqrt(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_sqrt(u.value()), .left_idx = u.idx, .op = OpType::Sqrt });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T>
inline Var<T> autodiff_abs(Var<T> u) {
    Tape<T>* t = u.tape;
    t->nodes.push_back(Node<T>{ .val = Tape<T>::call_abs(u.value()), .left_idx = u.idx, .op = OpType::Abs });
    return Var<T>{ t->nodes.size() - 1, t };
}

template <typename T> inline Var<T> autodiff_sin(parameter<T> u) { return autodiff_sin(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_cos(parameter<T> u) { return autodiff_cos(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_tan(parameter<T> u) { return autodiff_tan(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_asin(parameter<T> u) { return autodiff_asin(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_acos(parameter<T> u) { return autodiff_acos(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_atan(parameter<T> u) { return autodiff_atan(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_sinh(parameter<T> u) { return autodiff_sinh(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_cosh(parameter<T> u) { return autodiff_cosh(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_tanh(parameter<T> u) { return autodiff_tanh(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_exp(parameter<T> u) { return autodiff_exp(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_log(parameter<T> u) { return autodiff_log(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_sqrt(parameter<T> u) { return autodiff_sqrt(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_abs(parameter<T> u) { return autodiff_abs(static_cast<Var<T>>(u)); }
template <typename T> inline Var<T> autodiff_atan2(parameter<T> y, Var<T> x) { return autodiff_atan2(static_cast<Var<T>>(y), x); }
template <typename T> inline Var<T> autodiff_atan2(Var<T> y, parameter<T> x) { return autodiff_atan2(y, static_cast<Var<T>>(x)); }
template <typename T> inline Var<T> autodiff_atan2(parameter<T> y, parameter<T> x) { return autodiff_atan2(static_cast<Var<T>>(y), static_cast<Var<T>>(x)); }

// ---------------------------------------------------------------------------
// Debug utility: dump all nodes to a stream (useful during development)
// ---------------------------------------------------------------------------

template <typename T>
inline std::ostream& print_tape(const Tape<T>& tp, std::ostream& os) {
    for (std::size_t i = 0; i < tp.nodes.size(); ++i) {
        const Node<T>& nd = tp.nodes[i];

        const char* name = [&]() -> const char* {
            switch (nd.op) {
            case OpType::Input: return "Input";
            case OpType::Parameter: return "Parameter";
            case OpType::Constant: return "Constant";
            case OpType::Add:   return "Add";
            case OpType::Sub:   return "Sub";
            case OpType::Mul:   return "Mul";
            case OpType::Div:   return "Div";
            case OpType::Pow:   return "Pow";
            case OpType::Atan2: return "Atan2";
            case OpType::Neg:   return "Neg";
            case OpType::Sin:   return "Sin";
            case OpType::Cos:   return "Cos";
            case OpType::Tan:   return "Tan";
            case OpType::Asin:  return "Asin";
            case OpType::Acos:  return "Acos";
            case OpType::Atan:  return "Atan";
            case OpType::Sinh:  return "Sinh";
            case OpType::Cosh:  return "Cosh";
            case OpType::Tanh:  return "Tanh";
            case OpType::Exp:   return "Exp";
            case OpType::Log:   return "Log";
            case OpType::Sqrt:  return "Sqrt";
            case OpType::Abs:   return "Abs";
            default:            return "???";
            }
        }();

        const bool is_unary = (nd.op == OpType::Neg || nd.op == OpType::Sin ||
                                nd.op == OpType::Cos || nd.op == OpType::Tan ||
                                nd.op == OpType::Asin || nd.op == OpType::Acos ||
                                nd.op == OpType::Atan || nd.op == OpType::Sinh ||
                                nd.op == OpType::Cosh || nd.op == OpType::Tanh ||
                                nd.op == OpType::Exp || nd.op == OpType::Log ||
                                nd.op == OpType::Sqrt || nd.op == OpType::Abs);

        os << "[" << i << "] " << name
           << "  val=" << nd.val << "  adj=" << nd.adj;

        if (nd.op != OpType::Input && nd.op != OpType::Parameter && nd.op != OpType::Constant) {
            os << "  parents=[" << nd.left_idx;
            if (!is_unary) os << ", " << nd.right_idx;
            os << "]";
        }
        os << "\n";
    }
    return os;
}

template<std::size_t N, typename T, typename Func>
    requires std::invocable<Func, std::array<Var<T>, N>>
[[nodiscard]] std::array<T, N> reverse_gradient(Func&& f, const std::array<T, N>& x) {
    Tape<T> tape;
    std::array<Var<T>, N> vars{};

    for (std::size_t i = 0; i < N; ++i) {
        vars[i] = tape.input(x[i]);
    }

    const Var<T> loss = std::invoke(std::forward<Func>(f), vars);
    tape.backward(loss);

    std::array<T, N> grad{};
    for (std::size_t i = 0; i < N; ++i) {
        grad[i] = vars[i].gradient();
    }
    return grad;
}

template<std::size_t N, DifferentiableType T, typename Func>
    requires std::invocable<Func, std::array<Var<Dual<T>>, N>>
[[nodiscard]] std::array<std::array<T, N>, N> reverse_hessian(Func&& f, const std::array<T, N>& x) {
    std::array<std::array<T, N>, N> H{};

    for (std::size_t seed_axis = 0; seed_axis < N; ++seed_axis) {
        Tape<Dual<T>> tape;
        std::array<Var<Dual<T>>, N> vars{};

        for (std::size_t i = 0; i < N; ++i) {
            vars[i] = tape.input(Dual<T>{ .val = x[i], .der = (i == seed_axis) ? T{ 1 } : T{ 0 } });
        }

        const Var<Dual<T>> loss = std::invoke(std::forward<Func>(f), vars);
        tape.backward(loss);

        for (std::size_t row = 0; row < N; ++row) {
            H[row][seed_axis] = vars[row].gradient().der;
        }
    }

    return H;
}

template<std::size_t N, std::size_t M, typename T, typename Func>
    requires std::invocable<Func, std::array<Var<T>, N>>
[[nodiscard]] std::array<std::array<T, M>, N> reverse_jacobian(Func&& f, const std::array<T, N>& x) {
    Tape<T> tape;
    const auto vars = tape.inputs(x);
    const std::array<Var<T>, M> outputs = std::invoke(std::forward<Func>(f), vars);
    return tape.template evaluate<M, N>(outputs, vars).jacobian;
}
