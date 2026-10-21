package com.mojang.brigadier.arguments;
import com.mojang.brigadier.context.CommandContext;
public class IntegerArgumentType implements ArgumentType<Integer> {
    public static IntegerArgumentType integer() { return null; }
    public static IntegerArgumentType integer(int min) { return null; }
    public static IntegerArgumentType integer(int min, int max) { return null; }
    public static int getInteger(CommandContext<?> context, String name) { return 0; }
}
