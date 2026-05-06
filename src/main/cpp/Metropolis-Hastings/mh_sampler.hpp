#pragma once

#include "mh_concepts.hpp"

#include <vector>
#include <random>
#include <cmath>
#include <span>
#include <thread>
#include <stop_token>

// ============================================================================
// Generic Metropolis-Hastings sampler  (single chain + parallel multi-chain)
//
// Acceptance criterion with full Hastings correction for asymmetric proposals:
//
//   log alpha = log p(x') + log q(x | x')
//             - log p(x)  - log q(x' | x)
//
// For symmetric proposals log q(x'|x) == log q(x|x') and both terms cancel.
// ============================================================================

namespace mh {

// ---------------------------------------------------------------------------
// SamplerConfig
// ---------------------------------------------------------------------------
struct SamplerConfig {
    std::size_t   num_samples = 10'000;
    std::size_t   burn_in     =  1'000;
    std::size_t   thinning    =      1;
    std::size_t   num_chains  =      4;
    std::uint64_t base_seed   =     42;
};

// ---------------------------------------------------------------------------
// SamplerChain – single self-contained MCMC chain, owns its RNG.
// ---------------------------------------------------------------------------
template <StateType State,
          TargetDistribution<State>   Target,
          ProposalDistribution<State> Proposal>
class SamplerChain {
public:
    SamplerChain(State initial, Target target, Proposal proposal,
                 std::uint64_t seed)
        : current_  (std::move(initial))
        , target_   (std::move(target))
        , proposal_ (std::move(proposal))
        , rng_      (seed)
        , log_curr_ (target_.log_prob(current_))
    {}

    const State& step() {
        const State  proposed    = proposal_.propose(current_, rng_);
        const double log_prop    = target_.log_prob(proposed);
        const double log_q_fwd   = proposal_.log_prob(proposed, current_);
        const double log_q_rev   = proposal_.log_prob(current_,  proposed);
        const double log_alpha   = log_prop + log_q_rev - log_curr_ - log_q_fwd;

        if (std::log(uniform_(rng_)) < log_alpha) {
            current_  = proposed;
            log_curr_ = log_prop;
            ++accepted_;
        }
        ++total_;
        return current_;
    }

    [[nodiscard]] std::vector<State> run(const SamplerConfig& cfg) {
        for (std::size_t i = 0; i < cfg.burn_in; ++i) step();

        std::vector<State> samples;
        samples.reserve(cfg.num_samples);
        std::size_t iter = 0;
        while (samples.size() < cfg.num_samples) {
            step();
            if (++iter % cfg.thinning == 0)
                samples.push_back(current_);
        }
        return samples;
    }

    [[nodiscard]] double acceptance_rate() const noexcept {
        return total_ ? static_cast<double>(accepted_) / static_cast<double>(total_) : 0.0;
    }
    [[nodiscard]] const State& current_state() const noexcept { return current_; }

private:
    State           current_;
    Target          target_;
    Proposal        proposal_;
    std::mt19937_64 rng_;
    double          log_curr_;
    std::size_t     accepted_ = 0;
    std::size_t     total_    = 0;
    std::uniform_real_distribution<double> uniform_{ 0.0, 1.0 };
};

// ---------------------------------------------------------------------------
// ParallelSampler – num_chains independent chains via std::jthread.
// ---------------------------------------------------------------------------
template <StateType State,
          TargetDistribution<State>   Target,
          ProposalDistribution<State> Proposal>
class ParallelSampler {
public:
    using Results = std::vector<std::vector<State>>;

    ParallelSampler(State initial, Target target, Proposal proposal,
                    SamplerConfig cfg = {})
        : initial_  (std::move(initial))
        , target_   (std::move(target))
        , proposal_ (std::move(proposal))
        , cfg_      (cfg)
    {}

    [[nodiscard]] Results run() {
        Results results(cfg_.num_chains);
        rates_.assign(cfg_.num_chains, 0.0);
        {
            std::vector<std::jthread> threads;
            threads.reserve(cfg_.num_chains);
            for (std::size_t c = 0; c < cfg_.num_chains; ++c) {
                threads.emplace_back([&, c](std::stop_token) {
                    SamplerChain<State, Target, Proposal> chain(
                        initial_, target_, proposal_, cfg_.base_seed + c);
                    results[c] = chain.run(cfg_);
                    rates_[c]  = chain.acceptance_rate();
                });
            }
        } // jthreads join here (RAII)
        return results;
    }

    [[nodiscard]] std::span<const double> acceptance_rates() const noexcept {
        return rates_;
    }

private:
    State         initial_;
    Target        target_;
    Proposal      proposal_;
    SamplerConfig cfg_;
    std::vector<double> rates_;
};

} // namespace mh
