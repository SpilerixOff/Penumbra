package com.mojang.brigadier.exceptions;
import com.mojang.brigadier.Message;
import java.util.function.Function;
public class DynamicCommandExceptionType implements CommandExceptionType {
    public DynamicCommandExceptionType(Function<Object, Message> function) { }
    public CommandSyntaxException create(Object argument) { return null; }
}
