/*
 * Copyright 2023 - 2026 Stefan Zobel
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

#include <cstdio>
#include <algorithm>
#include <utility>
#include <vector>

#include "Avx2BlockedGemm.h"


// This is just a small study on how the operator overloading of a matrix class could be designed


// Define a thread_local memory arena for our AVX2 blocked GEMM computations
inline SimpleArena& get_thread_scratch_arena() {
    thread_local auto arena = SimpleArena::create(512 * 1024);
    return *arena;
}


class Mat
{
public:
    Mat(size_t rows, size_t cols) : rows_{ rows }, cols_{ cols }, a(len()) { puts("dimensions constructor called"); }
    Mat(size_t rows, size_t cols, const std::initializer_list<double>& vals) : rows_{ rows }, cols_{ cols }, a{vals} { puts("initializer list constructor called"); }
    Mat(const Mat& o) noexcept : rows_{ o.rows_ }, cols_{ o.cols_ } { puts("copy constructor called"); a = o.a; }
    Mat(Mat&& tmp) noexcept { puts("move constructor called"); rows_ = tmp.rows_; cols_ = tmp.cols_; a = std::move(tmp.a); tmp.rows_ = 0; tmp.cols_ = 0; }
    Mat& operator=(const Mat& o) noexcept { puts("copy assignment called"); rows_ = o.rows_; cols_ = o.cols_; a = o.a; return *this; }
    Mat& operator=(Mat&& tmp) noexcept { puts("move assignment called"); if (this != &tmp) { rows_ = tmp.rows_; cols_ = tmp.cols_; a = std::move(tmp.a); tmp.rows_ = 0; tmp.cols_ = 0; } return *this; }
    Mat operator-() const noexcept { puts("unary operator- called"); Mat result(*this); for (size_t i = 0; i < result.len(); ++i) { result.a[i] = -result.a[i]; } return result; }
    Mat& operator+=(const Mat& o) noexcept { puts("op+= called"); const auto ii = len(); for (size_t i = 0; i < ii; ++i) { a[i] += o.a[i]; } return *this; }
    Mat& operator-=(const Mat& o) noexcept { puts("op-= called"); const auto ii = len(); for (size_t i = 0; i < ii; ++i) { a[i] -= o.a[i]; } return *this; }
    Mat& operator*=(const Mat& o) noexcept { puts("op*= called"); *this = *this * o; return *this; }
public:
    friend Mat operator*(const Mat& a, const Mat& b) noexcept {
        puts("friend operator* variant 1 called (using AVX2 Blocked GEMM)");
        Mat c{ a.rows_, b.cols_ };

        MatrixView<const double> viewA{ a.a.data(), a.rows_, a.cols_, a.cols_ };
        MatrixView<const double> viewB{ b.a.data(), b.rows_, b.cols_, b.cols_ };
        MatrixView<double>       viewC{ c.a.data(), c.rows_, c.cols_, c.cols_ };

        gemm_nn_blocked_avx2<double>(get_thread_scratch_arena(), viewA, viewB, viewC);

        return c; // NRVO
    }
    friend Mat operator-(const Mat& a, Mat&& tmpB) noexcept {
        puts("operator- variant 2 (Lvalue - Rvalue) called");
        const auto ii = tmpB.len();
        // Zero-Allocation In-Place computation
        for (size_t i = 0; i < ii; ++i) {
            tmpB.a[i] = a.a[i] - tmpB.a[i];
        }
        return std::move(tmpB);
    }
private:
    size_t len() const noexcept { return rows_ * cols_; }
private:
    size_t rows_{0};
    size_t cols_{0};
    std::vector<double> a;
};

// Variant 1: Lvalue - Lvalue (Creates ONE necessary new matrix)
Mat operator-(const Mat& a, const Mat& b) noexcept {
    puts("operator- variant 1 called");
    Mat result{ a }; // 1 Copy
    result -= b;
    return result; // No std::move because of NRVO
}
Mat operator+(const Mat& a, const Mat& b) noexcept {
    puts("operator+ variant 1 called");
    Mat result{ a };
    result += b;
    return result; // NRVO (Named Return Value Optimization)
}
Mat operator+(const Mat& a, Mat&& tmpB) noexcept {
    puts("operator+ variant 2 called");
    return std::move(tmpB += a);
}
// Variant 3: Rvalue - Lvalue (Zero Allocations, re-uses tmpA)
Mat operator-(Mat&& tmpA, const Mat& b) noexcept {
    puts("operator- variant 3 called");
    return std::move(tmpA -= b);
}
Mat operator+(Mat&& tmpA, const Mat& b) noexcept {
    puts("operator+ variant 3 called");
    return std::move(tmpA += b);
}
// Variant 4: Rvalue - Rvalue (Zero Allocations, re-uses tmpA)
Mat operator-(Mat&& tmpA, Mat&& tmpB) noexcept {
    puts("operator- variant 4 called");
    return std::move(tmpA -= tmpB);
}
Mat operator+(Mat&& tmpA, Mat&& tmpB) noexcept {
    puts("operator+ variant 4 called");
    return std::move(tmpA += tmpB);
}
