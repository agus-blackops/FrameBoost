package androidx.annotation;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS)
public @interface OptIn { Class<? extends java.lang.annotation.Annotation>[] markerClass(); }
