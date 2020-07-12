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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public class SecureServerSocketFactory extends SSLServerSocketFactory {

    private static final SecureServerSocketFactory defaultFactory = new SecureServerSocketFactory();

    private final SSLContext ctx;
    private final SSLServerSocketFactory factory;

    private SecureServerSocketFactory() {
        ctx = new SimpleSSLContext().get();
        factory = ctx.getServerSocketFactory();
    }

    public SecureServerSocketFactory(String password, Path keystore) {
        ctx = SimpleSSLContext.get(password, keystore);
        factory = ctx.getServerSocketFactory();
    }

    /**
     * Returns the default SecureServerSocketFactory.
     * Test mode. Do not use in production!
     *
     * @return the default {@code SecureServerSocketFactory}
     */
    public static SecureServerSocketFactory getDefault() {
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
    public SSLServerSocket createServerSocket() throws IOException {
        return prepareSocket(factory.createServerSocket());
    }

    @Override
    public SSLServerSocket createServerSocket(int port) throws IOException {
        return prepareSocket(factory.createServerSocket(port));
    }

    @Override
    public SSLServerSocket createServerSocket(int port, int backlog) throws IOException {
        return prepareSocket(factory.createServerSocket(port, backlog));
    }

    @Override
    public SSLServerSocket createServerSocket(int port, int backlog, InetAddress ifAddress) throws IOException {
        return prepareSocket(factory.createServerSocket(port, backlog, ifAddress));
    }

    private SSLServerSocket prepareSocket(ServerSocket sock) throws IOException {
        SSLServerSocket sslSock = (SSLServerSocket) sock;
        sslSock.setReuseAddress(true);
        sslSock.setEnabledProtocols(SSLUtils.allSafeProtocols());
        sslSock.setEnabledCipherSuites(getDefaultCipherSuites());
        SSLParameters params = sslSock.getSSLParameters();
        params.setUseCipherSuitesOrder(true);
        sslSock.setSSLParameters(params);
        return sslSock;
    }
}
