package ks;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

import misc.PBKDF2;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.CertificateExtensions;
import sun.security.x509.KeyIdentifier;
import sun.security.x509.SubjectKeyIdentifierExtension;
import sun.security.x509.X500Name;

public final class CertGen {

    private static final int KEY_BITS_DEFAULT = 5120;

    public static void main(String[] args) {
        String password = (args != null && args.length > 0) ? args[0] : "";
        String ksFileName = (args != null && args.length > 1) ? args[1] : "ks.p12";
        char[] passwd = PBKDF2.getKey(password);
        System.out.println(passwd);
        System.out.println(passwd.length);
        String dn = "CN=GeoTrust Primary Certification Authority - G2, OU=(c) 2007 GeoTrust Inc. - For authorized use only,  O=GeoTrust Inc., C=US";
        String alias = "localhost";

        long start = System.currentTimeMillis();

        KeyStore ks = generateKeyStore(dn, KEY_BITS_DEFAULT, alias, passwd);

        long end = System.currentTimeMillis();
        System.out.println("took: " + (end - start) + " ms");

        try {
            ks.store(new FileOutputStream(ksFileName), passwd);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyStore generateKeyStore(String certificateDN, int keyBits, String keyEntryAlias, char[] passwd) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, new char[] {});
            CertAndKeyGen certGen = new CertAndKeyGen("RSA", "SHA512withRSA", null);
            certGen.generate(keyBits);

            CertificateExtensions ext = new CertificateExtensions();
            ext.set(SubjectKeyIdentifierExtension.NAME,
                    new SubjectKeyIdentifierExtension(new KeyIdentifier(certGen.getPublicKeyAnyway()).getIdentifier()));

            // 15 years validity
            long validSecs = 15L * 365L * 24L * 60L * 60L;
            X509Certificate cert = certGen.getSelfCertificate(new X500Name(certificateDN), new Date(), validSecs, ext);

            keyStore.setKeyEntry(keyEntryAlias, certGen.getPrivateKey(), passwd, new X509Certificate[] { cert });

            return keyStore;
        } catch (InvalidKeyException | KeyStoreException | NoSuchAlgorithmException | CertificateException
                | NoSuchProviderException | SignatureException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CertGen() {
        throw new AssertionError();
    }
}
