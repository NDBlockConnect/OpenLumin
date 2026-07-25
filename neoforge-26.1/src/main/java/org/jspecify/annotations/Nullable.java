package org.jspecify.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * fabric-1.21.10 shim: org.jspecify is not on MC 1.21.10 classpath.
 */
@Documented
@Target({
    ElementType.METHOD,
    ElementType.FIELD,
    ElementType.PARAMETER,
    ElementType.LOCAL_VARIABLE,
    ElementType.TYPE_USE
})
@Retention(RetentionPolicy.CLASS)
public @interface Nullable {}
