package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target(ElementType.METHOD)
public @interface Inject {
    String[] method() default { };
    At[] at();
    boolean cancellable() default false;
    boolean remap() default true;
    int require() default -1;
    String[] target() default { };
}
