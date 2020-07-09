package ssl;

import java.io.IOException;
import java.io.UncheckedIOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

public final class SSLSockets {

    private static final SSLContext ctx = new SimpleSSLContext().get();

    public static SSLSocket createSocket(String host, int port) {
        try {
            SSLSocket sock = (SSLSocket) ctx.getSocketFactory().createSocket(host, port);

            sock.setReuseAddress(true);
            sock.setEnabledProtocols(SSLUtils.allSafeProtocols());
            sock.setEnabledCipherSuites(SSLUtils.allSafeCipherSuites());

            return sock;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static SSLServerSocket createServerSocket(int port) {
        try {
            SSLServerSocket sock = (SSLServerSocket) ctx.getServerSocketFactory().createServerSocket(port);

            sock.setReuseAddress(true);
            sock.setEnabledProtocols(SSLUtils.allSafeProtocols());
            sock.setEnabledCipherSuites(SSLUtils.allSafeCipherSuites());

            return sock;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SSLSockets() {
        throw new AssertionError();
    }
}