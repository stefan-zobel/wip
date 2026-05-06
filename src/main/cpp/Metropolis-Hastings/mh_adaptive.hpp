#pragma once

// ============================================================================
// Adaptive Metropolis-Hastings  (Robbins-Monro step-size adaptation)
//
// Step size is adapted ONLY during burn-in to preserve ergodicity.
// Every `window` steps the empirical acceptance rate drives an update:
//
//   eps_new = eps * exp( (n + n0)^{-kappa} * (alpha_hat - alpha*) )
//
// Optimal alpha*:  0.574 for MALA,  0.234 for high-dim random-walk MH.
// ============================================================================

#include "mh_concepts.hpp"
#include "mh_sampler.hpp"
#include <cmath>
#include <algorithm>

namespace mh {

// ---------------------------------------------------------------------------
// AdaptableProposal – extends ProposalDistribution with a mutable step size.
// ---------------------------------------------------------------------------
template <typename T>
concept AdaptableProposal = requires(T p, double s) {
    { p.set_step_size(s) } -> std::same_as<void>;
    { p.step_size()      } -> std::convertible_to<double>;
};

struct AdaptiveConfig {
    double      target_accept = 0.574;  // optimal for MALA; use 0.234 for RWM
    double      adapt_rate    = 0.65;   // Robbins-Monro exponent kappa in (0.5, 1]
    double      n0            = 10.0;   // initial offset, slows early adaptation
    std::size_t window        = 50;     // adapt once every n steps
    double      step_min      = 1e-5;
    double      step_max      = 100.0;
};

// ---------------------------------------------------------------------------
// AdaptiveSamplerChain
// ---------------------------------------------------------------------------
template <StateType State,
          TargetDistribution<State>   Target,
          ProposalDistribution<State> Proposal>
    requires AdaptableProposal<Proposal>
class AdaptiveSamplerChain {
public:
    AdaptiveSamplerChain(State initial, Target target, Proposal proposal,
                         std::uint64_t seed, AdaptiveConfig adapt_cfg = {})
        : current_  (std::move(initial))
        , target_   (std::move(target))
        , proposal_ (std::move(proposal))
        , rng_      (seed)
        , log_curr_ (target_.log_prob(current_))
        , adapt_cfg_(adapt_cfg)
    {}

    const State& step(bool adapt) {
        const State  proposed  = proposal_.propose(current_, rng_);
        const double log_prop  = target_.log_prob(proposed);
        const double log_q_fwd = proposal_.log_prob(proposed, current_);
        const double log_q_rev = proposal_.log_prob(current_,  proposed);
        const double log_alpha = log_prop + log_q_rev - log_curr_ - log_q_fwd;

        const bool accepted = std::log(uniform_(rng_)) < log_alpha;
        if (accepted) { current_ = proposed; log_curr_ = log_prop; ++accepted_; }
        ++total_;

        if (adapt) {
            ++win_steps_;
            if (accepted) ++win_accepts_;
            if (win_steps_ >= adapt_cfg_.window) {
                const double rate  = static_cast<double>(win_accepts_)
                                   / static_cast<double>(win_steps_);
                const double gamma = std::pow(
                    static_cast<double>(++adapt_n_) + adapt_cfg_.n0,
                    -adapt_cfg_.adapt_rate);
                proposal_.set_step_size(std::clamp(
                    proposal_.step_size() * std::exp(gamma * (rate - adapt_cfg_.target_accept)),
                    adapt_cfg_.step_min, adapt_cfg_.step_max));
                win_steps_ = 0; win_accepts_ = 0;
            }
        }
        return current_;
    }

    [[nodiscard]] std::vector<State> run(const SamplerConfig& cfg) {
        for (std::size_t i = 0; i < cfg.burn_in; ++i) step(true);
        accepted_ = 0; total_ = 0;  // report only post-burn-in rate

        std::vector<State> samples;
        samples.reserve(cfg.num_samples);
        std::size_t iter = 0;
        while (samples.size() < cfg.num_samples) {
            step(false);
            if (++iter % cfg.thinning == 0)
                samples.push_back(current_);
        }
        return samples;
    }

    [[nodiscard]] double acceptance_rate()    const noexcept {
        return total_ ? static_cast<double>(accepted_) / static_cast<double>(total_) : 0.0;
    }
    [[nodiscard]] double       final_step_size() const noexcept { return proposal_.step_size(); }
    [[nodiscard]] const State& current_state()   const noexcept { return current_; }

private:
    State           current_;
    Target          target_;
    Proposal        proposal_;
    std::mt19937_64 rng_;
    double          log_curr_;
    AdaptiveConfig  adapt_cfg_;
    std::size_t     accepted_    = 0;
    std::size_t     total_       = 0;
    std::size_t     adapt_n_     = 0;
    std::size_t     win_steps_   = 0;
    std::size_t     win_accepts_ = 0;
    std::uniform_real_distribution<double> uniform_{ 0.0, 1.0 };
};

// ---------------------------------------------------------------------------
// ParallelAdaptiveSampler – num_chains adaptive chains via std::jthread.
// Each chain adapts its step size independently during its own burn-in.
// ---------------------------------------------------------------------------
template <StateType State,
          TargetDistribution<State>   Target,
          ProposalDistribution<State> Proposal>
    requires AdaptableProposal<Proposal>
class ParallelAdaptiveSampler {
public:
    using Results = std::vector<std::vector<State>>;

    ParallelAdaptiveSampler(State initial, Target target, Proposal proposal,
                            SamplerConfig cfg = {}, AdaptiveConfig adapt_cfg = {})
        : initial_  (std::move(initial))
        , target_   (std::move(target))
        , proposal_ (std::move(proposal))
        , cfg_      (cfg)
        , adapt_cfg_(adapt_cfg)
    {}

    [[nodiscard]] Results run() {
        Results results(cfg_.num_chains);
        rates_.assign(cfg_.num_chains, 0.0);
        final_steps_.assign(cfg_.num_chains, 0.0);
        {
            std::vector<std::jthread> threads;
            threads.reserve(cfg_.num_chains);
            for (std::size_t c = 0; c < cfg_.num_chains; ++c) {
                threads.emplace_back([&, c](std::stop_token) {
                    AdaptiveSamplerChain<State, Target, Proposal> chain(
                        initial_, target_, proposal_, cfg_.base_seed + c, adapt_cfg_);
                    results[c]      = chain.run(cfg_);
                    rates_[c]       = chain.acceptance_rate();
                    final_steps_[c] = chain.final_step_size();
                });
            }
        } // jthreads join here (RAII)
        return results;
    }

    [[nodiscard]] std::span<const double> acceptance_rates() const noexcept { return rates_; }
    [[nodiscard]] std::span<const double> final_step_sizes() const noexcept { return final_steps_; }

private:
    State          initial_;
    Target         target_;
    Proposal       proposal_;
    SamplerConfig  cfg_;
    AdaptiveConfig adapt_cfg_;
    std::vector<double> rates_;
    std::vector<double> final_steps_;
};

} // namespace mh
