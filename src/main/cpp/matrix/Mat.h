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

#include <cstdio>
#include <algorithm>
#include <utility>
#include <vector>

static void mul(const double* A, const size_t rowsA, const size_t colsA,
    const double* B, const size_t colsB, double* C);


// This is just a small study on how the operator overloading of a matrix class could be designed

class Mat
{
public:
    Mat(size_t rows, size_t cols) : rows_{ rows }, cols_{ cols }, a(len()) { puts("dimensions constructor called"); }
    Mat(size_t rows, size_t cols, const std::initializer_list<double>& vals) : rows_{ rows }, cols_{ cols }, a{vals} { puts("initializer list constructor called"); }
    Mat(const Mat& o) noexcept : rows_{ o.rows_ }, cols_{ o.cols_ } { puts("copy constructor called"); a = o.a; }
    Mat(Mat&& tmp) noexcept { puts("move constructor called"); rows_ = tmp.rows_; cols_ = tmp.cols_; a = std::move(tmp.a); }
    Mat& operator=(const Mat& o) noexcept { puts("copy assignment called"); rows_ = o.rows_; cols_ = o.cols_; a = o.a; return *this; }
    Mat& operator=(Mat&& tmp) noexcept { puts("move assignment called"); if (this != &tmp) { rows_ = tmp.rows_; cols_ = tmp.cols_; a = std::move(tmp.a); } return *this; }
    Mat& operator-() noexcept { puts("unary operator- called"); const auto ii = len(); for (size_t i = 0; i < ii; ++i) { a[i] *= -1.0; } return *this; }
    Mat& operator+=(const Mat& o) noexcept { puts("op+= called"); const auto ii = len(); for (size_t i = 0; i < ii; ++i) { a[i] += o.a[i]; } return *this; }
    Mat& operator-=(const Mat& o) noexcept { puts("op-= called"); const auto ii = len(); for (size_t i = 0; i < ii; ++i) { a[i] -= o.a[i]; } return *this; }
    Mat& operator*=(const Mat& o) noexcept { puts("op*= called"); *this = *this * o; return *this; }
public:
    friend Mat operator*(const Mat& a, const Mat& b) noexcept {
        puts("friend operator* variant 1 called");
        Mat c{ a.rows_, b.cols_ };
        mul(a.a.data(), a.rows_, a.cols_, b.a.data(), b.cols_, c.a.data());
        return c;
    }
private:
    const size_t len() const noexcept { return rows_ * cols_; }
private:
    size_t rows_{0};
    size_t cols_{0};
    std::vector<double> a;
};

Mat operator-(const Mat& a, const Mat& b) noexcept {
    puts("operator- variant 1 called");
    return std::move(Mat{ a } -= b);
}
Mat operator+(const Mat& a, const Mat& b) noexcept {
    puts("operator+ variant 1 called");
    return std::move(Mat{ a } += b);
}
Mat operator-(const Mat& a, Mat&& tmpB) noexcept {
    puts("operator- variant 2 called");
    return std::move(-tmpB += a);
}
Mat operator+(const Mat& a, Mat&& tmpB) noexcept {
    puts("operator+ variant 2 called");
    return std::move(tmpB += a);
}
Mat operator-(Mat&& tmpA, const Mat& b) noexcept {
    puts("operator- variant 3 called");
    return std::move(tmpA -= b);
}
Mat operator+(Mat&& tmpA, const Mat& b) noexcept {
    puts("operator+ variant 3 called");
    return std::move(tmpA += b);
}
Mat operator-(Mat&& tmpA, Mat&& tmpB) noexcept {
    puts("operator- variant 4 called");
    return std::move(tmpA -= tmpB);
}
Mat operator+(Mat&& tmpA, Mat&& tmpB) noexcept {
    puts("operator+ variant 4 called");
    return std::move(tmpA += tmpB);
}
Mat operator*(const Mat& a, Mat&& tmpB) noexcept {
    puts("operator* variant 2 called");
    return std::move(Mat{ a } * tmpB);
}
Mat operator*(Mat&& tmpA, const Mat& b) noexcept {
    puts("operator* variant 3 called");
    return std::move(tmpA * b);
}
Mat operator*(Mat&& tmpA, Mat&& tmpB) noexcept {
    puts("operator* variant 4 called");
    return std::move(tmpA * tmpB);
}

static void mul(const double* A, const size_t rowsA, const size_t colsA,
    const double* B, const size_t colsB, double* C) {

    for (size_t i = 0; i < rowsA; ++i) {
        for (size_t j = 0; j < colsB; ++j) {
            double sum = 0.0;
            for (size_t k = 0; k < colsA; ++k) {
                sum += A[i * colsA + k] * B[k * colsB + j];
            }
            C[i * colsB + j] = sum;
        }
    }
}
