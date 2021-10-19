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

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * A simple abstract implementation of {@link AsyncSource} that requires the
 * implementing class only to override
 * {@link #newRunnable(AsyncSourceAgent, CancellationToken)}.
 *
 * @param <T>
 *            The event type produced by the source
 */
public abstract class AbstractAsyncSource<T> implements AsyncSource<T> {

    /**
     * Public no argument constructor.
     */
    public AbstractAsyncSource() {
    }

    /**
     * Must return a {@link Runnable} that delivers the events to {@code agent}
     * and stops execution when
     * {@link CancellationToken#isCancellationRequested()} returns {@code true}.
     * 
     * @param agent
     *            the {@code AsyncSourceAgent} to call from the Runnable
     * @param ct
     *            the {@code CancellationToken} to query for cancellation in the
     *            Runnable
     * @return a Runnable
     */
    protected abstract Runnable newRunnable(AsyncSourceAgent<T> agent, CancellationToken ct);

    /**
     * {@inheritDoc}
     */
    @Override
    public CancellationToken forEach(AsyncSourceAgent<T> agent) {
        Objects.requireNonNull(agent);
        CancellationToken token = new CancellationToken();
        agent.onInit(token);

        // handle unlikely case that the agent cancels in onInit()
        if (token.isCancellationRequested()) {
            agent.onError(new CancellationException("onInit"));
            return token;
        }

        CompletableFuture<Void> future = decorateSource(agent, token);
        token.registerOnCancel(() -> {
            future.cancel(true); // this won't interrupt threads!
        });

        future.whenCompleteAsync((ignored, throwable) -> {
            if (throwable != null) {
                agent.onError(throwable);
            } else {
                agent.onCompleted();
            }
        });

        return token;
    }

    private CompletableFuture<Void> decorateSource(AsyncSourceAgent<T> agent, CancellationToken ct) {
        return CompletableFuture.runAsync(newRunnable(agent, ct)).thenCompose(stage -> {
            if (!ct.isCancellationRequested()) {
                return CompletableFuture.completedFuture(null);
            } else {
                CompletableFuture<Void> future = new CompletableFuture<>();
                future.completeExceptionally(new CancellationException("afterCompletion"));
                return future;
            }
        });
    }
}
