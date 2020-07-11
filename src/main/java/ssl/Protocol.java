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

public enum Protocol {

    //@formatter:off
    TLS_12("TLSv1.2"),
    TLS_13("TLSv1.3");
    //@formatter:on

    private final String name;

    private Protocol(String prot) {
        name = prot;
    }

    public String toString() {
        return name;
    }

    public static Protocol of(String protocolName) {
        switch (protocolName) {
        case "TLSv1.2":
            return TLS_12;
        case "TLSv1.3":
            return TLS_13;
        default:
            return null;
        }
    }
}
