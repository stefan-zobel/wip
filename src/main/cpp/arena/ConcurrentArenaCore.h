#pragma once

#define NOMINMAX

#include <algorithm>
#include <atomic>
#include <concepts>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <mutex>
#include <new>
#include <type_traits>
#include <utility>
#include <windows.h>

// ============================================================================
// ConcurrentArenaCore
// ============================================================================

struct NoCleanupPolicy {
    using DestructorFunc = void(*)(void*);

    static constexpr bool tracks_destructors = false;
    static constexpr size_t cleanup_node_size = 0;
    static constexpr size_t cleanup_node_alignment = 1;

    void publish_cleanup(void*, void*, DestructorFunc) noexcept {
    }

    void run_cleanup() noexcept {
    }

    void reset_cleanup() noexcept {
    }
};

struct AtomicCleanupStackPolicy {
    using DestructorFunc = void(*)(void*);

    struct CleanupNode {
        void* ptr;
        DestructorFunc dtor;
        CleanupNode* next;
    };

    static constexpr bool tracks_destructors = true;
    static constexpr size_t cleanup_node_size = sizeof(CleanupNode);
    static constexpr size_t cleanup_node_alignment = alignof(CleanupNode);

    void publish_cleanup(void* node_memory, void* object_ptr, DestructorFunc dtor) noexcept {
        CleanupNode* node = new (node_memory) CleanupNode{ object_ptr, dtor, nullptr };

        CleanupNode* head = cleanup_head_.load(std::memory_order_relaxed);
        do {
            node->next = head;
        } while (!cleanup_head_.compare_exchange_weak(head,
                                                      node,
                                                      std::memory_order_release,
                                                      std::memory_order_relaxed));
    }

    void run_cleanup() noexcept {
        CleanupNode* current = cleanup_head_.exchange(nullptr, std::memory_order_acq_rel);
        while (current) {
            current->dtor(current->ptr);
            current = current->next;
        }
    }

    void reset_cleanup() noexcept {
        cleanup_head_.store(nullptr, std::memory_order_relaxed);
    }

private:
    std::atomic<CleanupNode*> cleanup_head_{ nullptr };
};

struct ThreadLocalCleanupPolicy {
    using DestructorFunc = void(*)(void*);

    struct CleanupNode {
        void* ptr;
        DestructorFunc dtor;
        CleanupNode* next;
    };

    struct LocalList {
        CleanupNode* head = nullptr;
        LocalList* next = nullptr;
        bool bound = false;
    };

    struct RegistryState {
        LocalList* head = nullptr;
        std::mutex mutex;

        ~RegistryState() {
            while (head) {
                LocalList* next = head->next;
                delete head;
                head = next;
            }
        }
    };

    struct ThreadCacheEntry {
        uint64_t owner_id = 0;
        std::shared_ptr<RegistryState> registry;
        LocalList* list = nullptr;
        ThreadCacheEntry* next = nullptr;
    };

    struct ThreadCacheListOwner {
        ThreadCacheEntry* head = nullptr;

        ~ThreadCacheListOwner() {
            while (head) {
                ThreadCacheEntry* next = head->next;

                if (head->registry && head->list) {
                    std::scoped_lock lock(head->registry->mutex);
                    head->list->bound = false;
                }

                delete head;
                head = next;
            }
        }
    };

    inline static thread_local ThreadCacheListOwner tls_cache_{};
    inline static std::atomic<uint64_t> next_owner_id_{ 1 };

    static constexpr bool tracks_destructors = true;
    static constexpr size_t cleanup_node_size = sizeof(CleanupNode);
    static constexpr size_t cleanup_node_alignment = alignof(CleanupNode);

    ThreadLocalCleanupPolicy() noexcept
        : owner_id_(next_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          registry_(std::make_shared<RegistryState>()) {
    }

    ~ThreadLocalCleanupPolicy() = default;

    ThreadLocalCleanupPolicy(const ThreadLocalCleanupPolicy&) = delete;
    ThreadLocalCleanupPolicy& operator=(const ThreadLocalCleanupPolicy&) = delete;
    ThreadLocalCleanupPolicy(ThreadLocalCleanupPolicy&&) = delete;
    ThreadLocalCleanupPolicy& operator=(ThreadLocalCleanupPolicy&&) = delete;

    void publish_cleanup(void* node_memory, void* object_ptr, DestructorFunc dtor) noexcept {
        CleanupNode* node = new (node_memory) CleanupNode{ object_ptr, dtor, nullptr };
        LocalList* list = get_or_create_local_list();
        node->next = list->head;
        list->head = node;
    }

    void run_cleanup() noexcept {
        std::scoped_lock lock(registry_->mutex);

        for (LocalList* list = registry_->head; list != nullptr; list = list->next) {
            CleanupNode* current = list->head;
            while (current) {
                current->dtor(current->ptr);
                current = current->next;
            }
            list->head = nullptr;
        }
    }

    void reset_cleanup() noexcept {
        std::scoped_lock lock(registry_->mutex);

        for (LocalList* list = registry_->head; list != nullptr; list = list->next) {
            list->head = nullptr;
        }
    }

private:
    LocalList* get_or_create_local_list() {
        for (ThreadCacheEntry* entry = tls_cache_.head; entry != nullptr; entry = entry->next) {
            if (entry->owner_id == owner_id_) {
                return entry->list;
            }
        }

        LocalList* list = nullptr;

        {
            std::scoped_lock lock(registry_->mutex);

            for (LocalList* current = registry_->head; current != nullptr; current = current->next) {
                if (!current->bound) {
                    current->bound = true;
                    list = current;
                    break;
                }
            }

            if (!list) {
                list = new LocalList{};
                list->bound = true;
                list->next = registry_->head;
                registry_->head = list;
            }
        }

        ThreadCacheEntry* entry = new ThreadCacheEntry{};
        entry->owner_id = owner_id_;
        entry->registry = registry_;
        entry->list = list;
        entry->next = tls_cache_.head;
        tls_cache_.head = entry;

        return list;
    }

private:
    uint64_t owner_id_ = 0;
    std::shared_ptr<RegistryState> registry_;
};

struct QuiescentReleasePolicy {
    static constexpr bool blocks_on_seal = true;

    bool enter_operation() noexcept {
        for (;;) {
            while (sealed_.load(std::memory_order_acquire)) {
                sealed_.wait(true, std::memory_order_acquire);
            }

            active_operations_.fetch_add(1, std::memory_order_acq_rel);

            if (!sealed_.load(std::memory_order_acquire)) {
                return true;
            }

            leave_operation();
        }
    }

    void leave_operation() noexcept {
        if (active_operations_.fetch_sub(1, std::memory_order_acq_rel) == 1) {
            active_operations_.notify_all();
        }
    }

    void begin_release() noexcept {
        sealed_.store(true, std::memory_order_release);
    }

    void wait_for_quiescence() noexcept {
        size_t active = active_operations_.load(std::memory_order_acquire);
        while (active != 0) {
            active_operations_.wait(active, std::memory_order_acquire);
            active = active_operations_.load(std::memory_order_acquire);
        }
    }

    void end_release() noexcept {
        sealed_.store(false, std::memory_order_release);
        sealed_.notify_all();
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return sealed_.load(std::memory_order_acquire);
    }

private:
    std::atomic<bool> sealed_{ false };
    std::atomic<size_t> active_operations_{ 0 };
};

struct TryEnterReleasePolicy {
    static constexpr bool blocks_on_seal = false;

    bool enter_operation() noexcept {
        if (sealed_.load(std::memory_order_acquire)) {
            return false;
        }

        active_operations_.fetch_add(1, std::memory_order_acq_rel);

        if (sealed_.load(std::memory_order_acquire)) {
            leave_operation();
            return false;
        }

        return true;
    }

    void leave_operation() noexcept {
        if (active_operations_.fetch_sub(1, std::memory_order_acq_rel) == 1) {
            active_operations_.notify_all();
        }
    }

    void begin_release() noexcept {
        sealed_.store(true, std::memory_order_release);
    }

    void wait_for_quiescence() noexcept {
        size_t active = active_operations_.load(std::memory_order_acquire);
        while (active != 0) {
            active_operations_.wait(active, std::memory_order_acquire);
            active = active_operations_.load(std::memory_order_acquire);
        }
    }

    void end_release() noexcept {
        sealed_.store(false, std::memory_order_release);
        sealed_.notify_all();
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return sealed_.load(std::memory_order_acquire);
    }

private:
    std::atomic<bool> sealed_{ false };
    std::atomic<size_t> active_operations_{ 0 };
};

template <typename T>
concept ConcurrentArenaCleanupPolicy =
    requires(T policy, void* node_memory, void* object_ptr, void(*dtor)(void*)) {
        typename T::DestructorFunc;
        { T::tracks_destructors } -> std::convertible_to<bool>;
        { T::cleanup_node_size } -> std::convertible_to<size_t>;
        { T::cleanup_node_alignment } -> std::convertible_to<size_t>;
        policy.publish_cleanup(node_memory, object_ptr, dtor);
        policy.run_cleanup();
        policy.reset_cleanup();
    };

template <typename T>
concept ConcurrentArenaReleasePolicy =
    requires(T policy) {
        { T::blocks_on_seal } -> std::convertible_to<bool>;
        { policy.enter_operation() } -> std::convertible_to<bool>;
        policy.leave_operation();
        policy.begin_release();
        policy.wait_for_quiescence();
        policy.end_release();
        { policy.is_sealed() } -> std::convertible_to<bool>;
    };

template <
    ConcurrentArenaCleanupPolicy CleanupPolicy = AtomicCleanupStackPolicy,
    ConcurrentArenaReleasePolicy ReleasePolicy = QuiescentReleasePolicy>
class ConcurrentArenaCore final : private CleanupPolicy, private ReleasePolicy {
    struct LocalChunk {
        char* current = nullptr;
        char* end = nullptr;
        size_t epoch = 0;
    };

    struct ThreadChunkEntry {
        uint64_t owner_id = 0;
        LocalChunk chunk{};
        ThreadChunkEntry* next = nullptr;
    };

    struct ThreadChunkListOwner {
        ThreadChunkEntry* head = nullptr;

        ~ThreadChunkListOwner() {
            while (head) {
                ThreadChunkEntry* next = head->next;
                delete head;
                head = next;
            }
        }
    };

    static constexpr size_t calculate_padding(uintptr_t address, size_t alignment) noexcept {
        return (alignment - (address & (alignment - 1))) & (alignment - 1);
    }

    static constexpr bool is_power_of_two(size_t value) noexcept {
        return value != 0 && (value & (value - 1)) == 0;
    }

    static constexpr size_t LOCAL_CHUNK_SIZE = 4 * 1024;
    static constexpr size_t MAX_LOCAL_ALLOCATION = 1024;
    static constexpr size_t MAX_LOCAL_ALIGNMENT = 64;

public:
    static constexpr size_t PAGE_SIZE = 4096;
    static constexpr size_t INITIAL_COMMIT = 64 * 1024;
    static constexpr size_t UNIVERSAL_MAX_ALIGN = 64;
    static constexpr bool blocks_on_seal = ReleasePolicy::blocks_on_seal;
    static constexpr bool tracks_destructors = CleanupPolicy::tracks_destructors;
    static constexpr size_t local_chunk_size = LOCAL_CHUNK_SIZE;
    static constexpr size_t max_local_allocation = MAX_LOCAL_ALLOCATION;

    class ScopedOperation final {
    public:
        explicit ScopedOperation(ConcurrentArenaCore& arena) noexcept
            : arena_(&arena),
              active_(arena_->release_policy().enter_operation()) {
        }

        ~ScopedOperation() {
            if (active_) {
                arena_->release_policy().leave_operation();
            }
        }

        ScopedOperation(const ScopedOperation&) = delete;
        ScopedOperation& operator=(const ScopedOperation&) = delete;

        ScopedOperation(ScopedOperation&& other) noexcept
            : arena_(other.arena_),
              active_(other.active_) {
            other.arena_ = nullptr;
            other.active_ = false;
        }

        ScopedOperation& operator=(ScopedOperation&&) = delete;

        [[nodiscard]] bool active() const noexcept {
            return active_;
        }

        explicit operator bool() const noexcept {
            return active_;
        }

    private:
        ConcurrentArenaCore* arena_ = nullptr;
        bool active_ = false;
    };

    ~ConcurrentArenaCore() {
        release();
        if (base_ptr_) {
            VirtualFree(base_ptr_, 0, MEM_RELEASE);
        }
    }

    ConcurrentArenaCore(const ConcurrentArenaCore&) = delete;
    ConcurrentArenaCore& operator=(const ConcurrentArenaCore&) = delete;
    ConcurrentArenaCore(ConcurrentArenaCore&&) = delete;
    ConcurrentArenaCore& operator=(ConcurrentArenaCore&&) = delete;

    struct StatsSnapshot {
        size_t local_chunk_hits = 0;
        size_t local_chunk_refills = 0;
        size_t global_allocations = 0;
        size_t failed_allocations = 0;
        size_t commit_calls = 0;
        size_t release_calls = 0;
    };

    [[nodiscard]] static std::unique_ptr<ConcurrentArenaCore> create(size_t reserve_size) {
        void* base = VirtualAlloc(nullptr, reserve_size, MEM_RESERVE, PAGE_NOACCESS);
        if (!base) {
            return nullptr;
        }

        const size_t initial_commit = std::min(reserve_size, INITIAL_COMMIT);
        if (!VirtualAlloc(base, initial_commit, MEM_COMMIT, PAGE_READWRITE)) {
            VirtualFree(base, 0, MEM_RELEASE);
            return nullptr;
        }

        return std::unique_ptr<ConcurrentArenaCore>(
            new ConcurrentArenaCore(base, reserve_size, initial_commit));
    }

    [[nodiscard]] ScopedOperation acquire_operation() noexcept {
        return ScopedOperation(*this);
    }

    void* allocate_raw_aligned(size_t size, size_t alignment = UNIVERSAL_MAX_ALIGN) {
        auto op = acquire_operation();
        if (!op) {
            return nullptr;
        }
        return allocate_raw_aligned(op, size, alignment);
    }

    void* allocate_raw_aligned(ScopedOperation&, size_t size, size_t alignment = UNIVERSAL_MAX_ALIGN) {
        return allocate_raw_aligned_inside_operation(size, alignment);
    }

    template <typename T, typename... Args>
    T* construct(Args&&... args) {
        auto op = acquire_operation();
        if (!op) {
            return nullptr;
        }
        return construct<T>(op, std::forward<Args>(args)...);
    }

    template <typename T, typename... Args>
    T* construct(ScopedOperation&, Args&&... args) {
        void* cleanup_node_memory = nullptr;

        if constexpr (CleanupPolicy::tracks_destructors && !std::is_trivially_destructible_v<T>) {
            cleanup_node_memory = allocate_raw_aligned_inside_operation(CleanupPolicy::cleanup_node_size,
                                                                        CleanupPolicy::cleanup_node_alignment);
            if (!cleanup_node_memory) {
                return nullptr;
            }
        }

        void* object_memory = allocate_raw_aligned_inside_operation(sizeof(T), alignof(T));
        if (!object_memory) {
            return nullptr;
        }

        T* object = new (object_memory) T(std::forward<Args>(args)...);

        if constexpr (CleanupPolicy::tracks_destructors && !std::is_trivially_destructible_v<T>) {
            cleanup_policy().publish_cleanup(cleanup_node_memory,
                                             object,
                                             [](void* ptr) { static_cast<T*>(ptr)->~T(); });
        }

        return object;
    }

    void release() noexcept {
        release_calls_.fetch_add(1, std::memory_order_relaxed);
        if (!base_ptr_) {
            return;
        }

        std::scoped_lock lock(release_mutex_);

        release_policy().begin_release();
        release_policy().wait_for_quiescence();

        cleanup_policy().run_cleanup();

        const size_t committed = committed_size_.load(std::memory_order_acquire);
        if (committed > initial_commit_size_) {
            void* decommit_addr = static_cast<char*>(base_ptr_) + initial_commit_size_;
            const size_t decommit_size = committed - initial_commit_size_;

#pragma warning(suppress: 6250)
            VirtualFree(decommit_addr, decommit_size, MEM_DECOMMIT);
            committed_size_.store(initial_commit_size_, std::memory_order_release);
        }

        offset_.store(0, std::memory_order_release);
        cleanup_policy().reset_cleanup();
        current_epoch_.fetch_add(1, std::memory_order_acq_rel);

        release_policy().end_release();
    }

    [[nodiscard]] size_t get_epoch() const noexcept {
        return current_epoch_.load(std::memory_order_acquire);
    }

    [[nodiscard]] size_t get_reserved_size() const noexcept {
        return reserved_size_;
    }

    [[nodiscard]] size_t get_committed_size() const noexcept {
        return committed_size_.load(std::memory_order_acquire);
    }

    [[nodiscard]] size_t get_used_size() const noexcept {
        return offset_.load(std::memory_order_acquire);
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return release_policy().is_sealed();
    }

    [[nodiscard]] StatsSnapshot get_stats() const noexcept {
        return StatsSnapshot{
            .local_chunk_hits = local_chunk_hits_.load(std::memory_order_relaxed),
            .local_chunk_refills = local_chunk_refills_.load(std::memory_order_relaxed),
            .global_allocations = global_allocations_.load(std::memory_order_relaxed),
            .failed_allocations = failed_allocations_.load(std::memory_order_relaxed),
            .commit_calls = commit_calls_.load(std::memory_order_relaxed),
            .release_calls = release_calls_.load(std::memory_order_relaxed)
        };
    }

    void reset_stats() noexcept {
        local_chunk_hits_.store(0, std::memory_order_relaxed);
        local_chunk_refills_.store(0, std::memory_order_relaxed);
        global_allocations_.store(0, std::memory_order_relaxed);
        failed_allocations_.store(0, std::memory_order_relaxed);
        commit_calls_.store(0, std::memory_order_relaxed);
        release_calls_.store(0, std::memory_order_relaxed);
    }

private:

    ConcurrentArenaCore(void* base, size_t reserve_size, size_t initial_commit_size) noexcept
        : base_ptr_(base),
          reserved_size_(reserve_size),
          initial_commit_size_(initial_commit_size),
          owner_id_(next_chunk_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          committed_size_(initial_commit_size) {
    }

    [[nodiscard]] void* allocate_raw_aligned_inside_operation(size_t size, size_t alignment) {
        if (size == 0 || !is_power_of_two(alignment)) {
            failed_allocations_.fetch_add(1, std::memory_order_relaxed);
            return nullptr;
        }

        const size_t arena_epoch = current_epoch_.load(std::memory_order_acquire);

        // Fast path:
        // Small allocations are first attempted from the thread-local bump chunk.
        // This avoids touching the global bump pointer on the hot path.
        if (void* local = try_allocate_from_local_chunk(size, alignment, arena_epoch)) {
            return local;
        }

        // Slow path:
        // Fallback to the shared global bump pointer if the local chunk is
        // exhausted or if the allocation is too large / too strictly aligned.
        void* global = allocate_raw_aligned_global_inside_operation(size, alignment);
        if (!global) {
            failed_allocations_.fetch_add(1, std::memory_order_relaxed);
        }
        return global;
    }

    [[nodiscard]] void* try_allocate_from_local_chunk(size_t size,
                                                      size_t alignment,
                                                      size_t arena_epoch) noexcept {
        if (size > MAX_LOCAL_ALLOCATION || alignment > MAX_LOCAL_ALIGNMENT) {
            return nullptr;
        }

        LocalChunk& chunk = get_local_chunk(arena_epoch);

        for (;;) {
            if (chunk.current == nullptr || chunk.current == chunk.end) {
                if (!refill_local_chunk(chunk)) {
                    return nullptr;
                }
            }

            const uintptr_t current_address = reinterpret_cast<uintptr_t>(chunk.current);
            const size_t padding = calculate_padding(current_address, alignment);

            if (static_cast<size_t>(chunk.end - chunk.current) < padding + size) {
                chunk.current = chunk.end;
                continue;
            }

            char* result = chunk.current + padding;
            chunk.current = result + size;
            local_chunk_hits_.fetch_add(1, std::memory_order_relaxed);
            return result;
        }
    }

    [[nodiscard]] bool refill_local_chunk(LocalChunk& chunk) {
        void* memory = allocate_raw_aligned_global_inside_operation(LOCAL_CHUNK_SIZE, UNIVERSAL_MAX_ALIGN);
        if (!memory) {
            chunk.current = nullptr;
            chunk.end = nullptr;
            return false;
        }

        chunk.current = static_cast<char*>(memory);
        chunk.end = chunk.current + LOCAL_CHUNK_SIZE;
        local_chunk_refills_.fetch_add(1, std::memory_order_relaxed);
        return true;
    }

    [[nodiscard]] LocalChunk& get_local_chunk(size_t arena_epoch) {
        for (ThreadChunkEntry* entry = tls_chunks_.head; entry != nullptr; entry = entry->next) {
            if (entry->owner_id == owner_id_) {
                sync_local_chunk_epoch(entry->chunk, arena_epoch);
                return entry->chunk;
            }
        }

        ThreadChunkEntry* entry = new ThreadChunkEntry{};
        entry->owner_id = owner_id_;
        entry->chunk.epoch = arena_epoch;
        entry->next = tls_chunks_.head;
        tls_chunks_.head = entry;

        return entry->chunk;
    }

    static void sync_local_chunk_epoch(LocalChunk& chunk, size_t arena_epoch) noexcept {
        if (chunk.epoch != arena_epoch) {
            chunk.current = nullptr;
            chunk.end = nullptr;
            chunk.epoch = arena_epoch;
        }
    }

    [[nodiscard]] void* allocate_raw_aligned_global_inside_operation(size_t size, size_t alignment) {
        if (size == 0 || !is_power_of_two(alignment)) {
            return nullptr;
        }

        for (;;) {
            const size_t current_offset = offset_.load(std::memory_order_relaxed);

            const uintptr_t current_address = reinterpret_cast<uintptr_t>(base_ptr_) + current_offset;
            const size_t padding = calculate_padding(current_address, alignment);

            if (current_offset > reserved_size_ || padding > reserved_size_ - current_offset) {
                return nullptr;
            }

            const size_t aligned_offset = current_offset + padding;
            if (aligned_offset > reserved_size_ || size > reserved_size_ - aligned_offset) {
                return nullptr;
            }

            const size_t next_offset = aligned_offset + size;

            if (next_offset > committed_size_.load(std::memory_order_acquire)) {
                if (!ensure_committed(next_offset)) {
                    return nullptr;
                }
            }

            size_t expected = current_offset;
            if (offset_.compare_exchange_weak(expected,
                                              next_offset,
                                              std::memory_order_acq_rel,
                                              std::memory_order_relaxed)) {
                global_allocations_.fetch_add(1, std::memory_order_relaxed);
                return static_cast<char*>(base_ptr_) + aligned_offset;
            }
        }
    }

    [[nodiscard]] bool ensure_committed(size_t required_size) {
        size_t committed = committed_size_.load(std::memory_order_acquire);
        if (required_size <= committed) {
            return true;
        }

        std::scoped_lock lock(commit_mutex_);

        committed = committed_size_.load(std::memory_order_relaxed);
        if (required_size <= committed) {
            return true;
        }

        const size_t needed = required_size - committed;
        const size_t chunk_size = std::max(INITIAL_COMMIT, PAGE_SIZE);
        size_t commit_step = ((needed + chunk_size - 1) / chunk_size) * chunk_size;

        if (committed + commit_step > reserved_size_) {
            commit_step = reserved_size_ - committed;
        }

        void* commit_addr = static_cast<char*>(base_ptr_) + committed;
        commit_calls_.fetch_add(1, std::memory_order_relaxed);
        if (!VirtualAlloc(commit_addr, commit_step, MEM_COMMIT, PAGE_READWRITE)) {
            return false;
        }

        committed_size_.store(committed + commit_step, std::memory_order_release);
        return true;
    }

    CleanupPolicy& cleanup_policy() noexcept {
        return static_cast<CleanupPolicy&>(*this);
    }

    const CleanupPolicy& cleanup_policy() const noexcept {
        return static_cast<const CleanupPolicy&>(*this);
    }

    ReleasePolicy& release_policy() noexcept {
        return static_cast<ReleasePolicy&>(*this);
    }

    const ReleasePolicy& release_policy() const noexcept {
        return static_cast<const ReleasePolicy&>(*this);
    }

private:
    inline static thread_local ThreadChunkListOwner tls_chunks_{};
    inline static std::atomic<uint64_t> next_chunk_owner_id_{ 1 };

    void* base_ptr_ = nullptr;
    size_t reserved_size_ = 0;
    size_t initial_commit_size_ = 0;
    uint64_t owner_id_ = 0;

    std::atomic<size_t> committed_size_{ 0 };
    std::atomic<size_t> offset_{ 0 };
    std::atomic<size_t> current_epoch_{ 0 };

    std::mutex commit_mutex_;
    std::mutex release_mutex_;

    // Lightweight instrumentation counters.
    // All counters are monotonic and intentionally sampled with relaxed ordering.
    std::atomic<size_t> local_chunk_hits_{ 0 };
    std::atomic<size_t> local_chunk_refills_{ 0 };
    std::atomic<size_t> global_allocations_{ 0 };
    std::atomic<size_t> failed_allocations_{ 0 };
    std::atomic<size_t> commit_calls_{ 0 };
    std::atomic<size_t> release_calls_{ 0 };
};

using ConcurrentArena = ConcurrentArenaCore<AtomicCleanupStackPolicy, QuiescentReleasePolicy>;
using ConcurrentArenaTryEnter = ConcurrentArenaCore<AtomicCleanupStackPolicy, TryEnterReleasePolicy>;
using ConcurrentArenaThreadLocalCleanup = ConcurrentArenaCore<ThreadLocalCleanupPolicy, QuiescentReleasePolicy>;

static_assert(ConcurrentArenaCleanupPolicy<NoCleanupPolicy>);
static_assert(ConcurrentArenaCleanupPolicy<AtomicCleanupStackPolicy>);
static_assert(ConcurrentArenaCleanupPolicy<ThreadLocalCleanupPolicy>);

static_assert(ConcurrentArenaReleasePolicy<QuiescentReleasePolicy>);
static_assert(ConcurrentArenaReleasePolicy<TryEnterReleasePolicy>);

static_assert(ConcurrentArena::blocks_on_seal);
static_assert(!ConcurrentArenaTryEnter::blocks_on_seal);
static_assert(ConcurrentArenaThreadLocalCleanup::tracks_destructors);

static_assert(!std::copy_constructible<ConcurrentArena>);
static_assert(!std::movable<ConcurrentArena>);

static_assert(!std::copy_constructible<ConcurrentArenaTryEnter>);
static_assert(!std::movable<ConcurrentArenaTryEnter>);

static_assert(!std::copy_constructible<ConcurrentArenaThreadLocalCleanup>);
static_assert(!std::movable<ConcurrentArenaThreadLocalCleanup>);
