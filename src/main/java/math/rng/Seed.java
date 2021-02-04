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

public final class Seed {

    // from java.util.Random
    private static long seedUniquifier = 0x1ed8b55fac9decL;

    private static long lastSeed = pseudoRandomSeed();

    // from java.util.Random
    private static long nextSeedUniquifier() {
        // Pierre L'Ecuyer: "Tables of Linear Congruential Generators
        // of Different Sizes and Good Lattice Structure"
        seedUniquifier *= 0x106689d45497fdb5L;
        return seedUniquifier;
    }

    /*
     * Returns a reasonably good (pseudo) random seed
     */
    private static long pseudoRandomSeed() {
        long seed = nextSeedUniquifier() ^ System.nanoTime();

        // apply Austin Appleby's fmix64() hash
        seed ^= seed >>> 33;
        seed *= 0xff51afd7ed558ccdL;
        seed ^= seed >>> 33;
        seed *= 0xc4ceb9fe1a85ec53L;
        seed ^= seed >>> 33;

        return seed;
    }

    /**
     * Returns a reasonably good long random seed.
     * 
     * @return a long random seed.
     */
    public static synchronized long seed() {
        long seed = pseudoRandomSeed();
        while (seed == lastSeed || seed == 0L) {
            seed = pseudoRandomSeed();
        }
        lastSeed = seed;
        return seed;
    }

    private Seed() {
    }
}
