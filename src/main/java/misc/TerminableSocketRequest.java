/*
 * Copyright 2014 Stefan Zobel
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
package misc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * A terminable reader for requests or responses from a socket InputStream.
 */
public class TerminableSocketRequest {

    private static final int BUF_SIZE_DEFAULT = 2048;

    private final Socket sock;
    private final InputStream is;
    private final int timeoutMillisMax;
    private int bufferSize = BUF_SIZE_DEFAULT;
    private int millisRemaining;

    /**
     * Creates a reader for the {@code clientSocket} whose {@link #getBytes()}
     * method either returns the current request from the socket's InputStream
     * or throws a {@code SocketTimeoutException} when the request doesn't
     * complete within {@code timeoutMillis} milliseconds.
     * 
     * @param clientSocket
     *            the socket to read from
     * @param timeoutMillis
     *            the socket read timeout in milliseconds (must be strictly
     *            positive)
     * @throws IOException
     *             if an I/O error occurs when creating the InputStream, the
     *             socket is closed, the socket is not connected, or the socket
     *             input has been shutdown
     */
    public TerminableSocketRequest(Socket clientSocket, int timeoutMillis) throws IOException {
        this(clientSocket, timeoutMillis, BUF_SIZE_DEFAULT);
    }

    /**
     * Creates a reader for the {@code clientSocket} whose {@link #getBytes()}
     * method either returns the current request from the socket's InputStream
     * or throws a {@code SocketTimeoutException} when the request doesn't
     * complete within {@code timeoutMillis} milliseconds.
     * 
     * @param clientSocket
     *            the socket to read from
     * @param timeoutMillis
     *            the socket read timeout in milliseconds (must be strictly
     *            positive)
     * @param bufferSize
     *            size (in bytes) for the internal read buffer
     * @throws IOException
     *             if an I/O error occurs when creating the InputStream, the
     *             socket is closed, the socket is not connected, or the socket
     *             input has been shutdown
     */
    public TerminableSocketRequest(Socket clientSocket, int timeoutMillis, int bufferSize) throws IOException {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be strictly positive: " + timeoutMillis);
        }
        this.sock = clientSocket;
        this.is = clientSocket.getInputStream();
        this.timeoutMillisMax = timeoutMillis;
        if (bufferSize > BUF_SIZE_DEFAULT) {
            this.bufferSize = bufferSize;
        }
    }

    /**
     * Returns a copy of the client socket request or throws an IOException if
     * the read times out or another error occurs.
     * 
     * @return a newly allocated byte array that contains a copy of the socket
     *         client request
     * @throws IOException
     *             in case of an error or a SocketTimeoutException
     */
    public byte[] getBytes() throws IOException {
        millisRemaining = timeoutMillisMax;
        ByteArrayOutputStream baos = null;
        final byte[] array = new byte[bufferSize];
        boolean mayHaveRemaining = true;

        do {
            int count = readFully(array, 0, array.length);
            mayHaveRemaining = (count == array.length);
            // minimize amount of byte[] array allocation
            // and copying taking special cases into account
            if (count > 0) {
                if (baos == null && mayHaveRemaining) {
                    // this is the first iteration and there
                    // may come more, so we need to setup a
                    // buffer
                    baos = new ByteArrayOutputStream(bufferSize);
                } else if (baos == null) {
                    // this is the first **and** the last
                    // iteration, so return a trimmed copy
                    // of 'array'
                    byte[] copy = new byte[count];
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

    private int readFully(byte[] buffer, int off, int len) throws IOException {
        int n = 0;
        boolean expired = false;
        while (n < len && !expired) {
            long start = System.nanoTime();
            sock.setSoTimeout(millisRemaining);
            int count = is.read(buffer, off + n, len - n);
            if (count < 0) { // stream is exhausted
                break;
            }
            n += count;
            long nanosDelta = System.nanoTime() - start;
            millisRemaining -= TimeUnit.NANOSECONDS.toMillis(nanosDelta);
            if (millisRemaining <= 0L) {
                expired = true;
            }
        }
        return n;
    }
}
