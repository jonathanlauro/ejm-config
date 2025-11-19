package br.com.ejm.ejm_config.annotations;

import org.springframework.stereotype.Service;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface EjmService {

    // Alias opcional (padrão do Spring)
    String value() default "";

    // Porta do RMI Registry (normalmente 1099)
    int port() default 1099;

    // Nome do serviço no Registry
    String name() default "";

    // Porta fixa usada na exportação do objeto remoto
    // NECESSÁRIA para Docker/Kubernetes
    int exportPort() default 5001;
}
