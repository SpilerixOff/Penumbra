package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD)
public @interface ModifyVariable {
    String[] method() default { };
    At at();
    int ordinal() default -1;
    int index() default -1;
    boolean remap() default true;
}
