#pragma once

#include "bitfields.h"

// Example:

enum class Characteristics : uint8_t {
    None = 0,
    Fast = 1 << 0, // 1
    Strong = 1 << 1, // 2
    Invisible = 1 << 2, // 4
    Flying = 1 << 3  // 8
};

ENABLE_BITMASK_OPERATORS(Characteristics)

