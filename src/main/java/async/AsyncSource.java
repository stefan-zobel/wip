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
 * A source of events that can be processed asynchronously.
 * 
 * @param <T>
 *            The event type produced by the source
 */
public interface AsyncSource<T> {

    /**
     * Process the {@code AsyncSource} by pushing each event to the
     * {@link AsyncSourceAgent#onNext(Object)} method until cancellation is
     * requested or the source terminates either naturally or due to error.
     * 
     * @param agent
     *            the agent that processes the events from the source
     * @return a CancellationToken that can be used to cancel the generation of
     *         new events
     */
    CancellationToken forEach(AsyncSourceAgent<T> agent);
}
