package com.mojang.brigadier.builder;
import com.mojang.brigadier.tree.LiteralCommandNode;
public class LiteralArgumentBuilder<S> extends ArgumentBuilder<S, LiteralArgumentBuilder<S>> {
    public static <S> LiteralArgumentBuilder<S> literal(String name) { return null; }
    public String getLiteral() { return null; }
    @Override public LiteralCommandNode<S> build() { return null; }
}
