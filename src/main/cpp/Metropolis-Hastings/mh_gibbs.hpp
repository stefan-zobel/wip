#pragma once

// ============================================================================
// Generic Gibbs Sampler
//
// The Gibbs sampler is a Metropolis-Hastings special case where acceptance
// is always 1 - each dimension is drawn exactly from its full conditional
// p(xi | x-i).  One step = one full systematic sweep over all dimensions.
//
// Use Gibbs when:
//   + Full conditionals are analytically available (conjugate priors, Ising).
//   - Not applicable for black-box targets (use MH/MALA instead).
//   - Slow mixing when dimensions are highly correlated.
// ============================================================================

#include "mh_concepts.hpp"
#include "mh_sampler.hpp"

namespace mh {

// ---------------------------------------------------------------------------
// ConditionalSampler concept
//
// Required expressions:
//   cond.sample_conditional(state, dim, rng)  ->  void   (modifies state[dim])
//   cond.num_dimensions(state)                ->  std::size_t
// ---------------------------------------------------------------------------
template <typename T, typename State>
concept ConditionalSampler =
    StateType<State> &&
    requires(T cond, State& s, const State& cs,
             std::size_t dim, std::mt19937_64& rng) {
        { cond.sample_conditional(s, dim, rng) } -> std::same_as<void>;
        { cond.num_dimensions(cs)              } -> std::convertible_to<std::size_t>;
    };

// ---------------------------------------------------------------------------
// GibbsChain  -  single chain, one step = full sweep over all dimensions.
// ---------------------------------------------------------------------------
template <StateType State, ConditionalSampler<State> Conditionals>
class GibbsChain {
public:
    GibbsChain(State initial, Conditionals cond, std::uint64_t seed)
        : current_(std::move(initial))
        , cond_   (std::move(cond))
        , rng_    (seed)
    {}

    const State& step() {
        const std::size_t dims = cond_.num_dimensions(current_);
        for (std::size_t d = 0; d < dims; ++d)
            cond_.sample_conditional(current_, d, rng_);
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

    [[nodiscard]] const State& current_state() const noexcept { return current_; }

private:
    State           current_;
    Conditionals    cond_;
    std::mt19937_64 rng_;
};

// ---------------------------------------------------------------------------
// ParallelGibbsSampler  -  num_chains independent chains via std::jthread.
// ---------------------------------------------------------------------------
template <StateType State, ConditionalSampler<State> Conditionals>
class ParallelGibbsSampler {
public:
    using Results = std::vector<std::vector<State>>;

    ParallelGibbsSampler(State initial, Conditionals cond, SamplerConfig cfg = {})
        : initial_(std::move(initial))
        , cond_   (std::move(cond))
        , cfg_    (cfg)
    {}

    [[nodiscard]] Results run() {
        Results results(cfg_.num_chains);
        {
            std::vector<std::jthread> threads;
            threads.reserve(cfg_.num_chains);
            for (std::size_t c = 0; c < cfg_.num_chains; ++c) {
                threads.emplace_back([&, c](std::stop_token) {
                    GibbsChain<State, Conditionals> chain(
                        initial_, cond_, cfg_.base_seed + c);
                    results[c] = chain.run(cfg_);
                });
            }
        } // jthreads join here (RAII)
        return results;
    }

private:
    State         initial_;
    Conditionals  cond_;
    SamplerConfig cfg_;
};

} // namespace mh
