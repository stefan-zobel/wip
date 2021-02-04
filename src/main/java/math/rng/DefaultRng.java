/*
 * Copyright 2013 Stefan Zobel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package math.rng;

public final class DefaultRng {

    public static PseudoRandom getGlobalPseudoRandom() {
        return MersenneTwister64.getDefault();
    }

    public static PseudoRandom newPseudoRandom() {
        return new MersenneTwister64();
    }

    public static PseudoRandom newPseudoRandom(final long seed) {
        return new MersenneTwister64(seed);
    }

    public static PseudoRandom newPseudoRandom(final long[] seed) {
        return new MersenneTwister64(seed);
    }

    public static PseudoRandom[] newIndepPseudoRandoms(final int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count <= 0 : " + count);
        }
        if (count == 1) {
            return new PseudoRandom[] { newPseudoRandom() };
        }
        final int NN = 312;
        final long[] seed = new long[NN];
        getGlobalPseudoRandom().nextLongs(seed);
        final PseudoRandom[] multiplePrng = new PseudoRandom[count];
        for (int i = 0; i < multiplePrng.length; ++i) {
            final PseudoRandom prng = newPseudoRandom(seed);
            // now change the seed
            reseed(NN, seed, prng);
            multiplePrng[i] = prng;
        }
        return multiplePrng;
    }

    public static PseudoRandom newIndepPseudoRandom(
            final PseudoRandom prng) {
        final int NN = 312;
        final long[] seed = new long[NN];
        reseed(NN, seed, prng);
        return newPseudoRandom(seed);
    }

    private static void reseed(final int len, final long[] seed,
            final PseudoRandom prng) {
        prng.nextLongs(seed);
        int j = 0;
        while (j < seed.length && seed[j] == 0L) {
            ++j;
        }
        final long nucleus = (j < seed.length) ? seed[j] : -1L;
        final long[] half_seed = new long[len / 2];
        new MarsagliaXOR64(nucleus ^ Seed.seed()).nextLongs(half_seed);
        System.arraycopy(half_seed, 0, seed, 0, half_seed.length);
    }

    private DefaultRng() {
        throw new AssertionError();
    }
}
