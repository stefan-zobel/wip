#include "bitfields.h"
#include "bitfields_test.h"


Characteristics test() {
    Characteristics myChar = Characteristics::Fast | Characteristics::Strong;

    // check whether the 'Fast' flag is set
    if ((myChar & Characteristics::Fast) != Characteristics::None) {
        // ...
    }

    // add 'Invisible' flag
    myChar |= Characteristics::Invisible;

    // remove 'Strong' flag
    myChar &= ~Characteristics::Strong;

    Characteristics hero = Characteristics::Fast | Characteristics::Strong | Characteristics::Flying;

    // Check whether ALL are set
    if (has_all(hero, Characteristics::Fast, Characteristics::Flying)) {
        // hero is fast and can fly
    }

    // Check whether ANY is set
    if (has_any(hero, Characteristics::Invisible, Characteristics::Flying)) {
        // hero cany fly or is invisible (or both)
    }

    // constexpr compile-time check
    static_assert(has_all(Characteristics::Fast | Characteristics::Strong, Characteristics::Fast));

    return myChar;
}
