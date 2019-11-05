/*
 * Copyright 2019 Stefan Zobel
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
package net.incubator.banach.matrix;

public interface Dimensions {

    boolean isScalar();

    boolean isColumnVector();

    boolean isRowVector();

    boolean isSquareMatrix();

    int numColumns();

    int numRows();

    void checkIndex(int row, int col);

    /**
     * {@code (rFrom, cFrom)} upper left corner, {@code (rTo, cTo)} lower right
     * corner. All indexes must be valid and the submatrix must contain at least
     * one element.
     * 
     * @param rFrom
     *            {@code <= rTo}
     * @param cFrom
     *            {@code <= cTo}
     * @param rTo
     *            {@code >= rFrom}
     * @param cTo
     *            {@code >= rTo}
     */
    void checkSubmatrixIndexes(int rFrom, int cFrom, int rTo, int cTo);
}
