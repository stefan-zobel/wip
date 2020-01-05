/*
 * Copyright 2020 Stefan Zobel
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

/**
 * Some basic {@link MatrixD} operations expressed such that the operations'
 * resulting {@code MatrixD} doesn't have to be supplied as an additional
 * parameter.
 */
public interface DMatrixBasicOps {

//  MatrixD transpose();

    MatrixD times(MatrixD B);

//  MatrixD plus(MatrixD B);

//  MatrixD minus(MatrixD B);
}
