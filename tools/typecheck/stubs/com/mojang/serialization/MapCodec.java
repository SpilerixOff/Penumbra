package com.mojang.serialization;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
public interface MapCodec<A> {
    <O> RecordCodecBuilder<O, A> forGetter(Function<O, A> getter);
    Codec<A> codec();
    <B> MapCodec<B> xmap(Function<? super A, ? extends B> to, Function<? super B, ? extends A> from);
}
