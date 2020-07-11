/*
 * Copyright 2020 Stefan Zobel
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
package ssl;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public final class SSLSockets {

    private static final SSLContext defaultCtx = new SimpleSSLContext().get();

    public static SSLSocket createSocket(String host, int port) {
        return createSocket(host, port, null);
    }

    public static SSLServerSocket createServerSocket(int port) {
        return createServerSocket(port, null);
    }

    public static SSLSocket createSocket(String host, int port, SSLContext ctx) {
        try {
            if (ctx == null) {
                ctx = SSLSockets.defaultCtx;
            }
            SSLSocket sock = (SSLSocket) ctx.getSocketFactory().createSocket(host, port);

            sock.setReuseAddress(true);
            sock.setEnabledProtocols(SSLUtils.allSafeProtocols());
            sock.setEnabledCipherSuites(SSLUtils.allSafeCipherSuites());

            return sock;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SSLServerSocket createServerSocket(int port, SSLContext ctx) {
        return createServerSocket(port, ctx, true);
    }

    public static SSLServerSocket createServerSocket(int port, SSLContext ctx, boolean needClientAuth) {
        try {
            if (ctx == null) {
                ctx = SSLSockets.defaultCtx;
            }
            SSLServerSocket sock = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);

            sock.setReuseAddress(true);
            sock.setEnabledProtocols(SSLUtils.allSafeProtocols());
            sock.setEnabledCipherSuites(SSLUtils.allSafeCipherSuites());
            if (needClientAuth) {
                sock.setNeedClientAuth(true);
            }

            return sock;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SSLSockets() {
        throw new AssertionError();
    }
}
