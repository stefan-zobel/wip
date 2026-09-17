#pragma once

#ifndef NOMINMAX
#define NOMINMAX
#endif

#include <algorithm>
#include <array>
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

    [[nodiscard]] bool prepare_cleanup() noexcept {
        return true;
    }

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

    [[nodiscard]] bool prepare_cleanup() noexcept {
        return true;
    }

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

#if defined(_MSC_VER)
#define FK_ARENA_NOINLINE __declspec(noinline)
#else
#define FK_ARENA_NOINLINE [[gnu::noinline]]
#endif

namespace concurrent_arena_detail {

// Shared between an object and the thread-local entries that threads keep for it (chunks, pool
// caches, cleanup lists). The object retires it in its destructor; entries of retired owners are
// pruned and never run their thread-exit hook.
class OwnerLifetime {
public:
    [[nodiscard]] bool alive() const noexcept {
        return alive_.load(std::memory_order_acquire);
    }

    // Called by the owner's destructor. Waits until no thread-exit hook runs for the owner.
    void retire() noexcept {
        std::scoped_lock lock(mutex_);
        alive_.store(false);
    }

    // Runs 'hook' only while the owner is alive; the owner's destructor waits meanwhile.
    template <typename Hook>
    void run_if_alive(Hook&& hook) noexcept {
        std::scoped_lock lock(mutex_);
        if (alive_.load(std::memory_order_acquire)) {
            hook();
        }
    }

private:
    std::mutex mutex_;
    std::atomic<bool> alive_{ true };
};

// Thread-local list of per-owner state of one class. Lookups of a live owner (the fast path) are
// unchanged: a linear search by owner id. Creating an entry (the slow path) first removes the entries
// of owners that no longer exist, so a thread's list stays as short as the number of live owners it
// uses. At thread exit, every entry of a live owner runs its hook, e.g. to return
// cached blocks to a pool.
template <typename Payload>
class ThreadEntryList {
public:
    using ExitHook = void (*)(void* owner, Payload& payload) noexcept;

    // Hot fields first: a lookup reads owner_id and then uses the payload, which should share the
    // cache line with it. The fields for pruning and thread exit are only used on slow paths.
    struct Entry {
        uint64_t owner_id = 0;
        Payload payload{};
        Entry* next = nullptr;
        void* owner = nullptr;
        ExitHook on_thread_exit = nullptr;
        std::shared_ptr<OwnerLifetime> lifetime;
    };

    ThreadEntryList() noexcept = default;
    ThreadEntryList(const ThreadEntryList&) = delete;
    ThreadEntryList& operator=(const ThreadEntryList&) = delete;

    ~ThreadEntryList() {
        while (head_) {
            Entry* entry = head_;
            head_ = entry->next;
            if (entry->on_thread_exit) {
                entry->lifetime->run_if_alive([entry] { entry->on_thread_exit(entry->owner, entry->payload); });
            }
            delete entry;
        }
    }

    // An owner only looks up its own entry while it is alive, so no liveness check is needed here.
    [[nodiscard]] Entry* find(uint64_t owner_id) const noexcept {
        for (Entry* entry = head_; entry != nullptr; entry = entry->next) {
            if (entry->owner_id == owner_id) {
                return entry;
            }
        }
        return nullptr;
    }

    // Returns nullptr if memory is exhausted. Kept out of line, so that the lookups of the owners
    // (find() plus this call on a miss) stay small enough to be inlined into their fast paths.
    [[nodiscard]] FK_ARENA_NOINLINE Entry* create(uint64_t owner_id,
                                void* owner,
                                ExitHook on_thread_exit,
                                const std::shared_ptr<OwnerLifetime>& lifetime) noexcept {
        prune_retired_owners();
        Entry* entry = new (std::nothrow) Entry{};
        if (!entry) {
            return nullptr;
        }
        entry->owner_id = owner_id;
        entry->owner = owner;
        entry->on_thread_exit = on_thread_exit;
        entry->lifetime = lifetime;
        entry->next = head_;
        head_ = entry;
        return entry;
    }

    // Removes the entry of an owner that is being destroyed on this thread (no hook).
    void remove(uint64_t owner_id) noexcept {
        for (Entry** link = &head_; *link != nullptr; link = &(*link)->next) {
            if ((*link)->owner_id == owner_id) {
                Entry* entry = *link;
                *link = entry->next;
                delete entry;
                return;
            }
        }
    }

    [[nodiscard]] size_t size() const noexcept {
        size_t count = 0;
        for (Entry* entry = head_; entry != nullptr; entry = entry->next) {
            ++count;
        }
        return count;
    }

private:
    void prune_retired_owners() noexcept {
        Entry** link = &head_;
        while (*link != nullptr) {
            Entry* entry = *link;
            if (entry->lifetime->alive()) {
                link = &entry->next;
            } else {
                *link = entry->next;
                delete entry;
            }
        }
    }

    Entry* head_ = nullptr;
};

} // namespace concurrent_arena_detail

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

    // Thread-local state: the cleanup list this thread appends to
    struct ThreadState {
        LocalList* list = nullptr;
    };

    inline static thread_local concurrent_arena_detail::ThreadEntryList<ThreadState> tls_cache_{};
    inline static std::atomic<uint64_t> next_owner_id_{ 1 };

    static constexpr bool tracks_destructors = true;
    static constexpr size_t cleanup_node_size = sizeof(CleanupNode);
    static constexpr size_t cleanup_node_alignment = alignof(CleanupNode);

    // Throws std::bad_alloc if the shared state cannot be allocated.
    ThreadLocalCleanupPolicy()
        : owner_id_(next_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          registry_(std::make_shared<RegistryState>()),
          lifetime_(std::make_shared<concurrent_arena_detail::OwnerLifetime>()) {
    }

    ~ThreadLocalCleanupPolicy() {
        lifetime_->retire();
        tls_cache_.remove(owner_id_);
    }

    ThreadLocalCleanupPolicy(const ThreadLocalCleanupPolicy&) = delete;
    ThreadLocalCleanupPolicy& operator=(const ThreadLocalCleanupPolicy&) = delete;
    ThreadLocalCleanupPolicy(ThreadLocalCleanupPolicy&&) = delete;
    ThreadLocalCleanupPolicy& operator=(ThreadLocalCleanupPolicy&&) = delete;

    // Diagnostics (tests): number of thread-local entries of this class on the calling thread.
    [[nodiscard]] static size_t thread_entry_count() noexcept {
        return tls_cache_.size();
    }

    // Creates this thread's cleanup list before an object is constructed, so that publish_cleanup()
    // cannot fail afterwards. Returns false if memory is exhausted.
    [[nodiscard]] bool prepare_cleanup() noexcept {
        return get_or_create_local_list() != nullptr;
    }

    void publish_cleanup(void* node_memory, void* object_ptr, DestructorFunc dtor) noexcept {
        CleanupNode* node = new (node_memory) CleanupNode{ object_ptr, dtor, nullptr };
        LocalList* list = tls_cache_.find(owner_id_)->payload.list;  // created by prepare_cleanup()
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
    // At thread exit the list stays in the registry (its destructors run at the next release) and
    // may be bound by another thread.
    static void unbind_list_at_thread_exit(void* owner, ThreadState& state) noexcept {
        auto* policy = static_cast<ThreadLocalCleanupPolicy*>(owner);
        std::scoped_lock lock(policy->registry_->mutex);
        state.list->bound = false;
    }

    [[nodiscard]] LocalList* get_or_create_local_list() noexcept {
        if (auto* entry = tls_cache_.find(owner_id_)) {
            return entry->payload.list;
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
                list = new (std::nothrow) LocalList{};
                if (!list) {
                    return nullptr;
                }
                list->bound = true;
                list->next = registry_->head;
                registry_->head = list;
            }
        }

        auto* entry = tls_cache_.create(owner_id_, this, &unbind_list_at_thread_exit, lifetime_);
        if (!entry) {
            std::scoped_lock lock(registry_->mutex);
            list->bound = false;
            return nullptr;
        }
        entry->payload.list = list;
        return list;
    }

    uint64_t owner_id_ = 0;
    std::shared_ptr<RegistryState> registry_;
    std::shared_ptr<concurrent_arena_detail::OwnerLifetime> lifetime_;
};

namespace concurrent_arena_detail {

// Counts the operations in progress for the release policies and implements the handshake with
// release(). Instead of one shared counter, every thread registers on one of STRIPES counters,
// each on its own cache line, so operations of different threads do not write the same line.
//
// Handshake (Dekker): an operation increments its stripe and then loads sealed_; release() stores
// sealed_ and then loads all stripes. Every operation that is still active has incremented its
// stripe before it loaded sealed_ == false, i.e. before sealed_ was set, so release() sees that
// increment when it sums the stripes afterwards. The sum is taken stripe by stripe and not
// atomically: it can only miss decrements (wait longer), never an increment of an active operation.
// All counter updates are locked instructions and sealed_ / release_waiting_ are stored
// sequentially consistent (xchg): a plain store could still sit in the store buffer while this
// thread already loads the stripes.
class OperationQuiescence {
public:
    static constexpr size_t STRIPES = 16;

    // Registers an operation on the stripe of the calling thread and returns that stripe.
    [[nodiscard]] size_t register_operation() noexcept {
        const size_t stripe = current_thread_stripe();
        stripes_[stripe].count.fetch_add(1, std::memory_order_acq_rel);
        return stripe;
    }

    // Deregisters an operation from the stripe returned by register_operation().
    void deregister_operation(size_t stripe) noexcept {
        // Decrement first, then load release_waiting_: if the flag is still unset, release() stores
        // it later and then sums the stripes, which already contain this decrement. Otherwise the
        // generation changes before the notification, so a release() that loaded the generation
        // before its sum does not fall asleep. Without a waiting release() nothing is written.
        stripes_[stripe].count.fetch_sub(1, std::memory_order_acq_rel);
        if (release_waiting_.load(std::memory_order_acquire)) {
            wake_generation_.fetch_add(1, std::memory_order_acq_rel);
            wake_generation_.notify_all();
        }
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return sealed_.load(std::memory_order_acquire);
    }

    // Registers an operation unless a release() is in progress; never waits.
    [[nodiscard]] bool try_register_operation(size_t& stripe) noexcept {
        if (is_sealed()) {
            return false;
        }
        stripe = register_operation();
        if (is_sealed()) {
            deregister_operation(stripe);
            return false;
        }
        return true;
    }

    void wait_while_sealed() noexcept {
        while (sealed_.load(std::memory_order_acquire)) {
            sealed_.wait(true, std::memory_order_acquire);
        }
    }

    void begin_release() noexcept {
        // Sequentially consistent (serializing) store, see the class comment.
        sealed_.store(true);
    }

    void wait_for_quiescence() noexcept {
        release_waiting_.store(true);
        for (;;) {
            // Load the generation before the sum (see deregister_operation).
            const uint32_t generation = wake_generation_.load(std::memory_order_acquire);
            size_t active = 0;
            for (const Stripe& stripe : stripes_) {
                active += stripe.count.load(std::memory_order_acquire);
            }
            if (active == 0) {
                break;
            }
            wake_generation_.wait(generation, std::memory_order_acquire);
        }
        release_waiting_.store(false);
    }

    void end_release() noexcept {
        // Serializing as well, so a thread that is about to wait for sealed_ == true cannot miss
        // the change and the notification.
        sealed_.store(false);
        sealed_.notify_all();
    }

private:
    static constexpr size_t CACHE_LINE = 64;

    struct alignas(CACHE_LINE) Stripe {
        std::atomic<size_t> count{ 0 };
    };

    // Stripes are handed out round-robin to threads on their first operation, so the first
    // STRIPES threads never share a stripe. The thread_local is constant-initialized (0 = none
    // yet), which avoids a TLS initialization guard on every operation.
    [[nodiscard]] static size_t current_thread_stripe() noexcept {
        size_t stripe_plus_one = tls_stripe_plus_one_;
        if (stripe_plus_one == 0) {
            stripe_plus_one = next_stripe_.fetch_add(1, std::memory_order_relaxed) % STRIPES + 1;
            tls_stripe_plus_one_ = stripe_plus_one;
        }
        return stripe_plus_one - 1;
    }

    inline static std::atomic<size_t> next_stripe_{ 0 };
    inline static thread_local size_t tls_stripe_plus_one_ = 0;

    // Read by every operation: one cache line of its own
    alignas(CACHE_LINE) std::atomic<bool> sealed_{ false };
    std::atomic<bool> release_waiting_{ false };
    std::atomic<uint32_t> wake_generation_{ 0 };

    std::array<Stripe, STRIPES> stripes_{};
};

} // namespace concurrent_arena_detail

struct QuiescentReleasePolicy {
    static constexpr bool blocks_on_seal = true;

    // Waits while a release() is in progress. Returns true with 'stripe' set.
    bool enter_operation(size_t& stripe) noexcept {
        for (;;) {
            quiescence_.wait_while_sealed();
            stripe = quiescence_.register_operation();
            if (!quiescence_.is_sealed()) {
                return true;
            }
            quiescence_.deregister_operation(stripe);
        }
    }

    // Never waits: fails while a release() is in progress.
    bool try_enter_operation(size_t& stripe) noexcept {
        return quiescence_.try_register_operation(stripe);
    }

    void leave_operation(size_t stripe) noexcept {
        quiescence_.deregister_operation(stripe);
    }

    void begin_release() noexcept {
        quiescence_.begin_release();
    }

    void wait_for_quiescence() noexcept {
        quiescence_.wait_for_quiescence();
    }

    void end_release() noexcept {
        quiescence_.end_release();
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return quiescence_.is_sealed();
    }

private:
    concurrent_arena_detail::OperationQuiescence quiescence_;
};

struct TryEnterReleasePolicy {
    static constexpr bool blocks_on_seal = false;

    // Fails instead of waiting while a release() is in progress.
    bool enter_operation(size_t& stripe) noexcept {
        if (quiescence_.is_sealed()) {
            return false;
        }
        stripe = quiescence_.register_operation();
        if (quiescence_.is_sealed()) {
            quiescence_.deregister_operation(stripe);
            return false;
        }
        return true;
    }

    // Same as enter_operation() for this policy.
    bool try_enter_operation(size_t& stripe) noexcept {
        return quiescence_.try_register_operation(stripe);
    }

    void leave_operation(size_t stripe) noexcept {
        quiescence_.deregister_operation(stripe);
    }

    void begin_release() noexcept {
        quiescence_.begin_release();
    }

    void wait_for_quiescence() noexcept {
        quiescence_.wait_for_quiescence();
    }

    void end_release() noexcept {
        quiescence_.end_release();
    }

    [[nodiscard]] bool is_sealed() const noexcept {
        return quiescence_.is_sealed();
    }

private:
    concurrent_arena_detail::OperationQuiescence quiescence_;
};

template <typename T>
concept ConcurrentArenaCleanupPolicy =
    requires(T policy, void* node_memory, void* object_ptr, void(*dtor)(void*)) {
        typename T::DestructorFunc;
        { T::tracks_destructors } -> std::convertible_to<bool>;
        { T::cleanup_node_size } -> std::convertible_to<size_t>;
        { T::cleanup_node_alignment } -> std::convertible_to<size_t>;
        { policy.prepare_cleanup() } -> std::convertible_to<bool>;
        policy.publish_cleanup(node_memory, object_ptr, dtor);
        policy.run_cleanup();
        policy.reset_cleanup();
    };

template <typename T>
concept ConcurrentArenaReleasePolicy =
    requires(T policy, size_t& stripe) {
        { T::blocks_on_seal } -> std::convertible_to<bool>;
        { policy.enter_operation(stripe) } -> std::convertible_to<bool>;
        { policy.try_enter_operation(stripe) } -> std::convertible_to<bool>;
        policy.leave_operation(stripe);
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
        // Local chunk hits not yet added to the shared local_chunk_hits_ counter
        size_t pending_hits = 0;
    };

    // Hit counters of the fast path are batched per thread, so the fast path does not write
    // shared memory on every allocation (see StatsSnapshot).
    static constexpr size_t STATS_BATCH = 64;

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
              active_(arena_->release_policy().enter_operation(stripe_)) {
        }

        struct NonBlocking {};

        // Never waits: inactive while a release() is in progress.
        ScopedOperation(ConcurrentArenaCore& arena, NonBlocking) noexcept
            : arena_(&arena),
              active_(arena_->release_policy().try_enter_operation(stripe_)) {
        }

        ~ScopedOperation() {
            if (active_) {
                arena_->release_policy().leave_operation(stripe_);
            }
        }

        ScopedOperation(const ScopedOperation&) = delete;
        ScopedOperation& operator=(const ScopedOperation&) = delete;

        ScopedOperation(ScopedOperation&& other) noexcept
            : arena_(other.arena_),
              stripe_(other.stripe_),
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

        // True if this operation was acquired from 'arena' (and not moved away)
        [[nodiscard]] bool belongs_to(const ConcurrentArenaCore& arena) const noexcept {
            return arena_ == &arena;
        }

    private:
        ConcurrentArenaCore* arena_ = nullptr;
        size_t stripe_ = 0;  // declared before active_: enter_operation() sets it
        bool active_ = false;
    };

    ~ConcurrentArenaCore() {
        // First wait for thread-exit hooks of other threads, then forget this thread's entry.
        lifetime_->retire();
        tls_chunks_.remove(owner_id_);
        release();
        if (base_ptr_) {
            VirtualFree(base_ptr_, 0, MEM_RELEASE);
        }
    }

    ConcurrentArenaCore(const ConcurrentArenaCore&) = delete;
    ConcurrentArenaCore& operator=(const ConcurrentArenaCore&) = delete;
    ConcurrentArenaCore(ConcurrentArenaCore&&) = delete;
    ConcurrentArenaCore& operator=(ConcurrentArenaCore&&) = delete;

    // local_chunk_hits is collected per thread and published in batches of STATS_BATCH (and on
    // every chunk refill), so it may lag behind by up to STATS_BATCH - 1 per thread. reset_stats()
    // cannot clear batches that other threads have not published yet. All other counters are exact.
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

        // The arena object and its policies allocate; without memory the reservation is given back.
        try {
            return std::unique_ptr<ConcurrentArenaCore>(
                new ConcurrentArenaCore(base, reserve_size, initial_commit));
        } catch (const std::bad_alloc&) {
            VirtualFree(base, 0, MEM_RELEASE);
            return nullptr;
        }
    }

    [[nodiscard]] ScopedOperation acquire_operation() noexcept {
        return ScopedOperation(*this);
    }

    // Like acquire_operation(), but never waits: the operation is inactive while a release() is
    // in progress.
    [[nodiscard]] ScopedOperation try_acquire_operation() noexcept {
        return ScopedOperation(*this, typename ScopedOperation::NonBlocking{});
    }

    void* allocate_raw_aligned(size_t size, size_t alignment = UNIVERSAL_MAX_ALIGN) {
        auto op = acquire_operation();
        if (!op) {
            return nullptr;
        }
        return allocate_raw_aligned(op, size, alignment);
    }

    // 'op' must be an active operation of this arena; otherwise nothing protects the
    // allocation against a concurrent release() and nullptr is returned.
    void* allocate_raw_aligned(ScopedOperation& op, size_t size, size_t alignment = UNIVERSAL_MAX_ALIGN) {
        if (!is_valid_operation(op)) {
            return nullptr;
        }
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

    // 'op' must be an active operation of this arena (see allocate_raw_aligned).
    template <typename T, typename... Args>
    T* construct(ScopedOperation& op, Args&&... args) {
        if (!is_valid_operation(op)) {
            return nullptr;
        }

        void* cleanup_node_memory = nullptr;

        if constexpr (CleanupPolicy::tracks_destructors && !std::is_trivially_destructible_v<T>) {
            // Everything that can fail happens before T is constructed: an object whose
            // destructor cannot be registered must not exist.
            if (!cleanup_policy().prepare_cleanup()) {
                failed_allocations_.fetch_add(1, std::memory_order_relaxed);
                return nullptr;
            }
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

    // Diagnostics (tests): number of thread-local entries of this class on the calling thread.
    [[nodiscard]] static size_t thread_entry_count() noexcept {
        return tls_chunks_.size();
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

    // Throws std::bad_alloc (the policies and the lifetime allocate); create() handles it.
    ConcurrentArenaCore(void* base, size_t reserve_size, size_t initial_commit_size)
        : base_ptr_(base),
          reserved_size_(reserve_size),
          initial_commit_size_(initial_commit_size),
          owner_id_(next_chunk_owner_id_.fetch_add(1, std::memory_order_relaxed)),
          committed_size_(initial_commit_size),
          lifetime_(std::make_shared<concurrent_arena_detail::OwnerLifetime>()) {
    }

    [[nodiscard]] bool is_valid_operation(const ScopedOperation& op) noexcept {
        if (op.active() && op.belongs_to(*this)) {
            return true;
        }
        failed_allocations_.fetch_add(1, std::memory_order_relaxed);
        return false;
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

        LocalChunk* chunk_entry = get_local_chunk(arena_epoch);
        if (!chunk_entry) [[unlikely]] {
            return nullptr;  // no memory for the thread-local entry: use the shared bump pointer
        }
        LocalChunk& chunk = *chunk_entry;

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
            if (++chunk.pending_hits == STATS_BATCH) {
                publish_local_chunk_hits(chunk);
            }
            return result;
        }
    }

    // Thread-exit hook: the hits of a thread that ends are not lost.
    static void publish_hits_at_thread_exit(void* owner, LocalChunk& chunk) noexcept {
        static_cast<ConcurrentArenaCore*>(owner)->publish_local_chunk_hits(chunk);
    }

    void publish_local_chunk_hits(LocalChunk& chunk) noexcept {
        if (chunk.pending_hits != 0) {
            local_chunk_hits_.fetch_add(chunk.pending_hits, std::memory_order_relaxed);
            chunk.pending_hits = 0;
        }
    }

    [[nodiscard]] bool refill_local_chunk(LocalChunk& chunk) {
        publish_local_chunk_hits(chunk);
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

    // Returns nullptr if the thread has no entry yet and none can be allocated.
    [[nodiscard]] LocalChunk* get_local_chunk(size_t arena_epoch) noexcept {
        if (auto* entry = tls_chunks_.find(owner_id_)) {
            sync_local_chunk_epoch(entry->payload, arena_epoch);
            return &entry->payload;
        }

        auto* entry = tls_chunks_.create(owner_id_, this, &publish_hits_at_thread_exit, lifetime_);
        if (!entry) {
            return nullptr;
        }
        entry->payload.epoch = arena_epoch;
        return &entry->payload;
    }

    void sync_local_chunk_epoch(LocalChunk& chunk, size_t arena_epoch) noexcept {
        if (chunk.epoch != arena_epoch) {
            publish_local_chunk_hits(chunk);
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
    inline static thread_local concurrent_arena_detail::ThreadEntryList<LocalChunk> tls_chunks_{};
    inline static std::atomic<uint64_t> next_chunk_owner_id_{ 1 };

    // Memory layout, grouped by access pattern (one cache line each):
    // 1. The policy base classes come first; their counters (active_operations_, cleanup_head_)
    //    are written by every operation.
    // 2. Read by every allocation, rarely written.
    // 3. Written by every allocation from the shared bump pointer (e.g. each local chunk refill).
    // 4. Cold: mutexes and rarely updated counters.
    static constexpr size_t CACHE_LINE = 64;

    alignas(CACHE_LINE) void* base_ptr_ = nullptr;
    size_t reserved_size_ = 0;
    size_t initial_commit_size_ = 0;
    uint64_t owner_id_ = 0;
    std::atomic<size_t> committed_size_{ 0 };
    std::atomic<size_t> current_epoch_{ 0 };

    alignas(CACHE_LINE) std::atomic<size_t> offset_{ 0 };
    // Lightweight instrumentation counters.
    // All counters are monotonic and intentionally sampled with relaxed ordering.
    std::atomic<size_t> local_chunk_hits_{ 0 };
    std::atomic<size_t> local_chunk_refills_{ 0 };
    std::atomic<size_t> global_allocations_{ 0 };

    alignas(CACHE_LINE) std::atomic<size_t> failed_allocations_{ 0 };
    std::atomic<size_t> commit_calls_{ 0 };
    std::atomic<size_t> release_calls_{ 0 };
    std::shared_ptr<concurrent_arena_detail::OwnerLifetime> lifetime_;
    std::mutex commit_mutex_;
    std::mutex release_mutex_;
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
