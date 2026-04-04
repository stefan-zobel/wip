#pragma once

#include <algorithm>
#include <array>
#include <atomic>
#include <bit>
#include <concepts>
#include <cstddef>
#include <cstdint>
#include <mutex>
#include <type_traits>
#include <utility>

#include "ConcurrentArenaCore.h"

template <typename A>
concept ConcurrentSlabArena =
    requires(A& arena, typename A::ScopedOperation& op, size_t size, size_t alignment) {
        typename A::ScopedOperation;
        { A::blocks_on_seal } -> std::convertible_to<bool>;
        { arena.acquire_operation() } -> std::same_as<typename A::ScopedOperation>;
        { arena.get_epoch() } -> std::convertible_to<size_t>;
        { arena.allocate_raw_aligned(op, size, alignment) } -> std::same_as<void*>;
    };

template <ConcurrentSlabArena Arena = ConcurrentArena>
class ConcurrentSlabAllocator final {
    static constexpr size_t MIN_SIZE = 8;
    static constexpr size_t MAX_SIZE = 4'096;
    static constexpr size_t NUM_BINS =
        std::countr_zero(MAX_SIZE) - std::countr_zero(MIN_SIZE) + 1;

    struct Node {
        std::atomic<Node*> next{ nullptr };
    };

    struct TaggedHead {
        Node* ptr;
        uint64_t tag;
    };

    struct LocalBinCache {
        Node* head = nullptr;
        size_t count = 0;
    };

    struct ThreadCacheEntry {
        uint64_t owner_id = 0;
        size_t epoch = 0;
        std::array<LocalBinCache, NUM_BINS> bins{};
        ThreadCacheEntry* next = nullptr;
    };

    struct ThreadCacheListOwner {
        ThreadCacheEntry* head = nullptr;

        ~ThreadCacheListOwner() {
            while (head) {
                ThreadCacheEntry* next = head->next;
                delete head;
                head = next;
            }
        }
    };

    static constexpr size_t local_cache_capacity_ = 16;
    static constexpr size_t refill_batch_size_ = 8;
    static constexpr size_t drain_batch_size_ = 8;

    static_assert(Arena::blocks_on_seal,
                  "ConcurrentSlabAllocator requires an Arena with blocking release semantics.");
    static_assert(std::has_single_bit(MIN_SIZE));
    static_assert(std::has_single_bit(MAX_SIZE));
    static_assert(MAX_SIZE >= MIN_SIZE);
    static_assert(std::is_trivially_copyable_v<TaggedHead>);

public:
    static constexpr bool bin_heads_are_always_lock_free =
        std::atomic<TaggedHead>::is_always_lock_free;

    static constexpr size_t local_cache_capacity = local_cache_capacity_;
    static constexpr size_t refill_batch_size = refill_batch_size_;
    static constexpr size_t drain_batch_size = drain_batch_size_;

    explicit ConcurrentSlabAllocator(Arena& arena) noexcept
        : source_arena_(arena),
          owner_id_(next_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          known_epoch_(arena.get_epoch()) {
        for (auto& head : free_lists_) {
            head.store(TaggedHead{ nullptr, 0 }, std::memory_order_relaxed);
        }
    }

    ConcurrentSlabAllocator(const ConcurrentSlabAllocator&) = delete;
    ConcurrentSlabAllocator& operator=(const ConcurrentSlabAllocator&) = delete;
    ConcurrentSlabAllocator(ConcurrentSlabAllocator&&) = delete;
    ConcurrentSlabAllocator& operator=(ConcurrentSlabAllocator&&) = delete;

    void* allocate(size_t size, size_t alignment = 64) {
        auto op = source_arena_.acquire_operation();
        if (!op) {
            return nullptr;
        }

        const size_t arena_epoch = sync_epoch_inside_operation();
        const size_t required_size = std::max(size, MIN_SIZE);

        if (required_size > MAX_SIZE) {
            direct_arena_allocations_.fetch_add(1, std::memory_order_relaxed);
            return source_arena_.allocate_raw_aligned(op, required_size, alignment);
        }

        const size_t size_class = std::bit_ceil(required_size);

        // Pooling is only safe if the requested alignment is guaranteed by the bin.
        if (alignment > size_class) {
            direct_arena_allocations_.fetch_add(1, std::memory_order_relaxed);
            return source_arena_.allocate_raw_aligned(op, required_size, alignment);
        }

        const size_t bin_index = size_class_to_bin_index(size_class);
        ThreadCacheEntry& entry = get_thread_entry(arena_epoch);
        LocalBinCache& local = entry.bins[bin_index];

        if (void* ptr = pop_local_block(local)) {
            local_cache_hits_.fetch_add(1, std::memory_order_relaxed);
            return ptr;
        }

        refill_local_cache(local, bin_index);

        if (void* ptr = pop_local_block(local)) {
            local_cache_hits_.fetch_add(1, std::memory_order_relaxed);
            return ptr;
        }

        fresh_bin_allocations_.fetch_add(1, std::memory_order_relaxed);
        return source_arena_.allocate_raw_aligned(op,
                                                  size_class,
                                                  std::max(alignment, alignof(Node)));
    }

    void deallocate(void* ptr, size_t original_size, size_t original_alignment = 64) noexcept {
        if (!ptr) {
            return;
        }

        auto op = source_arena_.acquire_operation();
        if (!op) {
            return;
        }

        const size_t arena_epoch = sync_epoch_inside_operation();
        const size_t required_size = std::max(original_size, MIN_SIZE);

        if (required_size > MAX_SIZE) {
            return;
        }

        const size_t size_class = std::bit_ceil(required_size);

        if (original_alignment > size_class) {
            return;
        }

        const size_t bin_index = size_class_to_bin_index(size_class);
        ThreadCacheEntry& entry = get_thread_entry(arena_epoch);
        LocalBinCache& local = entry.bins[bin_index];

        if (local.count >= local_cache_capacity_) {
            drain_local_cache(local, bin_index, drain_batch_size_);
        }

        push_local_block(local, ptr);
    }

    template <typename T, typename... Args>
    [[nodiscard]] T* construct(Args&&... args) {
        void* ptr = allocate(sizeof(T), alignof(T));
        if (!ptr) {
            return nullptr;
        }

        try {
            return new (ptr) T(std::forward<Args>(args)...);
        } catch (...) {
            deallocate(ptr, sizeof(T), alignof(T));
            throw;
        }
    }

    template <typename T>
    void destruct(T* ptr) noexcept {
        if (!ptr) {
            return;
        }

        ptr->~T();
        deallocate(ptr, sizeof(T), alignof(T));
    }

    [[nodiscard]] size_t get_known_epoch() const noexcept {
        return known_epoch_.load(std::memory_order_acquire);
    }

    [[nodiscard]] static constexpr size_t min_size() noexcept {
        return MIN_SIZE;
    }

    [[nodiscard]] static constexpr size_t max_size() noexcept {
        return MAX_SIZE;
    }

    [[nodiscard]] static constexpr size_t num_bins() noexcept {
        return NUM_BINS;
    }

    struct StatsSnapshot {
        size_t local_cache_hits = 0;
        size_t local_cache_refills = 0;
        size_t local_cache_drains = 0;
        size_t global_bin_pops = 0;
        size_t global_bin_pushes = 0;
        size_t fresh_bin_allocations = 0;
        size_t direct_arena_allocations = 0;
        size_t epoch_resets = 0;
    };

    [[nodiscard]] StatsSnapshot get_stats() const noexcept {
        return StatsSnapshot{
            .local_cache_hits = local_cache_hits_.load(std::memory_order_relaxed),
            .local_cache_refills = local_cache_refills_.load(std::memory_order_relaxed),
            .local_cache_drains = local_cache_drains_.load(std::memory_order_relaxed),
            .global_bin_pops = global_bin_pops_.load(std::memory_order_relaxed),
            .global_bin_pushes = global_bin_pushes_.load(std::memory_order_relaxed),
            .fresh_bin_allocations = fresh_bin_allocations_.load(std::memory_order_relaxed),
            .direct_arena_allocations = direct_arena_allocations_.load(std::memory_order_relaxed),
            .epoch_resets = epoch_resets_.load(std::memory_order_relaxed)
        };
    }

    void reset_stats() noexcept {
        local_cache_hits_.store(0, std::memory_order_relaxed);
        local_cache_refills_.store(0, std::memory_order_relaxed);
        local_cache_drains_.store(0, std::memory_order_relaxed);
        global_bin_pops_.store(0, std::memory_order_relaxed);
        global_bin_pushes_.store(0, std::memory_order_relaxed);
        fresh_bin_allocations_.store(0, std::memory_order_relaxed);
        direct_arena_allocations_.store(0, std::memory_order_relaxed);
        epoch_resets_.store(0, std::memory_order_relaxed);
    }

private:
    static constexpr size_t size_class_to_bin_index(size_t size_class) noexcept {
        return std::countr_zero(size_class) - std::countr_zero(MIN_SIZE);
    }

    [[nodiscard]] size_t sync_epoch_inside_operation() noexcept {
        const size_t arena_epoch = source_arena_.get_epoch();
        if (known_epoch_.load(std::memory_order_acquire) == arena_epoch) {
            return arena_epoch;
        }

        std::scoped_lock epoch_lock(epoch_mutex_);

        if (known_epoch_.load(std::memory_order_relaxed) != arena_epoch) {
            for (auto& head : free_lists_) {
                const TaggedHead old_head = head.load(std::memory_order_acquire);
                head.store(TaggedHead{ nullptr, old_head.tag + 1 }, std::memory_order_release);
            }

            epoch_resets_.fetch_add(1, std::memory_order_relaxed);
            known_epoch_.store(arena_epoch, std::memory_order_release);
        }

        return arena_epoch;
    }

    [[nodiscard]] ThreadCacheEntry& get_thread_entry(size_t arena_epoch) {
        for (ThreadCacheEntry* entry = tls_cache_.head; entry != nullptr; entry = entry->next) {
            if (entry->owner_id == owner_id_) {
                sync_local_epoch(*entry, arena_epoch);
                return *entry;
            }
        }

        ThreadCacheEntry* entry = new ThreadCacheEntry{};
        entry->owner_id = owner_id_;
        entry->epoch = arena_epoch;
        entry->next = tls_cache_.head;
        tls_cache_.head = entry;

        return *entry;
    }

    static void sync_local_epoch(ThreadCacheEntry& entry, size_t arena_epoch) noexcept {
        if (entry.epoch == arena_epoch) {
            return;
        }

        for (auto& bin : entry.bins) {
            bin.head = nullptr;
            bin.count = 0;
        }

        entry.epoch = arena_epoch;
    }

    [[nodiscard]] static void* pop_local_block(LocalBinCache& cache) noexcept {
        if (!cache.head) {
            return nullptr;
        }

        Node* node = cache.head;
        cache.head = node->next.load(std::memory_order_relaxed);
        --cache.count;
        return node;
    }

    static void push_local_block(LocalBinCache& cache, void* ptr) noexcept {
        Node* node = static_cast<Node*>(ptr);
        node->next.store(cache.head, std::memory_order_relaxed);
        cache.head = node;
        ++cache.count;
    }

    void refill_local_cache(LocalBinCache& cache, size_t bin_index) noexcept {
        local_cache_refills_.fetch_add(1, std::memory_order_relaxed);

        while (cache.count < refill_batch_size_) {
            void* ptr = pop_free_block(bin_index);
            if (!ptr) {
                break;
            }

            push_local_block(cache, ptr);
        }
    }

    void drain_local_cache(LocalBinCache& cache, size_t bin_index, size_t count) noexcept {
        local_cache_drains_.fetch_add(1, std::memory_order_relaxed);

        for (size_t i = 0; i < count; ++i) {
            void* ptr = pop_local_block(cache);
            if (!ptr) {
                return;
            }

            push_free_block(bin_index, ptr);
        }
    }

    void* pop_free_block(size_t bin_index) noexcept {
        TaggedHead head = free_lists_[bin_index].load(std::memory_order_acquire);

        for (;;) {
            if (!head.ptr) {
                return nullptr;
            }

            Node* next = head.ptr->next.load(std::memory_order_relaxed);
            const TaggedHead desired{ next, head.tag + 1 };

            if (free_lists_[bin_index].compare_exchange_weak(head,
                                                             desired,
                                                             std::memory_order_acq_rel,
                                                             std::memory_order_acquire)) {
                global_bin_pops_.fetch_add(1, std::memory_order_relaxed);
                return head.ptr;
            }
        }
    }

    void push_free_block(size_t bin_index, void* ptr) noexcept {
        Node* node = static_cast<Node*>(ptr);
        TaggedHead head = free_lists_[bin_index].load(std::memory_order_acquire);

        for (;;) {
            node->next.store(head.ptr, std::memory_order_relaxed);
            const TaggedHead desired{ node, head.tag + 1 };

            if (free_lists_[bin_index].compare_exchange_weak(head,
                                                             desired,
                                                             std::memory_order_acq_rel,
                                                             std::memory_order_acquire)) {
                global_bin_pushes_.fetch_add(1, std::memory_order_relaxed);
                return;
            }
        }
    }

private:
    inline static thread_local ThreadCacheListOwner tls_cache_{};
    inline static std::atomic<uint64_t> next_owner_id_{ 1 };

    Arena& source_arena_;
    uint64_t owner_id_ = 0;
    std::atomic<size_t> known_epoch_{ 0 };

    std::array<std::atomic<TaggedHead>, NUM_BINS> free_lists_{};

    std::mutex epoch_mutex_;

    // Instrumentation counters for per-bin cache efficiency and slow-path use.
    std::atomic<size_t> local_cache_hits_{ 0 };
    std::atomic<size_t> local_cache_refills_{ 0 };
    std::atomic<size_t> local_cache_drains_{ 0 };
    std::atomic<size_t> global_bin_pops_{ 0 };
    std::atomic<size_t> global_bin_pushes_{ 0 };
    std::atomic<size_t> fresh_bin_allocations_{ 0 };
    std::atomic<size_t> direct_arena_allocations_{ 0 };
    std::atomic<size_t> epoch_resets_{ 0 };
};

static_assert(!std::copy_constructible<ConcurrentSlabAllocator<>>);
static_assert(!std::movable<ConcurrentSlabAllocator<>>);
