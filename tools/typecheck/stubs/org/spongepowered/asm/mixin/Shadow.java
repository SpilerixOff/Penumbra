package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Shadow {
    Class<?>[] value() default { };
    String[] targets() default { };
    int priority() default 1000;
    boolean remap() default true;
    String prefix() default "";
    String aliases() default "";
}
