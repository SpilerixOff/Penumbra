package com.mojang.brigadier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
public class CommandDispatcher<S> {
    public LiteralCommandNode<S> register(LiteralArgumentBuilder<S> command) { return null; }
    public RootCommandNode<S> getRoot() { return null; }
}
