#pragma once

#include <vector>
#include <tuple>
#include <cstdint>
#include <cstddef>
#include <utility>
#include <stdexcept>

namespace fk {

    // ========================================================================
    // SoA Reference Proxy
    // 
    // This is the magical "phantom struct" the user interacts with. 
    // When the user calls vector[5], they don't get a real struct. They get 
    // this Proxy, which holds a bunch of references pointing directly 
    // into the wildly separated internal arrays.
    // ========================================================================
    template <typename... Types>
    class SoARef {
    private:
        // A tuple holding purely references (lvalues) to the scattered array elements.
        std::tuple<Types&...> m_refs;

    public:
        explicit SoARef(Types&... args) : m_refs(args...) {}

        // Access via explicit index, e.g. proxy.get<0>() to get the X coordinate
        template <size_t N>
        decltype(auto) get() noexcept {
            return std::get<N>(m_refs);
        }

        template <size_t N>
        decltype(auto) get() const noexcept {
            return std::get<N>(m_refs);
        }

        // Allow bulk-assignment of a completely composed tuple into this proxy 
        // to propagate the values straight into the scattered arrays.
        SoARef& operator=(const std::tuple<Types...>& values) {
            assign_impl(values, std::index_sequence_for<Types...>{});
            return *this;
        }

    private:
        template <size_t... Is>
        void assign_impl(const std::tuple<Types...>& values, std::index_sequence<Is...>) {
            // Fold expression magic: assign each value to the referenced array slot
            ((std::get<Is>(m_refs) = std::get<Is>(values)), ...);
        }
    };


    // ========================================================================
    // soa_vector<Types...>
    // A fully automated Structure of Arrays. 
    // Instead of forcing the CPU to read [XYZC] [XYZC] [XYZC], it stores 
    // [XXX] [YYY] [ZZZ] [CCC], enabling auto-vectorization and zero Cache-Line
    // waste during mass processing.
    // ========================================================================
    template <typename... Types>
    class soa_vector {
    private:
        // The core: A tuple where every element is an entire std::vector
        std::tuple<std::vector<Types>...> m_arrays;

    public:
        // C++ STL standard types
        using size_type = size_t;
        using reference = SoARef<Types...>;

        // ====================================================================
        // Core Geometry
        // ====================================================================

        // We measure size by looking at the very first vector (they are all identical in size)
        size_type size() const noexcept {
            return std::get<0>(m_arrays).size();
        }

        bool empty() const noexcept {
            return size() == 0;
        }

        void reserve(size_type capacity) {
            reserve_impl(capacity, std::index_sequence_for<Types...>{});
        }

        // ====================================================================
        // Modifiers (magic push)
        // ====================================================================

        // You push values simultaneously, e.g., soa.push_back(1.0f, 2.0f, 3.0f, 'c');
        void push_back(Types... args) {
            push_back_impl(std::index_sequence_for<Types...>{}, std::forward<Types>(args)...);
        }

        // ====================================================================
        // Proxy Access (struct illusion)
        // ====================================================================

        // Operator[] returns the magic proxy structure holding hot references
        reference operator[](size_type index) {
            return get_proxy_impl(index, std::index_sequence_for<Types...>{});
        }

        // ====================================================================
        // Flat Array Access (The Secret Weapon)
        // ====================================================================

        // If one writes an AVX/Physics loop, one can completely ignore the Proxy.
        // Just ask for the 100% contiguous flat array of a specific component:
        // std::span<float> xs = soa.get_array<0>();
        template <size_t N>
        std::vector<std::tuple_element_t<N, std::tuple<Types...>>>& get_array() noexcept {
            return std::get<N>(m_arrays);
        }

        template <size_t N>
        const std::vector<std::tuple_element_t<N, std::tuple<Types...>>>& get_array() const noexcept {
            return std::get<N>(m_arrays);
        }

    private:
        // --------------------------------------------------------------------
        // C++ Tuple Iteration Boilerplate (via Index Sequences and Fold Expressions)
        // --------------------------------------------------------------------

        template <size_t... Is>
        void reserve_impl(size_type capacity, std::index_sequence<Is...>) {
            // Unpack and reserve every single array
            (std::get<Is>(m_arrays).reserve(capacity), ...);
        }

        template <size_t... Is>
        void push_back_impl(std::index_sequence<Is...>, Types... args) {
            // Black magic: Uses fold expression to push the correct argument 
            // into its corresponding inner array.
            auto args_tuple = std::forward_as_tuple(args...);
            (std::get<Is>(m_arrays).push_back(std::get<Is>(args_tuple)), ...);
        }

        template <size_t... Is>
        reference get_proxy_impl(size_type index, std::index_sequence<Is...>) {
            // Creates the phantom proxy struct by pulling the reference of element [index]
            // from absolutely every internal array simultaneously!
            return reference(std::get<Is>(m_arrays)[index]...);
        }
    };

} // namespace fk
