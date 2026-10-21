package com.mojang.serialization.codecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.datafixers.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Doublure de vérification : toutes les arités de group(), 1 à 16. */
public interface RecordCodecBuilder<O, F> {
    interface Instance<O> {
        <T1> Products1<O, T1> group(RecordCodecBuilder<O, T1> f1);
        <T1, T2> Products2<O, T1, T2> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2);
        <T1, T2, T3> Products3<O, T1, T2, T3> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3);
        <T1, T2, T3, T4> Products4<O, T1, T2, T3, T4> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4);
        <T1, T2, T3, T4, T5> Products5<O, T1, T2, T3, T4, T5> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5);
        <T1, T2, T3, T4, T5, T6> Products6<O, T1, T2, T3, T4, T5, T6> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6);
        <T1, T2, T3, T4, T5, T6, T7> Products7<O, T1, T2, T3, T4, T5, T6, T7> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7);
        <T1, T2, T3, T4, T5, T6, T7, T8> Products8<O, T1, T2, T3, T4, T5, T6, T7, T8> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9> Products9<O, T1, T2, T3, T4, T5, T6, T7, T8, T9> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> Products10<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> Products11<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> Products12<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11, RecordCodecBuilder<O, T12> f12);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> Products13<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11, RecordCodecBuilder<O, T12> f12, RecordCodecBuilder<O, T13> f13);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> Products14<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11, RecordCodecBuilder<O, T12> f12, RecordCodecBuilder<O, T13> f13, RecordCodecBuilder<O, T14> f14);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> Products15<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11, RecordCodecBuilder<O, T12> f12, RecordCodecBuilder<O, T13> f13, RecordCodecBuilder<O, T14> f14, RecordCodecBuilder<O, T15> f15);
        <T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> Products16<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> group(RecordCodecBuilder<O, T1> f1, RecordCodecBuilder<O, T2> f2, RecordCodecBuilder<O, T3> f3, RecordCodecBuilder<O, T4> f4, RecordCodecBuilder<O, T5> f5, RecordCodecBuilder<O, T6> f6, RecordCodecBuilder<O, T7> f7, RecordCodecBuilder<O, T8> f8, RecordCodecBuilder<O, T9> f9, RecordCodecBuilder<O, T10> f10, RecordCodecBuilder<O, T11> f11, RecordCodecBuilder<O, T12> f12, RecordCodecBuilder<O, T13> f13, RecordCodecBuilder<O, T14> f14, RecordCodecBuilder<O, T15> f15, RecordCodecBuilder<O, T16> f16);
        <A> RecordCodecBuilder<O, A> stable(A value);
        <A> RecordCodecBuilder<O, A> point(A value);
    }
    interface Products1<O, T1> {
        Codec<O> apply(Instance<O> instance, Function<T1, O> factory);
    }
    interface Products2<O, T1, T2> {
        Codec<O> apply(Instance<O> instance, BiFunction<T1, T2, O> factory);
    }
    interface Products3<O, T1, T2, T3> {
        Codec<O> apply(Instance<O> instance, Function3<T1, T2, T3, O> factory);
    }
    interface Products4<O, T1, T2, T3, T4> {
        Codec<O> apply(Instance<O> instance, Function4<T1, T2, T3, T4, O> factory);
    }
    interface Products5<O, T1, T2, T3, T4, T5> {
        Codec<O> apply(Instance<O> instance, Function5<T1, T2, T3, T4, T5, O> factory);
    }
    interface Products6<O, T1, T2, T3, T4, T5, T6> {
        Codec<O> apply(Instance<O> instance, Function6<T1, T2, T3, T4, T5, T6, O> factory);
    }
    interface Products7<O, T1, T2, T3, T4, T5, T6, T7> {
        Codec<O> apply(Instance<O> instance, Function7<T1, T2, T3, T4, T5, T6, T7, O> factory);
    }
    interface Products8<O, T1, T2, T3, T4, T5, T6, T7, T8> {
        Codec<O> apply(Instance<O> instance, Function8<T1, T2, T3, T4, T5, T6, T7, T8, O> factory);
    }
    interface Products9<O, T1, T2, T3, T4, T5, T6, T7, T8, T9> {
        Codec<O> apply(Instance<O> instance, Function9<T1, T2, T3, T4, T5, T6, T7, T8, T9, O> factory);
    }
    interface Products10<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10> {
        Codec<O> apply(Instance<O> instance, Function10<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, O> factory);
    }
    interface Products11<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11> {
        Codec<O> apply(Instance<O> instance, Function11<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, O> factory);
    }
    interface Products12<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12> {
        Codec<O> apply(Instance<O> instance, Function12<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, O> factory);
    }
    interface Products13<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13> {
        Codec<O> apply(Instance<O> instance, Function13<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, O> factory);
    }
    interface Products14<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14> {
        Codec<O> apply(Instance<O> instance, Function14<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, O> factory);
    }
    interface Products15<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15> {
        Codec<O> apply(Instance<O> instance, Function15<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, O> factory);
    }
    interface Products16<O, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16> {
        Codec<O> apply(Instance<O> instance, Function16<T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, O> factory);
    }
    static <O> Codec<O> create(Function<Instance<O>, ? extends Codec<O>> builder) { return null; }
    static <O> MapCodec<O> mapCodec(Function<Instance<O>, ? extends MapCodec<O>> builder) { return null; }
}
