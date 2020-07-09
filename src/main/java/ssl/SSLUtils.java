package ssl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

public final class SSLUtils {

    //@formatter:off
    private static final List<String> suites = Arrays.asList(
                            "TLS_AES_256_GCM_SHA384",
                            "TLS_CHACHA20_POLY1305_SHA256",
                            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256",
                            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                            "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256");
    //@formatter:on

    private static List<String> safestSuites = Collections.emptyList();

    public static final String TLS_12 = "TLSv1.2";
    public static final String TLS_13 = "TLSv1.3";
    private static final String SAFE_TLS = getSafestProtocol_();

    public static String safestProtocol() {
        return SAFE_TLS;
    }

    public static String safestCipherSuite() {
        if (safestSuites != null && !safestSuites.isEmpty()) {
            return safestSuites.get(0);
        }
        return "NONE";
    }

    public static String[] allSafeProtocols() {
        if (TLS_13.equals(SAFE_TLS)) {
            return new String[] { TLS_13, TLS_12 };
        }
        return new String[] { TLS_12 };
    }

    public static String[] allSafeCipherSuites() {
        if (safestSuites != null && !safestSuites.isEmpty()) {
            return safestSuites.toArray(new String[] {});
        }
        return new String[] {};
    }

    private static String getSafestProtocol_() {
        try (SSLServerSocket sock = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(0)) {
            sock.setReuseAddress(true);
            safestSuites = Arrays.stream(sock.getSupportedCipherSuites()).filter(suite -> suites.contains(suite))
                    .collect(Collectors.toList());
            List<String> protos = Arrays.asList(sock.getSupportedProtocols());
            if (protos.contains(TLS_13)) {
                return TLS_13;
            }
            if (protos.contains(TLS_12)) {
                return TLS_12;
            }
            throw new IllegalStateException("no acceptable protocol");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SSLUtils() {
        throw new AssertionError();
    }
}
