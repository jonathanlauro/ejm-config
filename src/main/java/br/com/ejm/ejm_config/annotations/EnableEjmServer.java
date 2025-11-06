package br.com.ejm.ejm_config.annotations;

import br.com.ejm.ejm_config.config.EjmServerAutoConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(EjmServerAutoConfig.class)
public @interface EnableEjmServer {
}