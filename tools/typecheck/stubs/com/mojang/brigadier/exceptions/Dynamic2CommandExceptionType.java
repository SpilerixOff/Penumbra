package com.mojang.brigadier.exceptions;
import com.mojang.brigadier.Message;
public class Dynamic2CommandExceptionType implements CommandExceptionType {
    public interface Function { Message apply(Object a, Object b); }
    public Dynamic2CommandExceptionType(Function function) { }
    public CommandSyntaxException create(Object a, Object b) { return null; }
}
