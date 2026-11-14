package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface RequiresPermission { String value() default ""; String[] allOf() default {}; String[] anyOf() default {}; boolean conditional() default false; }
