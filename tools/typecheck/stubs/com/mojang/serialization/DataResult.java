package com.mojang.serialization;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
public interface DataResult<R> {
    static <R> DataResult<R> success(R result) { return null; }
    static <R> DataResult<R> error(Supplier<String> message) { return null; }
    static <R> DataResult<R> error(Supplier<String> message, R partial) { return null; }
    Optional<R> result();
    R getOrThrow();
    R getOrThrow(Function<String, ? extends RuntimeException> exception);
    <T> DataResult<T> map(Function<? super R, ? extends T> function);
    <T> DataResult<T> flatMap(Function<? super R, ? extends DataResult<T>> function);
    Optional<R> resultOrPartial();
}
