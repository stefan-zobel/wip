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
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Path;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class SecureSocketFactory extends SSLSocketFactory {

    private static final SecureSocketFactory defaultFactory = new SecureSocketFactory();

    private final SSLContext ctx;
    private final SSLSocketFactory factory;

    private SecureSocketFactory() {
        ctx = new SimpleSSLContext().get();
        factory = ctx.getSocketFactory();
    }

    public SecureSocketFactory(String password, Path keystore) {
        ctx = SimpleSSLContext.get(password, keystore);
        factory = ctx.getSocketFactory();
    }

    /**
     * Returns the default SecureSocketFactory.
     * Test mode. Do not use in production!
     *
     * @return the default {@code SecureSocketFactory}
     */
    public static SecureSocketFactory getDefault() {
        return defaultFactory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return getSupportedCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return SSLUtils.allSafeCipherSuites();
    }

    @Override
    public SSLSocket createSocket() throws IOException {
        return prepareSocket(factory.createSocket());
    }

    @Override
    public SSLSocket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return prepareSocket(factory.createSocket(s, host, port, autoClose));
    }

    @Override
    public SSLSocket createSocket(String host, int port) throws IOException, UnknownHostException {
        return prepareSocket(factory.createSocket(host, port));
    }

    @Override
    public SSLSocket createSocket(InetAddress host, int port) throws IOException {
        return prepareSocket(factory.createSocket(host, port));
    }

    @Override
    public SSLSocket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException, UnknownHostException {
        return prepareSocket(factory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public SSLSocket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        return prepareSocket(factory.createSocket(address, port, localAddress, localPort));
    }

    @Override
    public SSLSocket createSocket(Socket s, InputStream consumed, boolean autoClose) throws IOException {
        return prepareSocket(factory.createSocket(s, consumed, autoClose));
    }

    private SSLSocket prepareSocket(Socket sock) throws IOException {
        SSLSocket sslSock = (SSLSocket) sock;
        sslSock.setReuseAddress(true);
        sslSock.setEnabledProtocols(SSLUtils.allSafeProtocols());
        sslSock.setEnabledCipherSuites(getDefaultCipherSuites());
        return sslSock;
    }
}
