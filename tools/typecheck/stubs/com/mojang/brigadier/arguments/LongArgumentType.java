package com.mojang.brigadier.arguments;
import com.mojang.brigadier.context.CommandContext;
public class LongArgumentType implements ArgumentType<Long> {
    public static LongArgumentType longArg() { return null; }
    public static LongArgumentType longArg(long min) { return null; }
    public static LongArgumentType longArg(long min, long max) { return null; }
    public static long getLong(CommandContext<?> context, String name) { return 0L; }
}
