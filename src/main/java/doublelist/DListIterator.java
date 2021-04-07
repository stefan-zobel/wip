/*
 * Copyright 2021 Stefan Zobel
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
package doublelist;

import math.fun.DForEachIterator;

public interface DListIterator extends DForEachIterator {

    boolean hasPrevious();

    double previous();

    /** Note that indexes are 0-based */
    int nextIndex();

    int previousIndex();

    void set(double e);

    void add(double e);
}
