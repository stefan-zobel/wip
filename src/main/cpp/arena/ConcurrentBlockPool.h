#pragma once

#include <algorithm>
#include <atomic>
#include <concepts>
#include <cstddef>
#include <cstdint>
#include <type_traits>
#include <utility>

#include "ConcurrentArenaCore.h"

template <typename A>
concept ConcurrentBlockPoolArena =
    requires(A& arena, typename A::ScopedOperation& op, size_t size, size_t alignment) {
        typename A::ScopedOperation;
        { A::blocks_on_seal } -> std::convertible_to<bool>;
        { arena.acquire_operation() } -> std::same_as<typename A::ScopedOperation>;
        { arena.get_epoch() } -> std::convertible_to<size_t>;
        { arena.allocate_raw_aligned(op, size, alignment) } -> std::same_as<void*>;
    };

template <typename T, ConcurrentBlockPoolArena Arena = ConcurrentArena>
class ConcurrentBlockPool final {
    struct Node {
        std::atomic<Node*> next{ nullptr };
    };

    struct TaggedHead {
        Node* ptr;
        uint64_t tag;
    };

    struct LocalCache {
        Node* head = nullptr;
        size_t count = 0;
        size_t epoch = 0;
    };

    struct ThreadCacheEntry {
        uint64_t owner_id = 0;
        LocalCache cache{};
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

    static constexpr size_t block_size_ = std::max(sizeof(T), sizeof(Node));
    static constexpr size_t block_alignment_ = std::max(alignof(T), alignof(Node));

    static constexpr size_t local_cache_capacity_ = 32;
    static constexpr size_t refill_batch_size_ = 16;
    static constexpr size_t drain_batch_size_ = 16;

    static_assert(Arena::blocks_on_seal,
                  "ConcurrentBlockPool requires an Arena with blocking release semantics.");
    static_assert(block_size_ >= sizeof(Node));
    static_assert(block_alignment_ >= alignof(Node));
    static_assert(std::is_trivially_copyable_v<TaggedHead>);

public:
    static constexpr bool free_list_head_is_always_lock_free =
        std::atomic<TaggedHead>::is_always_lock_free;

    static constexpr size_t local_cache_capacity = local_cache_capacity_;
    static constexpr size_t refill_batch_size = refill_batch_size_;
    static constexpr size_t drain_batch_size = drain_batch_size_;

    explicit ConcurrentBlockPool(Arena& arena) noexcept
        : source_arena_(arena),
          owner_id_(next_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          known_epoch_(arena.get_epoch()) {
        free_list_head_.store(TaggedHead{ nullptr, 0 }, std::memory_order_relaxed);
    }

    ConcurrentBlockPool(const ConcurrentBlockPool&) = delete;
    ConcurrentBlockPool& operator=(const ConcurrentBlockPool&) = delete;
    ConcurrentBlockPool(ConcurrentBlockPool&&) = delete;
    ConcurrentBlockPool& operator=(ConcurrentBlockPool&&) = delete;

    template <typename... Args>
    [[nodiscard]] T* construct(Args&&... args) {
        auto op = source_arena_.acquire_operation();
        if (!op) {
            return nullptr;
        }

        const size_t arena_epoch = sync_epoch_inside_operation();
        LocalCache& local = get_local_cache(arena_epoch);

        // Fast path: consume a previously recycled block from the local cache.
        void* memory = pop_local_block(local);
        if (memory) {
            local_cache_hits_.fetch_add(1, std::memory_order_relaxed);
        }

        // Refill the local cache from the shared global free list in small batches
        // before falling back to the Arena for a fresh block.
        if (!memory) {
            refill_local_cache(local);
            memory = pop_local_block(local);
            if (memory) {
                local_cache_hits_.fetch_add(1, std::memory_order_relaxed);
            }
        }

        if (!memory) {
            memory = source_arena_.allocate_raw_aligned(op, block_size_, block_alignment_);
            if (!memory) {
                return nullptr;
            }
            fresh_arena_blocks_.fetch_add(1, std::memory_order_relaxed);
        }

        try {
            return new (memory) T(std::forward<Args>(args)...);
        } catch (...) {
            push_local_block(local, memory);
            throw;
        }
    }

    void destruct(T* ptr) noexcept {
        if (!ptr) {
            return;
        }

        auto op = source_arena_.acquire_operation();
        if (!op) {
            return;
        }

        const size_t arena_epoch = sync_epoch_inside_operation();
        LocalCache& local = get_local_cache(arena_epoch);

        ptr->~T();

        if (local.count >= local_cache_capacity_) {
            drain_local_cache(local, drain_batch_size_);
        }

        push_local_block(local, ptr);
    }

    [[nodiscard]] size_t get_known_epoch() const noexcept {
        return known_epoch_.load(std::memory_order_acquire);
    }

    struct StatsSnapshot {
        size_t local_cache_hits = 0;
        size_t local_cache_refills = 0;
        size_t local_cache_drains = 0;
        size_t global_free_list_pops = 0;
        size_t global_free_list_pushes = 0;
        size_t fresh_arena_blocks = 0;
        size_t epoch_resets = 0;
    };

    [[nodiscard]] StatsSnapshot get_stats() const noexcept {
        return StatsSnapshot{
            .local_cache_hits = local_cache_hits_.load(std::memory_order_relaxed),
            .local_cache_refills = local_cache_refills_.load(std::memory_order_relaxed),
            .local_cache_drains = local_cache_drains_.load(std::memory_order_relaxed),
            .global_free_list_pops = global_free_list_pops_.load(std::memory_order_relaxed),
            .global_free_list_pushes = global_free_list_pushes_.load(std::memory_order_relaxed),
            .fresh_arena_blocks = fresh_arena_blocks_.load(std::memory_order_relaxed),
            .epoch_resets = epoch_resets_.load(std::memory_order_relaxed)
        };
    }

    void reset_stats() noexcept {
        local_cache_hits_.store(0, std::memory_order_relaxed);
        local_cache_refills_.store(0, std::memory_order_relaxed);
        local_cache_drains_.store(0, std::memory_order_relaxed);
        global_free_list_pops_.store(0, std::memory_order_relaxed);
        global_free_list_pushes_.store(0, std::memory_order_relaxed);
        fresh_arena_blocks_.store(0, std::memory_order_relaxed);
        epoch_resets_.store(0, std::memory_order_relaxed);
    }

private:
    [[nodiscard]] size_t sync_epoch_inside_operation() noexcept {
        const size_t arena_epoch = source_arena_.get_epoch();
        if (known_epoch_.load(std::memory_order_acquire) == arena_epoch) {
            return arena_epoch;
        }

        std::scoped_lock epoch_lock(epoch_mutex_);

        if (known_epoch_.load(std::memory_order_relaxed) != arena_epoch) {
            const TaggedHead old_head = free_list_head_.load(std::memory_order_acquire);
            free_list_head_.store(TaggedHead{ nullptr, old_head.tag + 1 }, std::memory_order_release);
            epoch_resets_.fetch_add(1, std::memory_order_relaxed);
            known_epoch_.store(arena_epoch, std::memory_order_release);
        }

        return arena_epoch;
    }

    [[nodiscard]] LocalCache& get_local_cache(size_t arena_epoch) {
        for (ThreadCacheEntry* entry = tls_cache_.head; entry != nullptr; entry = entry->next) {
            if (entry->owner_id == owner_id_) {
                sync_local_cache_epoch(entry->cache, arena_epoch);
                return entry->cache;
            }
        }

        ThreadCacheEntry* entry = new ThreadCacheEntry{};
        entry->owner_id = owner_id_;
        entry->cache.epoch = arena_epoch;
        entry->next = tls_cache_.head;
        tls_cache_.head = entry;

        return entry->cache;
    }

    static void sync_local_cache_epoch(LocalCache& cache, size_t arena_epoch) noexcept {
        if (cache.epoch != arena_epoch) {
            cache.head = nullptr;
            cache.count = 0;
            cache.epoch = arena_epoch;
        }
    }

    [[nodiscard]] static void* pop_local_block(LocalCache& cache) noexcept {
        if (!cache.head) {
            return nullptr;
        }

        Node* node = cache.head;
        cache.head = node->next.load(std::memory_order_relaxed);
        --cache.count;
        return node;
    }

    static void push_local_block(LocalCache& cache, void* ptr) noexcept {
        Node* node = static_cast<Node*>(ptr);
        node->next.store(cache.head, std::memory_order_relaxed);
        cache.head = node;
        ++cache.count;
    }

    void refill_local_cache(LocalCache& cache) noexcept {
        local_cache_refills_.fetch_add(1, std::memory_order_relaxed);
        while (cache.count < refill_batch_size_) {
            void* ptr = pop_free_block();
            if (!ptr) {
                break;
            }

            push_local_block(cache, ptr);
        }
    }

    void drain_local_cache(LocalCache& cache, size_t count) noexcept {
        local_cache_drains_.fetch_add(1, std::memory_order_relaxed);
        for (size_t i = 0; i < count; ++i) {
            void* ptr = pop_local_block(cache);
            if (!ptr) {
                return;
            }

            push_free_block(ptr);
        }
    }

    void* pop_free_block() noexcept {
        TaggedHead head = free_list_head_.load(std::memory_order_acquire);

        for (;;) {
            if (!head.ptr) {
                return nullptr;
            }

            Node* next = head.ptr->next.load(std::memory_order_relaxed);
            const TaggedHead desired{ next, head.tag + 1 };

            if (free_list_head_.compare_exchange_weak(head,
                                                      desired,
                                                      std::memory_order_acq_rel,
                                                      std::memory_order_acquire)) {
                global_free_list_pops_.fetch_add(1, std::memory_order_relaxed);
                return head.ptr;
            }
        }
    }

    void push_free_block(void* ptr) noexcept {
        Node* node = static_cast<Node*>(ptr);
        TaggedHead head = free_list_head_.load(std::memory_order_acquire);

        for (;;) {
            node->next.store(head.ptr, std::memory_order_relaxed);
            const TaggedHead desired{ node, head.tag + 1 };

            if (free_list_head_.compare_exchange_weak(head,
                                                      desired,
                                                      std::memory_order_acq_rel,
                                                      std::memory_order_acquire)) {
                global_free_list_pushes_.fetch_add(1, std::memory_order_relaxed);
                return;
            }
        }
    }

private:
    inline static thread_local ThreadCacheListOwner tls_cache_{};
    inline static std::atomic<uint64_t> next_owner_id_{ 1 };

    Arena& source_arena_;
    uint64_t owner_id_ = 0;
    std::atomic<TaggedHead> free_list_head_{ TaggedHead{ nullptr, 0 } };
    std::atomic<size_t> known_epoch_{ 0 };

    std::mutex epoch_mutex_;

    // Instrumentation counters for cache efficiency and slow-path pressure.
    std::atomic<size_t> local_cache_hits_{ 0 };
    std::atomic<size_t> local_cache_refills_{ 0 };
    std::atomic<size_t> local_cache_drains_{ 0 };
    std::atomic<size_t> global_free_list_pops_{ 0 };
    std::atomic<size_t> global_free_list_pushes_{ 0 };
    std::atomic<size_t> fresh_arena_blocks_{ 0 };
    std::atomic<size_t> epoch_resets_{ 0 };
};

static_assert(!std::copy_constructible<ConcurrentBlockPool<int>>);
static_assert(!std::movable<ConcurrentBlockPool<int>>);
