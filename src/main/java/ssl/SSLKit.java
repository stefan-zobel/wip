package ssl;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Some useful default constants for SSL connections during development.
 * Provides defaults that relax X509 certificate and hostname verification while
 * using SSL.
 */
public final class SSLKit {

    /**
     * A trust manager that does not validate certificate chains.
     */
    public static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[] { new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    } };

    /**
     * A hostname verifier that accepts all hostnames.
     */
    public static final HostnameVerifier ACCEPT_ALL_HOSTS = new HostnameVerifier() {
        public boolean verify(String urlHostName, SSLSession session) {
            return true;
        }
    };

    /**
     * Creates an initialized {@link SSLContext} that does not validate certificate
     * chains.
     * 
     * @return an initialized {@link SSLContext} that does not validate certificate
     *         chains
     */
    public static SSLContext createTrustAllContext() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance(SSLUtils.safestProtocol());
            // install the all-trusting trust manager
            sslContext.init(null, TRUST_ALL_CERTS, null);
        } catch (NoSuchAlgorithmException e) {
            // shouldn't happen
            throw new IllegalStateException(e);
        } catch (KeyManagementException e) {
            // shouldn't happen
            throw new IllegalStateException(e);
        }
        return sslContext;
    }

    private SSLKit() {
        throw new AssertionError();
    }
}
