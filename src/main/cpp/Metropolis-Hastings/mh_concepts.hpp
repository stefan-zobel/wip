#pragma once

#include <concepts>
#include <random>

// ============================================================================
// C++20 Concepts for the generic Metropolis-Hastings sampler
// ============================================================================

namespace mh {

// A State must be copyable and default-constructible.
template <typename T>
concept StateType = std::copyable<T> && std::default_initializable<T>;

// TargetDistribution: provides log p(x) – unnormalised log-density.
template <typename T, typename State>
concept TargetDistribution =
    StateType<State> &&
    requires(const T dist, const State& s) {
        { dist.log_prob(s) } -> std::convertible_to<double>;
    };

// ProposalDistribution: proposes new states and evaluates log q(to | from).
// For symmetric proposals log q(x'|x) == log q(x|x') and the Hastings
// correction cancels automatically.  Asymmetric proposals (e.g. MALA) must
// return the correct directional log-density from log_prob().
template <typename T, typename State>
concept ProposalDistribution =
    StateType<State> &&
    requires(T prop, const State& s, std::mt19937_64& rng) {
        { prop.propose(s, rng)  } -> std::same_as<State>;
        { prop.log_prob(s, s)   } -> std::convertible_to<double>;
    };

} // namespace mh
