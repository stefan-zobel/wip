/*
 * Copyright 2023 Stefan Zobel
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
#pragma once

#include <cassert>
#include <cstring> // std::memcpy
#include <algorithm> // std::swap

// A 2-dimensional double array whose dimensions need not be known at compile-time.
// The array is allocated on the heap and has contiguous storage.
class Dim2Arr
{
public:
    explicit Dim2Arr(size_t rows, size_t cols)
        : rows_(check(rows)), cols_(check(cols)), a(nullptr)
    {
        allocate();
        std::memset(a[0], 0, rows_ * cols_ * sizeof(double));
    }

    explicit Dim2Arr()
        : rows_(0), cols_(0), a(nullptr)
    {
    }

    explicit Dim2Arr(size_t rows, size_t cols, const std::initializer_list<double>& vals)
        : Dim2Arr()
    {
        assert(rows > 0 and cols > 0 and vals.size() == rows * cols);
        rows_ = rows;
        cols_ = cols;
        allocate();
        const double* v = std::data(vals);
        for (size_t i = 0; i < rows_; ++i) {
            for (size_t j = 0; j < cols_; ++j) {
                a[i][j] = *v;
                ++v;
            }
        }
    }

    Dim2Arr(const Dim2Arr& other)
        : Dim2Arr()
    {
        copy(other);
    }

    Dim2Arr& operator=(const Dim2Arr& other) {
        if (this != &other) {
            clear();
            copy(other);
        }
        return *this;
    }

    void swap(Dim2Arr& src) noexcept {
        std::swap(rows_, src.rows_);
        std::swap(cols_, src.cols_);
        std::swap(a, src.a);
    }

    Dim2Arr(Dim2Arr&& moving) noexcept
        : Dim2Arr()
    {
        moving.swap(*this);
    }

    Dim2Arr& operator=(Dim2Arr&& moving) noexcept {
        moving.swap(*this);
        return *this;
    }

    ~Dim2Arr() {
        clear();
    }

    // C++ 23
//  double operator[](size_t r, size_t c) const noexcept {
//      assert(r < rows_ and c < cols_);
//      return a[r][c];
//  }

    // C++ 23
//  double& operator[](size_t r, size_t c) noexcept {
//      assert(r < rows_ and c < cols_);
//      return a[r][c];
//  }

    double operator() (size_t r, size_t c) const noexcept {
        assert(r < rows_ and c < cols_);
        return a[r][c];
    }

    double& operator() (size_t r, size_t c) noexcept {
        assert(r < rows_ and c < cols_);
        return a[r][c];
    }

    size_t rows() const noexcept {
        return rows_;
    }

    size_t cols() const noexcept {
        return cols_;
    }

private:
    constexpr static size_t check(size_t x) {
        return x < 1 ? 1 : x;
    }

    void clear() {
        if (rows_ > 0) {
            delete[] a[0];
            delete[] a;
            a = nullptr;
            rows_ = 0;
            cols_ = 0;
        }
    }

    void copy(const Dim2Arr& other) {
        if (other.rows_ > 0) {
            double** tmp = new double*[other.rows_];
            std::memcpy(tmp[0], other.a[0], other.rows_ * other.cols_ * sizeof(double));
            for (size_t i = 1; i < other.rows_; ++i) {
                tmp[i] = tmp[i - 1] + other.cols_;
            }
            a = tmp;
            rows_ = other.rows_;
            cols_ = other.cols_;
        }
    }

    void allocate() {
        a = new double*[rows_];
        a[0] = new double[rows_ * cols_];
        for (size_t i = 1; i < rows_; ++i) {
            a[i] = a[i - 1] + cols_;
        }
    }

private:
    size_t rows_;
    size_t cols_;
    double** a;
};
