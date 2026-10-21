package com.mojang.brigadier.exceptions;
import com.mojang.brigadier.Message;
public class SimpleCommandExceptionType implements CommandExceptionType {
    public SimpleCommandExceptionType(Message message) { }
    public CommandSyntaxException create() { return null; }
}
