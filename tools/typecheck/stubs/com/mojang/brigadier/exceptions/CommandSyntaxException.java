package com.mojang.brigadier.exceptions;
import com.mojang.brigadier.Message;
public class CommandSyntaxException extends Exception {
    public CommandSyntaxException(CommandExceptionType type, Message message) { }
    public Message getRawMessage() { return null; }
    public CommandExceptionType getType() { return null; }
}
