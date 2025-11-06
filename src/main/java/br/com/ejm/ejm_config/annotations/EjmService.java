package br.com.ejm.ejm_config.annotations;

import org.springframework.stereotype.Service;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface EjmService {
    String value() default "";  // <-- este método precisa existir
    int port() default 1099;
    String name() default "";
}