package br.com.ejm.ejm_config.config;

import br.com.ejm.ejm_config.annotations.EjmService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

@Configuration
public class EjmServerAutoConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(EjmServerAutoConfig.class);

    @Autowired
    private ApplicationContext context;

    @PostConstruct
    public void exportServices() {
        Map<String, Object> services = context.getBeansWithAnnotation(EjmService.class);

        if (services.isEmpty()) {
            LOGGER.warn("[RMI] Nenhum serviço remoto encontrado.");
            return;
        }

        try {
            Registry registry;
            try {
                registry = LocateRegistry.getRegistry(1099);
                registry.list(); // tenta listar — se falhar, cria
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(1099);
                LOGGER.error("[RMI] Novo registry criado na porta 1099");
            }

            for (Object bean : services.values()) {
                if (!(bean instanceof Remote remoteBean)) continue;

                EjmService ann = bean.getClass().getAnnotation(EjmService.class);
                String serviceName = ann.value().isEmpty()
                        ? bean.getClass().getInterfaces()[0].getSimpleName()
                        : ann.value();

                Remote stub = UnicastRemoteObject.exportObject(remoteBean, 0);
                registry.rebind(serviceName, stub);

                LOGGER.info("[RMI] Serviço '{}' exportado com sucesso.{}", serviceName, serviceName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
