package com.mojang.brigadier.builder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.CommandNode;
import java.util.function.Predicate;
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(ArgumentBuilder<S, ?> argument) { return null; }
    public T then(CommandNode<S> argument) { return null; }
    public T executes(Command<S> command) { return null; }
    public T requires(Predicate<S> requirement) { return null; }
    public T redirect(CommandNode<S> target) { return null; }
    public CommandNode<S> build() { return null; }
}
