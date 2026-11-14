package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface RequiresOptIn { Level level() default Level.ERROR; enum Level { WARNING, ERROR } }
