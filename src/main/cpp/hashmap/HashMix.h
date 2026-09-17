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
#pragma once

#include <cstddef>

namespace hashmap_detail {

// Stafford's "variant 13" bit mixing function. The maps pick a shard from the low bits of the
// hash; std::hash<int> is the identity with libstdc++, so without mixing, keys that share their
// low bits (e.g. multiples of 32 or aligned pointers) would all land in the same shard.
[[nodiscard]] constexpr std::size_t mix_hash(std::size_t h) noexcept {
    h = (h ^ (h >> 30)) * 0xbf58476d1ce4e5b9ULL;
    h = (h ^ (h >> 27)) * 0x94d049bb133111ebULL;
    h = h ^ (h >> 31);
    return h;
}

} // namespace hashmap_detail
