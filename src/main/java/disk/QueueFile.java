/*
 * Copyright (C) 2010 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package disk;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * A reliable, efficient, file-based, FIFO queue. Additions and removals are
 * O(1). All operations are atomic. Writes are synchronous; data will be written
 * to disk before an operation returns. The underlying file is structured to
 * survive process and even system crashes. If an I/O exception is thrown during
 * a mutating change, the change is aborted. It is safe to continue to use a
 * {@code QueueFile} instance after an exception.
 * 
 * <p>
 * <strong>NOTE:</strong> The current implementation is built for file systems
 * that support atomic segment writes (like YAFFS). Most conventional file
 * systems don't support this; if the power goes out while writing a segment,
 * the segment will contain garbage and the file will be corrupt.
 * 
 * <p>
 * https://github.com/square/tape/blob/master/tape/src/main/java/com/squareup/tape2/QueueFile.java
 * <p>
 * Commit 9fa0a3eee397acc02cb7f08153e7dc72d9e31270
 * <p>
 * from 2017-11-30
 * 
 * @author Bob Lee (bob@squareup.com)
 */
public final class QueueFile implements Closeable {

    private static final Logger logger = Logger.getLogger(QueueFile.class.getName());

    /**
     * Leading bit set to 1 indicating a versioned header and the version of 1.
     */
    private static final int VERSIONED_HEADER = 0x80000001;

    /** The header length in bytes: Always 32 byte. */
    private static final int HEADER_LENGTH = 32;

    /** Initial file size in bytes. 128 file system blocks (= 512 KiB). */
    private static final int INITIAL_LENGTH = 128 * 4096;

    /**
     * The underlying file. Uses a ring buffer to store entries. Designed so
     * that a modification isn't committed or visible until we write the header.
     * The header is much smaller than a segment. So long as the underlying file
     * system supports atomic segment writes, changes to the queue are atomic.
     * Storing the file length ensures we can recover from a failed expansion
     * (i.e. if setting the file length succeeds but the process dies before the
     * data can be copied).
     * <p>
     * This implementation supports version 1 of the on-disk format.
     * 
     * <pre>
     * Format:
     *   32 bytes      Header
     *   ...           Data
     * 
     * Header (32 bytes):
     *   1 bit            Versioned indicator [1 = versioned]
     *   31 bits          Version, always 1
     *   8 bytes          File length
     *   4 bytes          Element count
     *   8 bytes          Head element position
     *   8 bytes          Tail element position
     * 
     * Element:
     *   4 bytes          Data length
     *   ...              Data
     * </pre>
     */
    private final RandomAccessFile raf;

    /** Keep file around for error reporting. */
    final File file;

    /** Cached file length. Always a power of 2. */
    private long fileLength;

    /** Number of elements. */
    volatile int elementCount;

    /** Pointer to first (or eldest) element. */
    Element first;

    /** Pointer to last (or newest) element. */
    private Element last;

    /** In-memory buffer. Big enough to hold the header. */
    private final byte[] header = new byte[HEADER_LENGTH];

    /**
     * The number of times this file has been structurally modified. It is
     * incremented during {@link #remove(int)} and
     * {@link #add(byte[], int, int)}.
     * 
     * Used by {@link ElementIterator} to guard against concurrent modification.
     */
    int modCount = 0;

    /**
     * When true, removing an element will also overwrite data with zero bytes.
     */
    private final boolean overwriteWithZeros;

    /** A block of nothing to write over old data. */
    private final byte[] zeroBytes;

    volatile boolean locked;
    private volatile boolean closed = false;

    static RandomAccessFile initializeFromFile(File file) throws IOException {
        if (!file.exists()) {
            // Use a temp file so we don't leave a partially-initialized file
            File tempFile = new File(file.getPath() + ".tmp");
            try (RandomAccessFile raf = open(tempFile)) {
                raf.setLength(INITIAL_LENGTH);
                raf.seek(0);
                raf.writeInt(VERSIONED_HEADER);
                raf.writeLong(INITIAL_LENGTH);
            }
            // A rename is atomic
            if (!tempFile.renameTo(file)) {
                throw new IOException("Rename failed!");
            }
        }

        return open(file);
    }

    /** Opens a random access file that writes synchronously. */
    private static RandomAccessFile open(File file) throws FileNotFoundException {
        // Open for reading and writing, as with "rw", and also require that
        // every update to the file's content (but NOT metadata) be written
        // synchronously to the underlying storage device.
        // If the file resides on a local storage device then when an invocation
        // of a method of this class returns it is guaranteed that all changes
        // made to the file by that invocation will have been written to that
        // device. This is useful for ensuring that critical information is not
        // lost in the event of a system crash. If the file does not reside on a
        // local device then no such guarantee is made.
        return new RandomAccessFile(file, "rwd");
    }

    QueueFile(File file, RandomAccessFile raf, boolean overwriteWithZeros) throws IOException {
        this.file = file;
        this.raf = raf;
        this.overwriteWithZeros = overwriteWithZeros;

        long rafLength = raf.length();
        if (!isPowerOfTwo(rafLength)) {
            throw new IOException("File length is not a power of 2: " + rafLength);
        }
        if (rafLength <= HEADER_LENGTH) {
            throw new IOException("File is corrupt. Too small to contain a header and data. Length: " + rafLength);
        }
        raf.seek(0L);
        raf.readFully(header);

        long firstOffset;
        long lastOffset;

        int version = readInt(header, 0) & 0x7FFFFFFF;
        if (version != 1) {
            throw new IOException(
                    "Unable to read version " + version + " format. The one and only supported version is 1");
        }
        fileLength = readLong(header, 4);
        elementCount = readInt(header, 12);
        firstOffset = readLong(header, 16);
        lastOffset = readLong(header, 24);

        if (!isPowerOfTwo(fileLength)) {
            throw new IOException("Header is corrupt. Length stored in header is not a power of 2: " + fileLength);
        }
        if (fileLength <= HEADER_LENGTH) {
            throw new IOException("Header is corrupt. Length stored in header (" + fileLength + ") is invalid.");
        }
        if (elementCount == 0 && !(firstOffset == 0L && lastOffset == 0L)) {
            throw new IOException("Header is corrupt. Inconsistent offsets (" + firstOffset + ", " + lastOffset
                    + ") for zero elements.");
        }
        if (rafLength > fileLength && elementCount == 0) {
            fileLength = rafLength;
            writeHeader(fileLength, elementCount, firstOffset, lastOffset);
        } else if (rafLength < fileLength && elementCount == 0 && rafLength >= INITIAL_LENGTH) {
            fileLength = rafLength;
            writeHeader(fileLength, elementCount, firstOffset, lastOffset);
        }

        if (fileLength > rafLength) {
            throw new IOException(
                    "File is truncated. Expected length: " + fileLength + ", Actual length: " + raf.length());
        }

        this.zeroBytes = overwriteWithZeros
                ? new byte[Math.max(Integer.parseInt(Long.toString(fileLength)), INITIAL_LENGTH)] : null;

        first = readElement(firstOffset);
        last = readElement(lastOffset);
    }

    /**
     * Stores an {@code int} in the {@code byte[]}. The behavior is equivalent
     * to calling {@link RandomAccessFile#writeInt}.
     */
    private static void writeInt(byte[] buffer, int off, int value) {
        buffer[off] = (byte) (value >> 24);
        buffer[off + 1] = (byte) (value >> 16);
        buffer[off + 2] = (byte) (value >> 8);
        buffer[off + 3] = (byte) value;
    }

    /** Reads an {@code int} from the {@code byte[]}. */
    //@formatter:off
    private static int readInt(byte[] buffer, int off) {
        return ((buffer[off] & 0xff) << 24)
             + ((buffer[off + 1] & 0xff) << 16)
             + ((buffer[off + 2] & 0xff) << 8)
             +  (buffer[off + 3] & 0xff);
    }
    //@formatter:on

    /**
     * Stores a {@code long} in the {@code byte[]}. The behavior is equivalent
     * to calling {@link RandomAccessFile#writeLong}.
     */
    private static void writeLong(byte[] buffer, int off, long value) {
        buffer[off] = (byte) (value >> 56);
        buffer[off + 1] = (byte) (value >> 48);
        buffer[off + 2] = (byte) (value >> 40);
        buffer[off + 3] = (byte) (value >> 32);
        buffer[off + 4] = (byte) (value >> 24);
        buffer[off + 5] = (byte) (value >> 16);
        buffer[off + 6] = (byte) (value >> 8);
        buffer[off + 7] = (byte) value;
    }

    /** Reads a {@code long} from the {@code byte[]}. */
    //@formatter:off
    private static long readLong(byte[] buffer, int off) {
        return ((buffer[off] & 0xffL) << 56)
             + ((buffer[off + 1] & 0xffL) << 48)
             + ((buffer[off + 2] & 0xffL) << 40)
             + ((buffer[off + 3] & 0xffL) << 32)
             + ((buffer[off + 4] & 0xffL) << 24)
             + ((buffer[off + 5] & 0xffL) << 16)
             + ((buffer[off + 6] & 0xffL) << 8)
             +  (buffer[off + 7] & 0xffL);
    }
    //@formatter:on

    /**
     * Writes header atomically. The arguments contain the updated values. The
     * class member fields should not have changed yet. This only updates the
     * state in the file. It's up to the caller to update the class member
     * variables *after* this call succeeds. Assumes segment writes are atomic
     * in the underlying file system.
     */
    private void writeHeader(long fileLength, int elementCount, long firstPosition, long lastPosition)
            throws IOException {
        raf.seek(0L);

        writeInt(header, 0, VERSIONED_HEADER);
        writeLong(header, 4, fileLength);
        writeInt(header, 12, elementCount);
        writeLong(header, 16, firstPosition);
        writeLong(header, 24, lastPosition);

        raf.write(header, 0, HEADER_LENGTH);
    }

    Element readElement(long position) throws IOException {
        if (position == 0L) {
            return Element.NULL;
        }
        ringRead(position, header, 0, Element.ELEM_HEADER_LEN);
        int length = readInt(header, 0);
        return new Element(position, length);
    }

    /** Wraps the position if it exceeds the end of the file. */
    long wrapPosition(long position) {
        return position < fileLength ? position : HEADER_LENGTH + position - fileLength;
    }

    /**
     * Writes count bytes from buffer to position in file. Automatically wraps
     * write if position is past the end of the file or if buffer overlaps it.
     * 
     * @param position
     *            in file to write to
     * @param buffer
     *            to write from
     * @param count
     *            # of bytes to write
     */
    private void ringWrite(long position, byte[] buffer, int offset, int count) throws IOException {
        position = wrapPosition(position);
        if (position + count <= fileLength) {
            raf.seek(position);
            raf.write(buffer, offset, count);
        } else {
            // The write overlaps the EOF.
            // # of bytes to write before the EOF. Guaranteed to be less than
            // Integer.MAX_VALUE
            int beforeEof = (int) (fileLength - position);
            raf.seek(position);
            raf.write(buffer, offset, beforeEof);
            raf.seek(HEADER_LENGTH);
            raf.write(buffer, offset + beforeEof, count - beforeEof);
        }
    }

    private void ringErase(long position, long length) throws IOException {
        while (length > 0L) {
            int chunk = (int) Math.min(length, zeroBytes.length);
            ringWrite(position, zeroBytes, 0, chunk);
            length -= chunk;
            position += chunk;
        }
    }

    /**
     * Reads count bytes into buffer from file. Wraps if necessary.
     * 
     * @param position
     *            in file to read from
     * @param buffer
     *            to read into
     * @param count
     *            # of bytes to read
     */
    void ringRead(long position, byte[] buffer, int offset, int count) throws IOException {
        position = wrapPosition(position);
        if (position + count <= fileLength) {
            raf.seek(position);
            raf.readFully(buffer, offset, count);
        } else {
            // The read overlaps the EOF.
            // # of bytes to read before the EOF. Guaranteed to be less than
            // Integer.MAX_VALUE
            int beforeEof = (int) (fileLength - position);
            raf.seek(position);
            raf.readFully(buffer, offset, beforeEof);
            raf.seek(HEADER_LENGTH);
            raf.readFully(buffer, offset + beforeEof, count - beforeEof);
        }
    }

    /**
     * Adds an element to the end of the queue.
     * 
     * @param data
     *            message to copy bytes from
     */
    public synchronized void addMessage(byte[] data) throws IOException {
        if (data == null) {
            return;
        }
        checkOpen();
        add(data, 0, data.length);
    }

    /**
     * Adds an element to the end of the queue.
     * 
     * @param data
     *            to copy bytes from
     * @param offset
     *            to start from in buffer
     * @param count
     *            number of bytes to copy
     * @throws IndexOutOfBoundsException
     *             if {@code offset < 0} or {@code count < 0}, or if
     *             {@code offset + count} is bigger than the length of
     *             {@code buffer}.
     */
    private void add(byte[] data, int offset, int count) throws IOException {
        Objects.requireNonNull(data, "data == null");
        if ((offset | count) < 0 || count > data.length - offset) {
            throw new IndexOutOfBoundsException();
        }
        checkOpen();

        expandIfNecessary(count);

        // Insert a new element after the current last element
        boolean wasEmpty = isEmpty();
        long position = wasEmpty ? HEADER_LENGTH : wrapPosition(last.position + Element.ELEM_HEADER_LEN + last.length);
        Element newLast = new Element(position, count);

        // Write length
        writeInt(header, 0, count);
        ringWrite(newLast.position, header, 0, Element.ELEM_HEADER_LEN);

        // Write data
        ringWrite(newLast.position + Element.ELEM_HEADER_LEN, data, offset, count);

        // Commit the addition. If wasEmpty, then first == last
        long firstPosition = wasEmpty ? newLast.position : first.position;
        writeHeader(fileLength, elementCount + 1, firstPosition, newLast.position);
        last = newLast;
        elementCount++;
        modCount++;
        if (wasEmpty) {
            first = last; // first element
        }
    }

    private long usedBytes() {
        if (elementCount == 0) {
            return HEADER_LENGTH;
        }

        if (last.position >= first.position) {
            // Contiguous queue.
            return (last.position - first.position) // all but last entry
                    + Element.ELEM_HEADER_LEN + last.length // last entry
                    + HEADER_LENGTH;
        } else {
            // tail < head. The queue wraps.
            return last.position // buffer front + header
                    + Element.ELEM_HEADER_LEN + last.length // last entry
                    + fileLength - first.position; // buffer end
        }
    }

    private long remainingBytes() {
        return fileLength - usedBytes();
    }

    /** Returns true if this queue contains no entries. */
    public boolean isEmpty() {
        return elementCount == 0;
    }

    /**
     * If necessary, expands the file to accommodate an additional element of
     * the given length.
     * 
     * @param dataLength
     *            length of data being added
     */
    private void expandIfNecessary(long dataLength) throws IOException {
        long elementLength = Element.ELEM_HEADER_LEN + dataLength;
        long remainingBytes = remainingBytes();
        if (remainingBytes >= elementLength) {
            return;
        }

        // Expand
        long previousLength = fileLength;
        long newLength;
        // Double the length until we can fit the new data
        do {
            remainingBytes += previousLength;
            newLength = previousLength << 1;
            previousLength = newLength;
        } while (remainingBytes < elementLength);

        setLength(newLength);

        // Calculate the position of the tail end of the data in the ring buffer
        long endOfLastElement = wrapPosition(last.position + Element.ELEM_HEADER_LEN + last.length);
        long count = 0L;
        // If the buffer is split, we need to make it contiguous
        if (endOfLastElement <= first.position) {
            FileChannel channel = raf.getChannel();
            channel.position(fileLength); // destination position
            count = endOfLastElement - HEADER_LENGTH;
            if (channel.transferTo(HEADER_LENGTH, count, channel) != count) {
                throw new IOException("Copied insufficient number of bytes!");
            }
        }

        // Commit the expansion
        if (last.position < first.position) {
            long newLastPosition = fileLength + last.position - HEADER_LENGTH;
            writeHeader(newLength, elementCount, first.position, newLastPosition);
            last = new Element(newLastPosition, last.length);
        } else {
            writeHeader(newLength, elementCount, first.position, last.position);
        }

        fileLength = newLength;

        if (overwriteWithZeros) {
            ringErase(HEADER_LENGTH, count);
        }
    }

    /** Sets the length of the file. */
    private void setLength(long newLength) throws IOException {
        // Set new file length (considered metadata) and sync it to storage
        raf.setLength(newLength);
        syncToDisk(true);
    }

    private static boolean isPowerOfTwo(long len) {
        return len > 0L && ((len & (len - 1L)) == 0L);
    }

    private void syncToDisk(boolean includeMetadata) throws IOException {
        raf.getChannel().force(includeMetadata);
    }

    private void checkOpen() throws IOException {
        if (locked) {
            throw new ClosedQueueFileException("Closed : " + file);
        }
    }

    public synchronized void forEachAccept(MessageConsumer consumer) throws IOException {
        if (consumer == null) {
            return;
        }
        checkOpen();
        for (Iterator<byte[]> it = iterator(); it.hasNext(); /**/) {
            try {
                consumer.acceptMessage(it.next());
            } catch (Throwable t) {
                throw new IOException(t);
            }
        }
    }

    /**
     * Returns an iterator over elements in this QueueFile.
     * 
     * <p>
     * The iterator disallows modifications to be made to the QueueFile during
     * iteration. Removing elements from the head of the QueueFile is permitted
     * during iteration using {@link Iterator#remove()}.
     * 
     * <p>
     * The iterator may throw an unchecked {@link RuntimeException} during
     * {@link Iterator#next()} or {@link Iterator#remove()}.
     */
    private Iterator<byte[]> iterator() {
        return new ElementIterator();
    }

    private final class ElementIterator implements Iterator<byte[]> {
        /** Index of element to be returned by subsequent call to next. */
        int nextElementIndex = 0;

        /** Position of element to be returned by subsequent call to next. */
        private long nextElementPosition = first.position;

        /**
         * The {@link #modCount} value that the iterator believes that the
         * backing QueueFile should have. If this expectation is violated, the
         * iterator has detected concurrent modification.
         */
        int expectedModCount = modCount;

        ElementIterator() {
        }

        private void checkForComodification() {
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }

        private void checkOpen() {
            if (locked) {
                throw new IllegalStateException("Closed : " + file);
            }
        }

        @Override
        public boolean hasNext() {
            checkOpen();
            checkForComodification();
            return nextElementIndex != elementCount;
        }

        @Override
        public byte[] next() {
            checkOpen();
            checkForComodification();
            if (isEmpty()) {
                throw new NoSuchElementException();
            }
            if (nextElementIndex >= elementCount) {
                throw new NoSuchElementException();
            }

            try {
                // Read the current element
                Element current = readElement(nextElementPosition);
                byte[] buffer = new byte[current.length];
                nextElementPosition = wrapPosition(current.position + Element.ELEM_HEADER_LEN);
                ringRead(nextElementPosition, buffer, 0, current.length);

                // Update the pointer to the next element
                nextElementPosition = wrapPosition(current.position + Element.ELEM_HEADER_LEN + current.length);
                nextElementIndex++;

                // Return the element we've just read
                return buffer;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void remove() {
            checkForComodification();
            if (isEmpty()) {
                throw new NoSuchElementException();
            }
            if (nextElementIndex != 1) {
                throw new UnsupportedOperationException("Removal is only permitted from the head.");
            }

            try {
                QueueFile.this.remove();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            expectedModCount = modCount;
            nextElementIndex--;
        }
    } // ElementIterator

    /** Returns the number of elements in this queue. */
    public int size() {
        return elementCount;
    }

    public synchronized boolean removeNextMessage(MessageConsumer consumer) throws IOException {
        if (consumer == null) {
            return false;
        }
        byte[] message = peek();
        try {
            consumer.acceptMessage(message);
        } catch (Throwable t) {
            return false;
        }
        remove();
        return true;
    }

    /** Reads the eldest element. Returns null if the queue is empty. */
    private byte[] peek() throws IOException {
        checkOpen();
        if (isEmpty()) {
            return null;
        }
        int length = first.length;
        byte[] data = new byte[length];
        ringRead(first.position + Element.ELEM_HEADER_LEN, data, 0, length);
        return data;
    }

    /**
     * Removes the eldest element.
     */
    void remove() throws IOException {
        checkOpen();
        remove(1);
    }

    /**
     * Removes the eldest {@code n} elements.
     */
    private void remove(int n) throws IOException {
        if (n < 0) {
            throw new IllegalArgumentException("Cannot remove negative (" + n + ") number of elements");
        }
        if (n == 0) {
            return;
        }
        if (n == elementCount) {
            clear();
            return;
        }
        if (isEmpty()) {
            return;
        }
        if (n > elementCount) {
            throw new IllegalArgumentException(
                    "Cannot remove more elements (" + n + ") than present in queue (" + elementCount + ")");
        }

        long eraseStartPosition = first.position;
        long eraseTotalLength = 0L;

        // Read the position and length of the new first element
        long newFirstPosition = first.position;
        int newFirstLength = first.length;
        for (int i = 0; i < n; i++) {
            eraseTotalLength += Element.ELEM_HEADER_LEN + newFirstLength;
            newFirstPosition = wrapPosition(newFirstPosition + Element.ELEM_HEADER_LEN + newFirstLength);
            ringRead(newFirstPosition, header, 0, Element.ELEM_HEADER_LEN);
            newFirstLength = readInt(header, 0);
        }

        // Commit the header
        writeHeader(fileLength, elementCount - n, newFirstPosition, last.position);
        elementCount -= n;
        modCount++;
        first = new Element(newFirstPosition, newFirstLength);

        if (overwriteWithZeros) {
            ringErase(eraseStartPosition, eraseTotalLength);
        }
    }

    /** Clears this queue. Truncates the file to the initial size. */
    private void clear() throws IOException {
        checkOpen();

        // Commit the header
        writeHeader(INITIAL_LENGTH, 0, 0L, 0L);

        if (overwriteWithZeros) {
            // Zero out data
            raf.seek(HEADER_LENGTH);
            raf.write(zeroBytes, 0, INITIAL_LENGTH - HEADER_LENGTH);
        }

        elementCount = 0;
        first = Element.NULL;
        last = Element.NULL;
        if (fileLength > INITIAL_LENGTH) {
            setLength(INITIAL_LENGTH);
        }
        fileLength = INITIAL_LENGTH;
        modCount++;
    }

    /** The underlying {@link File} backing this queue. */
    public File file() {
        return file;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!locked) {
            locked = true;
            try {
                if (fileLength > raf.length()) {
                    setLength(fileLength);
                } else {
                    syncToDisk(true);
                }
            } catch (ClosedByInterruptException ignore) {
                logger.info(ignore.getClass().getName() + " " + this.toString());
            }
            raf.close();
            closed = true;
        }
    }

    public boolean isClosed() {
        return locked;
    }

    @Override
    //@formatter:off
    public synchronized String toString() {
        return "QueueFile{"
             + "file=" + file
             + ", locked=" + locked
             + ", closed=" + closed
             + ", size=" + elementCount
             + ", length=" + fileLength
             + ", first=" + first
             + ", last=" + last
             + ", zero=" + overwriteWithZeros
             + '}';
    }
    //@formatter:on

    /** A pointer to an element. */
    static class Element {
        static final Element NULL = new Element(0L, 0);

        /** Length of element header in bytes */
        static final int ELEM_HEADER_LEN = 4;

        /** Position in file */
        final long position;

        /** The length of the data */
        final int length;

        /**
         * Constructs a new element.
         * 
         * @param position
         *            within file
         * @param length
         *            of data
         */
        Element(long position, int length) {
            this.position = position;
            this.length = length;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[pos=" + position + ", len=" + length + "]";
        }
    }

    /** Fluent API for creating {@link QueueFile} instances. */
    public static final class Builder {
        private final File file;

        /** Start constructing a new queue backed by the given file. */
        public Builder(File file) {
            Objects.requireNonNull(file, "file == null");
            this.file = file;
        }

        /**
         * Constructs a new queue backed by the given builder.
         */
        public QueueFile build() throws IOException {
            RandomAccessFile raf = initializeFromFile(file);
            return createQueueFile(raf, true);
        }

        /**
         * Constructs a new queue backed by the given builder.
         * 
         * @param overwriteWithZeros
         *            whether to overwrite old data with zero bytes when
         *            removing an element
         */
        public QueueFile build(boolean overwriteWithZeros) throws IOException {
            RandomAccessFile raf = initializeFromFile(file);
            return createQueueFile(raf, overwriteWithZeros);
        }

        private QueueFile createQueueFile(RandomAccessFile raf, boolean overwriteWithZeros) throws IOException {
            QueueFile qf = null;
            try {
                qf = new QueueFile(file, raf, overwriteWithZeros);
                return qf;
            } finally {
                if (qf == null) {
                    raf.close();
                }
            }
        }
    }
}
