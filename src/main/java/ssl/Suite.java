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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public enum Suite {

    //@formatter:off
    AES_256_GCM_SHA384("TLS_AES_256_GCM_SHA384", true),
    CHACHA20_POLY1305_SHA256("TLS_CHACHA20_POLY1305_SHA256", true),
    ECDHE_ECDSA_WITH_AES_256_GCM_SHA384("TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384", false),
    ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256("TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256", false),
    ECDHE_RSA_WITH_AES_256_GCM_SHA384("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", false),
    ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256("TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", false),
    ECDHE_ECDSA_WITH_AES_128_GCM_SHA256("TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256", false),
    ECDHE_RSA_WITH_AES_128_GCM_SHA256("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", false);
    //@formatter:on

    private final String name;
    private final boolean tls13Only;

    private Suite(String suite, boolean tls13) {
        name = suite;
        tls13Only = tls13;
    }

    public String toString() {
        return name;
    }

    public boolean isTLS13Suite() {
        return tls13Only;
    }

    @SuppressWarnings("serial")
    private static final HashMap<String, Suite> suites = new HashMap<String, Suite>() {
        {
            for (Suite suite : Suite.class.getEnumConstants()) {
                put(suite.toString(), suite);
            }
        }
    };

    public static Suite of(String suiteName) {
        return suites.get(suiteName);
    }

    public static List<Suite> getTLS13Suites() {
        ArrayList<Suite> tls13List = new ArrayList<>();
        for (Suite suite : Suite.class.getEnumConstants()) {
            if (suite.isTLS13Suite()) {
                tls13List.add(suite);
            }
        }
        return tls13List;
    }
}
