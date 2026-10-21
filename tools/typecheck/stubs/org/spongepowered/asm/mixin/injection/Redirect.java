package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD)
public @interface Redirect {
    String[] method() default { };
    At at();
    boolean remap() default true;
}
