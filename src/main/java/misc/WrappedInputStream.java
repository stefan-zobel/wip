package misc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A wrapper for {@link InputStream} that supports the additional convenience
 * methods {@link #readFully(byte[])} and {@link #getBytes()}
 */
public class WrappedInputStream extends InputStream {

    private final InputStream is;
    private int bufSize = 2048;

    public WrappedInputStream(final InputStream is) {
        if (is == null) {
            throw new IllegalArgumentException("InputStream == null");
        }
        this.is = is;
    }

    /**
     * Sets the size of the internal buffer(s) used in {@link #getBytes()} (the
     * default is <tt>2048</tt> byte). Returns this stream (for method
     * chaining). The argument must be <tt>>= 1</tt>.
     * 
     * @param size
     *            the size of the buffer(s) used in {@link #getBytes()}.
     * @return Returns this stream.
     * @throws IllegalArgumentException
     *             if <tt>size < 1</tt>.
     */
    public WrappedInputStream setBufferSize(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("illegal size: " + size);
        }
        bufSize = size;
        return this;
    }

    /**
     * Returns the size of the buffer(s) used in {@link #getBytes()}.
     * 
     * @return The size of the buffer(s) used in {@link #getBytes()}.
     */
    public int getBufferSize() {
        return bufSize;
    }

    /**
     * Copies up to <code>buffer.length</code> bytes from this stream into the
     * passed <tt>buffer</tt> byte array and returns the number of bytes
     * actually copied into the array.
     * <p/>
     * In contrast to {@link #read(byte[])} (which itself calls
     * {@link #read(byte[], int, int)} with arguments
     * <code>(buffer, 0, buffer.length)</code> underneath), this method attempts
     * to exhaust this stream, i.e. if the returned byte count is less than
     * <code>buffer.length</code> you'll have the guarantee that this stream has
     * been read fully (it may not be exhausted if the return value equals the
     * buffer length).
     * <p/>
     * This saves oneself from the usual looping construct that is used to
     * maximally fill up a given <tt>byte[]</tt> buffer from an input stream.
     * 
     * @param buffer
     *            the byte array to copy into.
     * @return the count of bytes copied into the array.
     * @throws IOException
     *             in case of an error.
     */
    public final int readFully(byte[] buffer) throws IOException {
        return readFully(buffer, 0, buffer.length);
    }

    public final int readFully(byte[] buffer, int off, int len) throws IOException {
        if (len < 0) {
            throw new IndexOutOfBoundsException("len < 0");
        }
        int n = 0;
        while (n < len) {
            final int count = is.read(buffer, off + n, len - n);
            if (count < 0) { // stream is exhausted
                break;
            }
            n += count;
        }
        return n;
    }

    /**
     * Returns a newly allocated byte array that contains a copy of the content
     * of this stream (this stream is exhausted afterwards). Returns an empty
     * byte array if this stream is empty.
     * 
     * @return a newly allocated byte array that contains (a copy of) the
     *         content of this stream.
     * @throws IOException
     *             in case of an error.
     */
    public byte[] getBytes() throws IOException {
        ByteArrayOutputStream baos = null;
        final byte[] array = new byte[bufSize];
        boolean mayHaveRemaining = true;

        do {
            final int count = readFully(array);
            mayHaveRemaining = (count == array.length);
            // minimize amount of byte[] array allocation
            // and copying taking special cases into account
            if (count > 0) {
                if (baos == null && mayHaveRemaining) {
                    // this is the first iteration and there
                    // may come more, so we need to setup a
                    // buffer
                    baos = new ByteArrayOutputStream(bufSize);
                } else if (baos == null) {
                    // this is the first _and_ the last
                    // iteration, so return a trimmed copy
                    // of 'array'
                    final byte[] copy = new byte[count];
                    System.arraycopy(array, 0, copy, 0, Math.min(array.length, count));
                    return copy;
                }
                // only reached when baos != null
                baos.write(array, 0, count);
            }
        } while (mayHaveRemaining);

        if (baos != null) {
            return baos.toByteArray();
        }
        // we read 0 bytes from this stream
        return new byte[] {};
    }

    /**
     * @see java.io.InputStream#available()
     */
    public int available() throws IOException {
        return is.available();
    }

    /**
     * @see java.io.InputStream#close()
     */
    public void close() throws IOException {
        is.close();
    }

    /**
     * @see java.io.InputStream#mark(int)
     */
    public void mark(int readlimit) {
        is.mark(readlimit);
    }

    /**
     * @see java.io.InputStream#markSupported()
     */
    public boolean markSupported() {
        return is.markSupported();
    }

    /**
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {
        return is.read();
    }

    /**
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException {
        return is.read(b, off, len);
    }

    /**
     * @see java.io.InputStream#read(byte[])
     */
    public int read(byte[] b) throws IOException {
        return is.read(b);
    }

    /**
     * @see java.io.InputStream#reset()
     */
    public void reset() throws IOException {
        is.reset();
    }

    /**
     * @see java.io.InputStream#skip(long)
     */
    public long skip(long n) throws IOException {
        return is.skip(n);
    }
}
