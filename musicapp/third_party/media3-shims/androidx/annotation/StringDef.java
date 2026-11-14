package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface StringDef { String[] value() default {}; boolean open() default false; }
