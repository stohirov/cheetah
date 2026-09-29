package annotations;

import http.HttpStatus;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExceptionHandler {
  Class<? extends Throwable>[] value() default {};
  HttpStatus status() default HttpStatus.INTERNAL_SERVER_ERROR;
}
