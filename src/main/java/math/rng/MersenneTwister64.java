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
 * ====================================================
 * This is a port from the C code at
 * http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/VERSIONS/C-LANG/mt19937-64.c
 * .
 * 
 *  Copyright (C) 2004, Makoto Matsumoto and Takuji Nishimura,
 *  All rights reserved.                          
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions
 *  are met:
 *
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 *    3. The names of its contributors may not be used to endorse or promote 
 *       products derived from this software without specific prior written 
 *       permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================
 */
package math.rng;

import java.security.SecureRandom;

/**
 * 64-bit Mersenne Twister. This generator has a period of
 * 2<sup>19937</sup>&nbsp;&minus;&nbsp;1. It is about 30% slower than
 * {@link java.util.concurrent.ThreadLocalRandom} and more than twice as fast as
 * {@link java.util.Random}. Mersenne Twister (mostly in its 32-bit incarnation)
 * is the <i>de facto</i> standard PRNG for scientific applications.
 * <p>
 * The recursion is similar to the 32-bit Mersenne Twister but different, so the
 * output is totally different from the 32-bit version.
 * </p>
 * <p>
 * This is a port of the <a href=
 * "http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/VERSIONS/C-LANG/mt19937-64.c"
 * > "mt19937-64.c" C code (2004/9/29 version).</a>.
 * </p>
 * See: <a href="http://www.math.sci.hiroshima-u.ac.jp/~m-mat/MT/emt.html"> The
 * Mersenne Twister Home Page </a>
 */
public class MersenneTwister64 extends AbstractRng64 {

    private static final int NN = 312;

    private static final int MM = 156;

    private static final long MATRIX_A = 0xb5026f5aa96619e9L;

    /* Most significant 33 bits */
    private static final long UM = 0xffffffff80000000L;

    /* Least significant 31 bits */
    private static final long LM = 0x7fffffffL;

    private static final long[] mag01 = { 0L, MATRIX_A };

    private static final MersenneTwister64 defaultRng = createDefaultRng();

    /* The array for the state vector */
    private long[] mt = new long[NN];

    /* mti == NN + 1 means mt[] is not initialized */
    private int mti = NN + 1;

    public MersenneTwister64() {
        setSeed(MarsagliaXOR64.getDefault().nextLong());
    }

    public MersenneTwister64(final long seed) {
        setSeed(seed == 0L ? -1 : seed);
    }

    public MersenneTwister64(final long[] seedArray) {
        setSeed(seedArray);
    }

    /*
     * Initialize the RNG with a 64-bit seed.
     */
    private void setSeed(final long seed) {
        mt[0] = seed;
        for (mti = 1; mti < NN; mti++) {
            mt[mti] = (6364136223846793005L * (mt[mti - 1] ^ (mt[mti - 1] >>> 62)) + mti);
        }
    }

    /*
     * Initialize the RNG by an array.
     */
    private void setSeed(final long[] seedArray) {
        setSeed(19650218L);
        int i = 1;
        int j = 0;
        int k = (NN > seedArray.length ? NN : seedArray.length);
        for (; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 62)) * 3935559000370003845L))
                    + seedArray[j] + j; /* non linear */
            i++;
            j++;
            if (i >= NN) {
                mt[0] = mt[NN - 1];
                i = 1;
            }
            if (j >= seedArray.length) {
                j = 0;
            }
        }
        for (k = NN - 1; k != 0; k--) {
            mt[i] = (mt[i] ^ ((mt[i - 1] ^ (mt[i - 1] >>> 62)) * 2862933555777941757L))
                    - i; /* non linear */
            i++;
            if (i >= NN) {
                mt[0] = mt[NN - 1];
                i = 1;
            }
        }

        mt[0] = 1L << 63; /* MSB is 1; assuring non-zero initial array */
    }

    public final long nextLong() {
        long x;
        if (mti >= NN) { /* generate NN words at one time */
            int i;
            for (i = 0; i < NN - MM; i++) {
                x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + MM] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];
            }
            for (; i < NN - 1; i++) {
                x = (mt[i] & UM) | (mt[i + 1] & LM);
                mt[i] = mt[i + (MM - NN)] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];
            }
            x = (mt[NN - 1] & UM) | (mt[0] & LM);
            mt[NN - 1] = mt[MM - 1] ^ (x >>> 1) ^ mag01[(int) (x & 1L)];

            mti = 0;
        }

        x = mt[mti++];

        x ^= (x >>> 29) & 0x5555555555555555L;
        x ^= (x << 17) & 0x71D67FFFEDA60000L;
        x ^= (x << 37) & 0xFFF7EEE000000000L;
        x ^= (x >>> 43);

        return x;
    }

    public static MersenneTwister64 getDefault() {
        return defaultRng;
    }

    private static MersenneTwister64 createDefaultRng() {
        final long[] randSeed = new long[NN];
        try {
            final SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            for (int i = 0; i < NN; ++i) {
                randSeed[i] = sr.nextLong();
            }
        } catch (Exception e) {
            final MarsagliaXOR64 rng = MarsagliaXOR64.getDefault();
            for (int i = 0; i < NN; ++i) {
                randSeed[i] = rng.nextLong();
            }
        }
        return new MersenneTwister64(randSeed);
    }
}
