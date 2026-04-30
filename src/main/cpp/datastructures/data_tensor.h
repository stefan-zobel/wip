#pragma once

#include <concepts>
#include <vector>
#include <span>
#include <stdexcept>
#include <algorithm> // for std::fill
#include "strong_types.h"

namespace fk {

    // ========================================================================
    // DataTensor2D
    // A type-safe, strict 2D Tensor (Matrix) that internally wraps a flat 
    // std::vector using private inheritance.
    // ========================================================================
    template <std::default_initializable T = fk::f32>
    class DataTensor2D final : private std::vector<T> {
        using Base = std::vector<T>;

    public:
        using value_type = typename Base::value_type;
        using size_type = typename Base::size_type;
        using reference = typename Base::reference;
        using const_reference = typename Base::const_reference;
        using pointer = typename Base::pointer;
        using const_pointer = typename Base::const_pointer;

        using iterator = typename Base::iterator;
        using const_iterator = typename Base::const_iterator;

        // Creates an initialized tensor of dimension rows x cols
        DataTensor2D(size_type rows, size_type cols, T initial_value = T{})
            : Base(rows * cols, initial_value), m_rows(rows), m_cols(cols) {}

        // Construct from an external flat container/span if dimensions match
        DataTensor2D(size_type rows, size_type cols, std::span<const T> external_data)
            : Base(external_data.begin(), external_data.end()), m_rows(rows), m_cols(cols) {
            if (m_rows * m_cols != external_data.size()) {
                throw std::invalid_argument("DataTensor2D: External span size does not match rows * cols layout.");
            }
        }

        // --- Geometry API ---

        size_type rows() const noexcept { return m_rows; }
        size_type cols() const noexcept { return m_cols; }

        using Base::size;     // Exposes the total linear size (rows * cols)
        using Base::empty;
        using Base::data;     // Critical for C-APIs, OpenGL or AVX/SIMD instructions

        // --- Data Access API ---

        // Unchecked fast 2D access using operator()
        // (Note: C++23 introduces multidimensional operator[row, col], for C++20, operator() is still the standard)
        constexpr reference operator()(size_type row, size_type col) noexcept {
            return Base::operator[](row * m_cols + col);
        }

        constexpr const_reference operator()(size_type row, size_type col) const noexcept {
            return Base::operator[](row * m_cols + col);
        }

        // Strict boundary checked access
        constexpr reference at(size_type row, size_type col) {
            if (row >= m_rows || col >= m_cols) {
                throw std::out_of_range("DataTensor2D out of bounds access.");
            }
            return Base::operator[](row * m_cols + col);
        }

        constexpr const_reference at(size_type row, size_type col) const {
            if (row >= m_rows || col >= m_cols) {
                throw std::out_of_range("DataTensor2D out of bounds access.");
            }
            return Base::operator[](row * m_cols + col);
        }

        // --- Iterators & Spans ---

        // Expose iterators so std::ranges, raw loops or algorithms can process the tensor linearly
        using Base::begin;
        using Base::end;
        using Base::cbegin;
        using Base::cend;

        // Implicit conversions to linear unshaped spans
        constexpr operator std::span<T>() noexcept { 
            return std::span<T>(data(), size()); 
        }
        constexpr operator std::span<const T>() const noexcept { 
            return std::span<const T>(data(), size()); 
        }

        // Retrieve a specific row as an immutable/mutable span (O(1) complexity)
        // Memory is contiguous per row, making this highly suitable for SIMD.
        constexpr std::span<T> row_span(size_type row) noexcept {
            return std::span<T>(data() + (row * m_cols), m_cols);
        }

        constexpr std::span<const T> row_span(size_type row) const noexcept {
            return std::span<const T>(data() + (row * m_cols), m_cols);
        }

        // --- Mutator API ---

        // Fills the entire tensor with a specific value instantly
        void fill(const T& value) noexcept {
            std::fill(begin(), end(), value);
        }

        // Resizes the underlying buffer and establishes a completely new geometric layout.
        // Drops existing values and resets them to the desired clear_value.
        void resize_and_clear(size_type new_rows, size_type new_cols, T clear_value = T{}) {
            m_rows = new_rows;
            m_cols = new_cols;
            Base::assign(m_rows * m_cols, clear_value);
        }

    private:
        size_type m_rows = 0;
        size_type m_cols = 0;
    };


    // ========================================================================
    // DataTensor3D
    // A strict 3D Volume Tensor (Depth x Rows x Cols). Perfect for Voxel 
    // engines, MRI image data, or 3-Dimensional Physics grids.
    // ========================================================================
    template <std::default_initializable T = fk::f32>
    class DataTensor3D final : private std::vector<T> {
        using Base = std::vector<T>;

    public:
        using value_type = typename Base::value_type;
        using size_type = typename Base::size_type;
        using reference = typename Base::reference;
        using const_reference = typename Base::const_reference;
        using pointer = typename Base::pointer;
        using const_pointer = typename Base::const_pointer;

        using iterator = typename Base::iterator;
        using const_iterator = typename Base::const_iterator;

        DataTensor3D(size_type depth, size_type rows, size_type cols, T initial_value = T{})
            : Base(depth * rows * cols, initial_value), m_depth(depth), m_rows(rows), m_cols(cols) {}

        DataTensor3D(size_type depth, size_type rows, size_type cols, std::span<const T> external_data)
            : Base(external_data.begin(), external_data.end()), m_depth(depth), m_rows(rows), m_cols(cols) {
            if (m_depth * m_rows * m_cols != external_data.size()) {
                throw std::invalid_argument("DataTensor3D: External span size does not match depth * rows * cols layout.");
            }
        }

        // --- Geometry API ---

        size_type depth() const noexcept { return m_depth; }
        size_type rows() const noexcept { return m_rows; }
        size_type cols() const noexcept { return m_cols; }

        using Base::size;     // Exposes the total linear size (depth * rows * cols)
        using Base::empty;
        using Base::data;     

        // --- Data Access API ---

        // Unchecked fast 3D access using operator()
        // Standard C-Style Row-Major 3D unrolling
        constexpr reference operator()(size_type z, size_type y, size_type x) noexcept {
            return Base::operator[]((z * m_rows * m_cols) + (y * m_cols) + x);
        }

        constexpr const_reference operator()(size_type z, size_type y, size_type x) const noexcept {
            return Base::operator[]((z * m_rows * m_cols) + (y * m_cols) + x);
        }

        // Strict boundary checked access
        constexpr reference at(size_type z, size_type y, size_type x) {
            if (z >= m_depth || y >= m_rows || x >= m_cols) throw std::out_of_range("DataTensor3D out of bounds access.");
            return Base::operator[]((z * m_rows * m_cols) + (y * m_cols) + x);
        }

        constexpr const_reference at(size_type z, size_type y, size_type x) const {
            if (z >= m_depth || y >= m_rows || x >= m_cols) throw std::out_of_range("DataTensor3D out of bounds access.");
            return Base::operator[]((z * m_rows * m_cols) + (y * m_cols) + x);
        }

        // --- Iterators & Spans ---
        using Base::begin;
        using Base::end;
        using Base::cbegin;
        using Base::cend;

        constexpr operator std::span<T>() noexcept { return std::span<T>(data(), size()); }
        constexpr operator std::span<const T>() const noexcept { return std::span<const T>(data(), size()); }

        // --- Slicing API ---

        // Retrieve an entire 2D slice (sheet) along the Z-axis (depth) as a 1D span.
        // Useful if you need to pass a single layer of the 3D volume to a 2D processing function.
        constexpr std::span<T> slice_span(size_type z) noexcept {
            return std::span<T>(data() + (z * m_rows * m_cols), m_rows * m_cols);
        }

        constexpr std::span<const T> slice_span(size_type z) const noexcept {
            return std::span<const T>(data() + (z * m_rows * m_cols), m_rows * m_cols);
        }

        // --- Mutator API ---

        void fill(const T& value) noexcept {
            std::fill(begin(), end(), value);
        }

        void resize_and_clear(size_type nw_depth, size_type nw_rows, size_type nw_cols, T clear_value = T{}) {
            m_depth = nw_depth;
            m_rows = nw_rows;
            m_cols = nw_cols;
            Base::assign(m_depth * m_rows * m_cols, clear_value);
        }

    private:
        size_type m_depth = 0;
        size_type m_rows = 0;
        size_type m_cols = 0;
    };

} // namespace fk
