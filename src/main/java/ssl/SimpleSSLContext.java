package ssl;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import ks.KSLoader;

public final class SimpleSSLContext {

    private static final KeyStore keyStore = KSLoader.loadKeyStore();

    private final SSLContext sslCtx;

    public SimpleSSLContext() {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KSLoader.passPhrase());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            sslCtx = SSLContext.getInstance(SSLUtils.safestProtocol());
            sslCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    public SSLContext get() {
        return sslCtx;
    }
}
