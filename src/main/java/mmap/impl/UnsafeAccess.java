package mmap.impl;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

@SuppressWarnings("restriction")
class UnsafeAccess {

    static final Unsafe unsafe;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private UnsafeAccess() {
    }
}
