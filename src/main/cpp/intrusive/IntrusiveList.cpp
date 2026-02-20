/*
 * Copyright 2026 Stefan Zobel
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

#include <iostream>
#include <algorithm>
#include <cassert>
#include <string_view>
#include "IntrusiveList.h"


struct Item {
    const char* name{ nullptr };
    double value{ 0.0 };
};


// Function that creates a list at compile-time
consteval auto create_static_data() {
    IntrusiveList<Item, 10> list;
    list.push_back({ "Fixed Item A", 1.0 });
    list.push_back({ "Fixed Item B", 2.0 });
    return list; // list is a compile-time constant
}

// This object gets linked into the read-only segment of the program
static constexpr auto my_const_list = create_static_data();


// Used as a function pointer argument for erase_if later
bool is_expired(const Item& item) {
    return item.value < 0.0;
}

// Used as a functor argument for erase_if later
struct PriceLimitAbove {
    double limit;
    bool operator()(const Item& item) const {
        return item.value > limit;
    }
};

// Helper function for checking the expected element order
template<typename SomeIterableList>
bool check_order(const SomeIterableList& list, std::initializer_list<int> values) {
    auto it = list.begin();
    for (int val : values) {
        if (it == list.end() || it->value != val) return false;
        ++it;
    }
    return it == list.end();
}

// Some Test functions

void test_push_operations() {
    std::cout << "Testing Push Operations... ";
    IntrusiveList<Item, 10> list;

    list.push_back({ "A", 1 });
    list.push_front({ "B", 2 });
    list.push_back({ "C", 3 });

    // Expected order: B(2), A(1), C(3)
    assert(check_order(list, { 2, 1, 3 }));
    assert(list.size() == 3);
    std::cout << "PASSED\n";
}

void test_removal_and_freelist() {
    std::cout << "Testing Removal and Freelist... ";
    IntrusiveList<Item, 5> list;

    list.push_back({ "A", 10 });
    auto idx2 = list.push_back({ "B", 20 });
    list.push_back({ "C", 30 });

    list.remove(idx2); // remove "B" in the middle
    assert(check_order(list, { 10, 30 }));
    assert(list.size() == 2);

    // Check that the slot of "B" is reused
    auto idx4 = list.push_back({ "D", 40 });
    // Freelist should be LIFO
    assert(idx4 == idx2);
    assert(list.size() == 3);
    idx4; // suppress 'not referenced' warning

    std::cout << "PASSED\n";
}

void test_insert_operations() {
    std::cout << "Testing Insert... ";
    IntrusiveList<Item, 10> list;

    list.push_back({ "1", 10 });
    auto i2 = list.push_back({ "3", 30 });

    // Insert in the middle (before i2)
    list.insert(i2, { "2", 20 });
    assert(check_order(list, { 10, 20, 30 }));

    // Insert at the head via begin()-iterator
    list.insert(list.cbegin(), { "0", 5 });
    assert(check_order(list, { 5, 10, 20, 30 }));

    // Insert at the tail via insert(Nil)
    list.insert(list.Nil, { "4", 40 });
    assert(check_order(list, { 5, 10, 20, 30, 40 }));

    std::cout << "PASSED\n";
}

void test_erase_if_concept() {
    std::cout << "Testing erase_if... ";
    IntrusiveList<Item, 10> list;

    list.push_back({ "1", 10 });
    list.push_back({ "2", 100 });
    list.push_back({ "3", 20 });
    list.push_back({ "4", 200 });

    // Remove all values >= 100
    size_t removed = list.erase_if([](const Item& i) { return i.value >= 100; });

    assert(removed == 2);
    assert(check_order(list, { 10, 20 }));
    removed; // suppress 'not referenced' warning
    std::cout << "PASSED\n";
}

void test_constexpr_capability() {
    std::cout << "Testing constexpr (Compile-time)... ";

    // If that compiles constexpr is working
    static constexpr auto compile_time_test_size = []() {
        IntrusiveList<Item, 5> l;
        l.push_back({ "X", 99 });
        l.push_front({ "Y", 88 });
        return l.size();
    }();

    static_assert(compile_time_test_size == 2);
    std::cout << "PASSED\n";
}

void test_safety_access() {
    std::cout << "Testing Access Safety (Debug/Release hybrid)... ";

    constexpr int CAPACITY = 10;
    using ListType = IntrusiveList<Item, CAPACITY>;
    ListType list;

#ifdef _DEBUG
    // --- DEBUG MODE ---
    // It's not possible to "contaminate" the sentinel, every attempt to access it should throw

    bool caught = false;
    try {
        [[maybe_unused]] auto& item = list[ListType::Nil];
    }
    catch (...) {
        caught = true;
    }
    assert(caught && "Debug Mode: Access to Nil should have thrown!");

#else
    // --- RELEASE MODE ---
    // Test the "healing" of the sentinel

    // Get a writable reference to the sentinel (Index 0)
    Item& sentinel = list[ListType::Nil];

    // Contaminate it
    sentinel.name = "!!!Garbage!!!";
    sentinel.value = 999.0;
    assert(sentinel.value == 999.0);

    // Trigger the "healing" by accessing the sentinel again
    // The internal access() method must trigger: storage[Nil] = T{}
    Item& cleaned = list.front(); // head is Nil, so access(Nil) gets called

    // Verify that the sentinel is in its default state again
    assert(cleaned.name == nullptr && "Release: Name wasn't cleaned!");
    assert(cleaned.value == 0.0 && "Release: Value wasn't cleaned!");

    // try access behind with an index too large
    sentinel = list[2 * CAPACITY];
    // Contaminate it
    sentinel.name = "???Ordures???";
    sentinel.value = 777.7;
    assert(sentinel.value == 777.7);
    cleaned = list.front();
    // Verify that the sentinel is in its default state again
    assert(cleaned.name == nullptr && "Release: Name wasn't cleaned!");
    assert(cleaned.value == 0.0 && "Release: Value wasn't cleaned!");

    cleaned; // suppress 'not referenced' warning
#endif

    std::cout << "PASSED\n";
}

void test_iterator_structured_bindings() {
    std::cout << "Testing Structured Bindings... ";
    IntrusiveList<Item, 5> list;
    list.push_back({ "A", 1 });
    list.push_back({ "B", 2 });
    list.push_back({ "C", 9 });

    int sum = 0;
    for (auto [idx, item] : list) {
        assert(idx > 0);
        sum += (int)item.value;
    }
    assert(sum == 12);
    std::cout << "PASSED\n";
}

void test_find_if() {
    std::cout << "Testing find_if... ";

    using ListType = IntrusiveList<Item, 5>;
    ListType list;

    list.push_back({ "AB", 1.0 });
    list.push_back({ "CD", 2.0 });
    list.push_back({ "EF", 3.0 });

    auto it = list.find_if([](const Item& i) { return i.value == 2.0; });
    assert(it->name == std::string_view("CD"));

    it = list.find_if([](const Item& i) { return i.value == 99.99; });
    assert(it.current == 0);
#ifndef _DEBUG
    if (it->name) { // This would throw in DEBUG mode because it is end()!
        throw "item->name is not nullptr!";
    }
#endif

    using IdxType = ListType::IndexType;
#ifndef _DEBUG
    std::pair<IdxType, const Item&> pair = *it; // This would throw in DEBUG mode because it is end()!
    const Item& item = pair.second;
    if (item.name) {
        throw "item.name is not nullptr!";
    }
#endif

    std::cout << "PASSED\n";
}

// Execute all test functions
void run_all_tests() {
    std::cout << "--- STARTING INTRUSIVE LIST TESTS ---\n";
    try {
        test_push_operations();
        test_removal_and_freelist();
        test_insert_operations();
        test_erase_if_concept();
        test_constexpr_capability();
        test_safety_access();
        test_iterator_structured_bindings();
        test_find_if();
        std::cout << "--- ALL TESTS PASSED SUCCESSFULLY ---\n";
    }
    catch (const std::exception& e) {
        std::cerr << "TEST FAILED WITH EXCEPTION: " << e.what() << "\n";
    }
}




int main() {

    using ListType = IntrusiveList<Item, 640>;
    using Index = ListType::IndexType;
//    const Index Nil = ListType::Nil;
    ListType list2;


    list2.push_back({ "Gold", 100.0 });
    Index idx2 = list2.push_front({ "Silver", 50.0 });

    list2.remove(idx2); // Silver slot becomes free

    list2.push_back({ "Bronze", 75.0 });
    list2.push_back({ "Bread", 1.99 });
    list2.push_back({ "Milk", 0.99 });
    list2.push_back({ "Coffee", 5.49 });
    list2.push_back({ "Apple", 0.99 });
    list2.push_back({ "Banana", 1.50 });

    // Range-based for loop with structured binding (auto)
    for (auto [idx, item] : list2) {
        // idx is a Copy of Index
        // item is a const reference (const T&) to the real object in the list
        std::cout << "Index: " << (int)idx << " Name: " << item.name << "\n";
    }
    std::cout << "---------------------\n";
    // Range-based for loop with structured binding (const auto&)
    for (const auto& [idx, item] : list2) {
        std::cout << "Index: " << (int)idx << " Name: " << item.name << ", Value: " << item.value << " Dollar\n";
    }
    std::cout << "---------------------\n";

    // Usage with <algorithm> (std::find_if)
    // Since the iterator returna a std::pair the lambda has to accept a pair
    // Using cbegin() / cend() and const auto&
    auto it = std::find_if(list2.cbegin(), list2.cend(), [](auto entry) {
        const auto& [idx, item] = entry; // extract
        return item.value > 5.0;
    });
    if (it != list2.cend()) {
        std::cout << "Found an expensive product: " << it->name << "\n";
    }
    std::cout << "---------------------\n";

    // Using begin() / end() and auto&
    auto it2 = std::find_if(list2.begin(), list2.end(), [](auto entry) {
        auto& [idx, item] = entry; // extractt
        return item.value < -1500.0;
    });
    if (it2 == list2.end()) {
        std::cout << "Predicate didn't match anything! Index is: " << it2.current << "\n";
    }
    std::cout << "---------------------\n";

    // Using cbegin() / cend() and auto
    auto cit2 = std::find_if(list2.cbegin(), list2.cend(), [](auto entry) {
        auto [idx, item] = entry; // extract
        return item.value < -250.0;
    });
    if (cit2 == list2.cend()) {
        std::cout << "Predicate didn't match anything! Index is: " << cit2.current << "\n";
    }
    std::cout << "---------------------\n";


    // Test an object with Move-Only semantics
    struct MoveOnlyItem {
        std::unique_ptr<Item> ptr; // Pointer to an Item

        // Default construcor
        MoveOnlyItem() = default;
        // Constructor that takes a std::unique_ptr<Item>
        MoveOnlyItem(std::unique_ptr<Item> p) : ptr(std::move(p)) {}
    };

    IntrusiveList<MoveOnlyItem, 10> list3;
    Item item1{ "Test1", 43.0 };
    std::unique_ptr<Item> ptr1 = std::make_unique<Item>(item1);
    Item item2{ "Test2", 45.0 };
    auto ptr2 = std::make_unique<Item>(item2);
    // item3
    list3.push_back(std::move( std::make_unique<Item>(Item{"Test3", 42.0 }) )); // Works
    // item1
    list3.push_back(MoveOnlyItem(std::move(ptr1))); // Works
    // Or, thanks to the forwarding-constructor) even directly
    list3.push_back({ std::move(ptr2) });
    for (auto [idx, moveOnly] :list3) {
        std::cout << "Index: " << (int)idx << ", Name: " << moveOnly.ptr->name << ", Value: " << moveOnly.ptr->value << "\n";
    }
    std::cout << "---------------------\n";

    // Iteration over the static constexpr list
    for (auto [idx, item4] : my_const_list) {
        std::cout << "Index: " << (int)idx << " Name: " << item4.name << "\n";
    }

    // erase_if with a lambda
    size_t erased = list2.erase_if([](const Item& item) {
        return item.value > 150.0;
    });
    std::cout << "Erased: " << erased << "\n";

    // erase_if with a function pointer
    erased = list2.erase_if(is_expired);
    std::cout << "Erased: " << erased << "\n";

    // erase_if with a functor
    erased = list2.erase_if(PriceLimitAbove{ 150.0 });
    std::cout << "Erased: " << erased << "\n";

    double threshold = 1.45;
    const char* search_name = "Banana";
    // erase_if with a capturing lambda
    erased = list2.erase_if([&](const auto& item) {
        return (item.value >= threshold) && (std::string_view(item.name) == search_name);
    });
    std::cout << "Erased: " << erased << "\n";
    std::cout << "---------------------\n\n";

    run_all_tests();

    return 0;
}
