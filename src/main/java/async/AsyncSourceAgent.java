/*
 * Copyright 2021 Stefan Zobel
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
package async;

/**
 * Handler for the asynchronous delivery of signals (individual events, error or
 * completion of the source) from a {@link AsyncSource}.
 * 
 * @param <T>
 *            The source's element type
 */
@FunctionalInterface
public interface AsyncSourceAgent<T> {

    /**
     * Gets called on arrival of the next asynchronously produced event from the
     * source.
     * 
     * @param e
     *            the next event produced
     */
    void onNext(T e);

    /**
     * Gets invoked before initiating event retrieval from the source.
     * 
     * @param cancellationToken
     *            the token used to stop the source from producing further
     *            events
     */
    default void onInit(CancellationToken cancellationToken) {
    }

    /**
     * A terminal signal indicating the source terminated normally.
     */
    default void onCompleted() {
    }

    /**
     * A terminal signal indicating the source terminated due to an error.
     * 
     * @param t
     *            the error that led to the termination
     */
    default void onError(Throwable t) {
    }
}
