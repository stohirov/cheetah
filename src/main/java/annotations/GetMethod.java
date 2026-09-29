package annotations;

import http.HttpStatus;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GetMethod {
  String path() default "";
  String consumes() default "";
  String produces() default "";
  HttpStatus status() default HttpStatus.OK;
}
