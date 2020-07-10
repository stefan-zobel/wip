package ssl;

import java.nio.file.Path;
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

    private final SSLContext defaultCtx;
    private final SSLContext ctx;

    public SimpleSSLContext() {
        ctx = null;
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, KSLoader.passPhrase());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            defaultCtx = SSLContext.getInstance(SSLUtils.safestProtocol());
            defaultCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (UnrecoverableKeyException | NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }

    private SimpleSSLContext(String password, Path file) {
        defaultCtx = null;
        try {
            KeyStore ks = KSLoader.loadKeyStore(password, file);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KSLoader.passPhrase(password));
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            ctx = SSLContext.getInstance(SSLUtils.safestProtocol());
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (UnrecoverableKeyException | KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    public SSLContext get() {
        if (defaultCtx != null) {
            return defaultCtx;
        }
        return ctx;
    }

    public static SSLContext get(String password, Path file) {
        return new SimpleSSLContext(password, file).get();
    }
}
