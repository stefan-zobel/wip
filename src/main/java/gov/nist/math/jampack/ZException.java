/*
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
package gov.nist.math.jampack;

/**
 * This is the exception class for Jampack. Since most errors in matrix
 * algorithms are unrecoverable, the standard response is to pass an error
 * message up the line.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
@SuppressWarnings("serial")
public final class ZException extends RuntimeException {
    public ZException(String s) {
        super(s);
    }
}
