package doublelist;

import java.util.Objects;
import java.util.function.DoubleConsumer;

public interface DIterator {

    public boolean hasNext();

    public double next();

    public void remove();

    default void forEachRemaining(DoubleConsumer action) {
        Objects.requireNonNull(action);
        while (hasNext()) {
            action.accept(next());
        }
    }
}
