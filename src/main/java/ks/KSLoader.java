package ks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public final class KSLoader {

    private static final String KS_NAME = "ks.p12";

    public static KeyStore loadKeyStore() {
        try (InputStream is = KSLoader.class.getResourceAsStream(KS_NAME)) {
            if (is == null) {
                throw new IllegalStateException("KeyStore " + KS_NAME + " not found!");
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(is, passPhrase());
            return ks;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static char[] passPhrase() {
        return "VpAovsDbxZdX+k3qed8hqEOeSqknJGT1uYanS69gZ2CBJ6oQuZSCGYky7n4WZGpWNWwR".toCharArray();
    }
}
