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

/**
 * 64-bit Xorshift pseudo random generator by George Marsaglia (2003).
 * <p/>
 * A faster and statistically better pseudo RNG than {@link java.util.Random} or
 * {@link java.util.concurrent.ThreadLocalRandom}. This generator has a period
 * of 2<sup>64</sup>&nbsp;&minus;&nbsp;1.
 */
public class MarsagliaXOR64 extends AbstractRng64 {

    private static final MarsagliaXOR64 defaultRng = new MarsagliaXOR64();

    private long seed;

    public MarsagliaXOR64() {
        long seed = 0L;
        do {
            seed = Seed.seed();
        } while (seed == 0L);
        this.seed = seed;
        recover();
    }

    public MarsagliaXOR64(final long seed) {
        this.seed = (seed == 0L) ? -1L : seed;
        recover();
    }

    public MarsagliaXOR64(final long[] seed) {
        MersenneTwister64 seeder = new MersenneTwister64(seed);
        long seed_ = seeder.nextLong();
        this.seed = (seed_ == 0L) ? -1L : seed_;
        recover();
    }

    public final long nextLong() {
        long x = seed;
        x ^= (x << 21);
        x ^= (x >>> 35);
        x ^= (x << 4);
        seed = x;
        return x;
    }

    public static MarsagliaXOR64 getDefault() {
        return defaultRng;
    }

    /*
     * Protect against poor seeds.
     */
    private void recover() {
        long l = 0L;
        for (int i = 0; i < 10; ++i) {
            l = nextLong();
        }
        if (l == 0L) {
            // this cannot happen
            throw new AssertionError("0L");
        }
    }
}
