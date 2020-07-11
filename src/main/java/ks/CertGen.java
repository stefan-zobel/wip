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

    private static final int RSA_KEY_BITS_DEFAULT = 5120;
    private static final int ECDSA_KEY_BITS_DEFAULT = 521;
    // 15 years validity
    private static final long VALIDITY = 15L * 365L * 24L * 60L * 60L;
    private static final String DUMMY_DN = "CN=GeoTrust Primary Certification Authority - G2, OU=(c) 2007 GeoTrust Inc. - For authorized use only,  O=GeoTrust Inc., C=US";
    private static final String DEFAULT_ALIAS = "localhost";

    public static void main(String[] args) {
        String password = (args != null && args.length > 0) ? args[0] : "";
        String ksFileName = (args != null && args.length > 1) ? args[1] : "ks.p12";
        String dn = (args != null && args.length > 2) ? args[2] : DUMMY_DN;
        String alias = (args != null && args.length > 3) ? args[3] : DEFAULT_ALIAS;
        char[] passwd = PBKDF2.getKey(password);
        System.out.println(passwd);
        System.out.println(passwd.length);

        long start = System.currentTimeMillis();

        Date startDate = new Date();
        KeyStore ks = generateKeyStore(dn, alias, passwd, startDate);

        long end = System.currentTimeMillis();
        System.out.println("took: " + (end - start) + " ms");

        try (FileOutputStream fos = new FileOutputStream(ksFileName)) {
            ks.store(fos, passwd);
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyStore generateKeyStore(String certificateDN, String keyEntryAlias, char[] passwd,
            Date startDate) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, new char[] {});

            generateECDSACertificate(keyStore, certificateDN, keyEntryAlias, passwd, startDate);
            generateRSACertificate(keyStore, certificateDN, keyEntryAlias + "2", passwd, startDate);

            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void generateECDSACertificate(KeyStore keyStore, String certificateDN, String keyEntryAlias,
            char[] passwd, Date startDate) {
        try {
            CertAndKeyGen certGen = new CertAndKeyGen("EC", "SHA512withECDSA", null);
            certGen.generate(ECDSA_KEY_BITS_DEFAULT);

            storeCertificate(keyStore, certificateDN, keyEntryAlias, passwd, startDate, certGen);

        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private static void generateRSACertificate(KeyStore keyStore, String certificateDN, String keyEntryAlias,
            char[] passwd, Date startDate) {
        try {
            CertAndKeyGen certGen = new CertAndKeyGen("RSA", "SHA512withRSA", null);
            certGen.generate(RSA_KEY_BITS_DEFAULT);

            storeCertificate(keyStore, certificateDN, keyEntryAlias, passwd, startDate, certGen);

        } catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException(e);
        }
    }

    private static void storeCertificate(KeyStore keyStore, String certificateDN, String keyEntryAlias, char[] passwd,
            Date startDate, CertAndKeyGen certGen) {
        try {
            CertificateExtensions ext = new CertificateExtensions();
            ext.set(SubjectKeyIdentifierExtension.NAME,
                    new SubjectKeyIdentifierExtension(new KeyIdentifier(certGen.getPublicKeyAnyway()).getIdentifier()));

            X509Certificate cert = certGen.getSelfCertificate(new X500Name(certificateDN), startDate, VALIDITY, ext);

            keyStore.setKeyEntry(keyEntryAlias, certGen.getPrivateKey(), passwd, new X509Certificate[] { cert });
        } catch (InvalidKeyException | CertificateException | SignatureException | NoSuchAlgorithmException
                | NoSuchProviderException | KeyStoreException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private CertGen() {
        throw new AssertionError();
    }
}
