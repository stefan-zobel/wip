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
 * Abstract base class for 64-bit pseudo RNGs.
 * <p>
 * Derived classes need to supply an implementation of {@link #nextLong()}.
 * </p>
 * This base class implementation is efficient for {@link #nextDouble()},
 * {@link #nextGaussian()} and {@link #nextBytes(byte[])} but somehow wasteful
 * for the other methods because it dissipates valuable random bits piled up in
 * the call to {@link #nextLong()} whenever less than {@code 33} random bits are
 * needed for the result type.
 * <p/>
 */
public abstract class AbstractRng64 implements PseudoRandom {

    protected static final double DOUBLE_NORM = 1.0 / (1L << 53);
    protected static final float FLOAT_NORM = 1.0F / (1 << 24);

    /** cache for the next gaussian */
    protected double nextGaussian = Double.NaN;

    @Override
    public abstract long nextLong();

    // TODO: explain: is this [0, 1] or [0, 1)? { -> rather [0, 1)}
    @Override
    public double nextDouble() {
        return (nextLong() >>> 11) * DOUBLE_NORM;
    }

    @Override
    public final double nextGaussian() {
        final double rndVal;
        if (Double.isNaN(nextGaussian)) {
            // Marsaglia's polar method
            double u1, u2, q;
            do {
                u1 = 2.0 * nextDouble() - 1.0; // between -1 and 1
                u2 = 2.0 * nextDouble() - 1.0; // between -1 and 1
                q = u1 * u1 + u2 * u2;
            } while (q >= 1 || q == 0.0);
            final double p = Math.sqrt(-2.0 * Math.log(q) / q);
            rndVal = u1 * p;
            nextGaussian = u2 * p;
        } else {
            rndVal = nextGaussian;
            nextGaussian = Double.NaN;
        }
        return rndVal;
    }

    @Override
    public float nextFloat() {
        return (nextLong() >>> 40) * FLOAT_NORM;
    }

    @Override
    public int nextInt() {
        return (int) (nextLong() >> 32);
    }

    @Override
    public void nextBytes(final byte[] bytes) {
        // awful code (adapted from java.util.Random)
        for (int i = 0, len = bytes.length; i < len; /**/) {
            for (long rnd = nextLong(), n = Math.min(len - i, Long.SIZE
                    / Byte.SIZE); n-- > 0; rnd >>= Byte.SIZE) {
                bytes[i++] = (byte) rnd;
            }
        }
    }

    @Override
    public void nextLongs(final long[] longs) {
        for (int i = 0; i < longs.length; ++i) {
            longs[i] = nextLong();
        }
    }

    @Override
    public boolean nextBoolean() {
        return (nextLong() >> 63) != 0L;
    }

    @Override
    public long nextLong(final long n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }
        while (true) {
            final long x = nextLong() >>> 1;
            final long y = x % n;
            if (x - y + (n - 1) >= 0) {
                return y;
            }
        }
    }

    @Override
    public int nextInt(final int n) {
        return (int) nextLong(n);
    }

    @Override
    public int nextInt(final int min, final int max) {
        return (int) nextLong(min, max);
    }

    @Override
    public long nextLong(final long min, final long max) {
        return min + nextLong((max - min) + 1);
    }

    @Override
    public int next(final int bits) {
        return (int) (nextLong() >>> (64 - bits));
    }
}
