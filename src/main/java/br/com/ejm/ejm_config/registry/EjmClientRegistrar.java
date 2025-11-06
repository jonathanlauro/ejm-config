package br.com.ejm.ejm_config.registry;

import br.com.ejm.ejm_config.annotations.EnableEjmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.rmi.Naming;
import java.rmi.Remote;
import java.util.Map;

public class EjmClientRegistrar implements ImportBeanDefinitionRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger("EJM");

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableEjmClient.class.getName());
        if (attrs == null) return;

        String host = (String) attrs.get("host");
        int port = (int) attrs.get("port");
        Class<?>[] services = (Class<?>[]) attrs.get("services");

        for (Class<?> service : services) {
            try {
                String serviceName = service.getSimpleName();
                String rmiUrl = String.format("rmi://%s:%d/%s", host, port, serviceName);

                RootBeanDefinition beanDef = new RootBeanDefinition();
                beanDef.setTargetType(service);
                beanDef.setInstanceSupplier(() -> {
                    try {
                        return (Remote) Naming.lookup(rmiUrl); // força o cast para Remote
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao conectar ao serviço RMI: " + rmiUrl, e);
                    }
                });
                registry.registerBeanDefinition(serviceName, beanDef);

                LOGGER.info("[RMI] Cliente '{}' registrado em {}", serviceName, rmiUrl);
            } catch (Exception e) {
                throw new RuntimeException("Erro ao registrar cliente RMI para: " + service.getName(), e);
            }
        }
    }
}
