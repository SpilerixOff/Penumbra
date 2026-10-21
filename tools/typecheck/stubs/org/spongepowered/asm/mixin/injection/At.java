package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
public @interface At {
    String value();
    String target() default "";
    int ordinal() default -1;
    int opcode() default -1;
    String[] args() default { };
    Shift shift() default Shift.NONE;
    int by() default 0;
    boolean remap() default true;
    enum Shift { NONE, BEFORE, AFTER, BY }
}
