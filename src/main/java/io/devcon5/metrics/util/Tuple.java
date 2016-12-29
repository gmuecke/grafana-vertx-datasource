package io.devcon5.metrics.util;

import java.util.Objects;

/**
 *
 */
public final class Tuple<T, U> {

    private final T obj1;
    private final U obj2;

    private Tuple(final T obj1, final U obj2) {

        this.obj1 = obj1;
        this.obj2 = obj2;
    }

    public T getFirst() {

        return obj1;
    }

    public U getSecond() {

        return obj2;
    }

    public static <T, U> Tuple<T, U> of(T first, U second) {

        Objects.requireNonNull(first, "first tuple element must not be null");
        Objects.requireNonNull(first, "second tuple element must not be null");
        return new Tuple<>(first, second);
    }

}
