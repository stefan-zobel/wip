package ks;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import misc.PBKDF2;

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

    public static KeyStore loadKeyStore(String password, Path file) {
        //@formatter:off
        try (InputStream is = Files.newInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(is, 1 << 14)
        )
        {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(bis, passPhrase(password));
            return ks;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        //@formatter:on
    }

    public static char[] passPhrase() {
        return "VpAovsDbxZdX+k3qed8hqEOeSqknJGT1uYanS69gZ2CBJ6oQuZSCGYky7n4WZGpWNWwR".toCharArray();
    }

    public static char[] passPhrase(String password) {
        return PBKDF2.getKey(password);
    }
}
