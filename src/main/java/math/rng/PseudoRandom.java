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

public interface PseudoRandom {

    long nextLong();

    // TODO: explain: is this [0, 1] or [0, 1)?
    double nextDouble();

    double nextGaussian();

    float nextFloat();

    int nextInt();

    void nextBytes(byte[] bytes);

    boolean nextBoolean();

    long nextLong(long n);

    int nextInt(int n);

    int nextInt(int min, int max);

    long nextLong(long min, long max);

    int next(int bits);

    void nextLongs(long[] longs);
}
