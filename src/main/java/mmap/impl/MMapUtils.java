package mmap.impl;

import sun.misc.Unsafe;

import java.io.FileDescriptor;
import java.lang.reflect.Field;

/**
 * Native utilities for memory-mapped files.
 */
@SuppressWarnings("restriction")
public final class MMapUtils {

    // Note that on Windows this will always return false
    // (irrespective of whether the pages have been loaded or not)!
    public static boolean isLoaded(long address, long size) {
        if ((address == 0L) || (size == 0L)) {
            return true;
        }
        long offset = mappingOffset(address);
        long length = mappingLength(offset, size);
        long pageCount = Native.pageCount(length);
        if (pageCount > Integer.MAX_VALUE) {
            return false;
        }
        return isLoaded0(mappingAddress(address, offset), length, pageCount);
    }

    public static boolean load(long address, long size) {
        if ((address == 0L) || (size == 0L)) {
            return false;
        }
        long offset = mappingOffset(address);
        long length = mappingLength(offset, size);
        boolean success = load0(mappingAddress(address, offset), length);
        if (!success) {
            return false;
        }

        long count = Native.pageCount(length);
        if (count > Integer.MAX_VALUE) {
            return true;
        }
        // Read a byte from each page to bring it into memory. A checksum
        // is computed as we go along to prevent the compiler from otherwise
        // considering the loop as dead code.
        Unsafe U = Native.unsafe();
        int ps = Native.pageSize();
        long a = mappingAddress(address, offset);
        byte x = 0;
        for (long i = 0L; i < count; i++) {
            x ^= U.getByte(a);
            a += ps;
        }
        if (unused != 0) {
            unused = x;
        }
        return true;
    }

    // not used, but a potential target for a store, see load() for details.
    private static byte unused;

    public static boolean unload(long address, long size) {
        if ((address == 0L) || (size == 0L)) {
            return false;
        }
        long offset = mappingOffset(address);
        long length = mappingLength(offset, size);
        return unload0(mappingAddress(address, offset), length);
    }

    public static boolean force(FileDescriptor fd, long address, long index, long length) {
        // force writeback via file descriptor
        long offset = mappingOffset(address, index);
        // only the Windows implementation of 'force0()' needs the raw fd
        long rawfd = 0L;
        if (Native.isWindows()) {
            rawfd = getFileDescriptor(fd);
        }
        return force0(rawfd, mappingAddress(address, offset, index), mappingLength(offset, length));
    }

    // native methods

    private static native boolean isLoaded0(long address, long length, long pageCount);

    private static native boolean load0(long address, long length);

    private static native boolean unload0(long address, long length);

    private static native boolean force0(long fd, long address, long length);

    // utility methods

    // Returns the distance (in bytes) of the buffer start from the
    // largest page aligned address of the mapping less than or equal
    // to the start address.
    private static long mappingOffset(long address) {
        return mappingOffset(address, 0L);
    }

    // Returns the distance (in bytes) of the buffer element
    // identified by index from the largest page aligned address of
    // the mapping less than or equal to the element address.
    private static long mappingOffset(long address, long index) {
        int ps = Native.pageSize();
        long indexAddress = address + index;
        long baseAddress = alignDown(indexAddress, ps);
        return indexAddress - baseAddress;
    }

    // Given an offset previously obtained from calling
    // mappingOffset() returns the largest page aligned address of the
    // mapping less than or equal to the buffer start address.
    private static long mappingAddress(long address, long mappingOffset) {
        return mappingAddress(address, mappingOffset, 0L);
    }

    // Given an offset previously obtained from calling
    // mappingOffset(index) returns the largest page aligned address
    // of the mapping less than or equal to the address of the buffer
    // element identified by index.
    private static long mappingAddress(long address, long mappingOffset, long index) {
        long indexAddress = address + index;
        return indexAddress - mappingOffset;
    }

    // given a mappingOffset previously obtained from calling
    // mappingOffset(index) return that offset added to the supplied
    // length.
    private static long mappingLength(long mappingOffset, long length) {
        return length + mappingOffset;
    }

    // align address down to page size
    private static long alignDown(long address, int pageSize) {
        // pageSize must be a power of 2
        return address & ~(pageSize - 1);
    }

    private static long getFileDescriptor(FileDescriptor fd) {
        try {
            if (Native.isWindows()) {
                return FD_WIN.getLong(fd);
            } else {
                return FD_LINUX.getInt(fd);
            }
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    private static final Field FD_WIN;
    private static final Field FD_LINUX;
    static {
        try {
            if (Native.isWindows()) {
                Field f = FileDescriptor.class.getDeclaredField("handle");
                f.setAccessible(true);
                FD_WIN = f;
                FD_LINUX = null;
            } else {
                Field f = FileDescriptor.class.getDeclaredField("fd");
                f.setAccessible(true);
                FD_WIN = null;
                FD_LINUX = f;
            }
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    private MMapUtils() {
        throw new AssertionError();
    }
}
