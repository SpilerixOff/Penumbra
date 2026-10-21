package com.mojang.serialization;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
public interface Codec<A> {
    Codec<Integer> INT = null;
    Codec<Long> LONG = null;
    Codec<Float> FLOAT = null;
    Codec<Double> DOUBLE = null;
    Codec<Byte> BYTE = null;
    Codec<Short> SHORT = null;
    Codec<Boolean> BOOL = null;
    Codec<String> STRING = null;
    Codec<ByteBuffer> BYTE_BUFFER = null;
    <B> Codec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from);
    <B> Codec<B> comapFlatMap(Function<? super A, DataResult<? extends B>> to, Function<? super B, ? extends A> from);
    <B> Codec<B> flatComapMap(Function<? super A, ? extends B> to, Function<? super B, DataResult<? extends A>> from);
    <B> Codec<B> flatXmap(Function<? super A, DataResult<? extends B>> to, Function<? super B, DataResult<? extends A>> from);
    <B> Codec<B> dispatch(Function<? super B, ? extends A> type, Function<? super A, ? extends MapCodec<? extends B>> codec);
    Codec<List<A>> listOf();
    Codec<List<A>> listOf(int min, int max);
    MapCodec<A> fieldOf(String name);
    MapCodec<Optional<A>> optionalFieldOf(String name);
    MapCodec<A> optionalFieldOf(String name, A defaultValue);
    Codec<A> orElse(A value);
    Codec<A> stable();
    <S> DataResult<A> parse(DynamicOps<S> ops, S input);
    <S> DataResult<S> encodeStart(DynamicOps<S> ops, A input);
    static <K, V> Codec<Map<K, V>> unboundedMap(Codec<K> key, Codec<V> value) { return null; }
    static <A> Codec<A> unit(A value) { return null; }
    static <E> Codec<E> stringResolver(Function<E, String> to, Function<String, E> from) { return null; }
    static Codec<Integer> intRange(int min, int max) { return null; }
    static Codec<Float> floatRange(float min, float max) { return null; }
    static Codec<Double> doubleRange(double min, double max) { return null; }
    static <A> Codec<A> lazyInitialized(Supplier<Codec<A>> supplier) { return null; }
    static <A> Codec<A> recursive(String name, Function<Codec<A>, Codec<A>> builder) { return null; }
}
