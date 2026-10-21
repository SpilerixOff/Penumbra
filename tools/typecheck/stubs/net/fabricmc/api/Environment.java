package net.fabricmc.api;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
public @interface Environment { EnvType value(); }
