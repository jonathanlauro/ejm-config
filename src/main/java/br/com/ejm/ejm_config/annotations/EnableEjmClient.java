package br.com.ejm.ejm_config.annotations;

import br.com.ejm.ejm_config.config.EjmClientAutoConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(EjmClientAutoConfig.class)
public @interface EnableEjmClient {
}