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

    /**
     * Test mode. Do not use in production!
     */
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
