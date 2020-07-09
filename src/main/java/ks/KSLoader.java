package ks;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public final class KSLoader {

    public static KeyStore loadKeyStore() {
        try (InputStream is = KSLoader.class.getResourceAsStream("ks.p12")) {
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
        return "passphrase".toCharArray();
    }
}
