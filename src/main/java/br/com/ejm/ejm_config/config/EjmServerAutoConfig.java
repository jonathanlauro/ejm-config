package br.com.ejm.ejm_config.config;

import br.com.ejm.ejm_config.annotations.EjmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.Naming;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

@Component
public class EjmServerAutoConfig implements ApplicationContextAware, SmartInitializingSingleton {

    private static final Logger LOGGER = LoggerFactory.getLogger(EjmServerAutoConfig.class);
    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            configurePublicHostname();
            exportServices();
        } catch (Exception e) {
            LOGGER.error("[EJM] ❌ Erro ao exportar serviços RMI: {}", e.getMessage(), e);
        }
    }

    /**
     * Define Hostname Público (RMI Stub)
     *
     * LOCAL:
     *   EJM_PUBLIC_HOST ausente -> usa "localhost"
     *
     * DOCKER/KUBERNETES:
     *   EJM_PUBLIC_HOST define hostname, ex: usuarios-service
     */
    private void configurePublicHostname() {
        String host = System.getenv("EJM_PUBLIC_HOST");

        if (host == null || host.isBlank()) {
            host = "localhost"; // fallback local
            LOGGER.info("[EJM] 🌍 Ambiente LOCAL detectado. Usando hostname '{}'.", host);
        } else {
            LOGGER.info("[EJM] 🌐 Ambiente DOCKER/KUBERNETES detectado. Usando hostname externo '{}'.", host);
        }

        System.setProperty("java.rmi.server.hostname", host);
    }


    private void exportServices() throws Exception {
        Map<String, Object> remotes = context.getBeansWithAnnotation(EjmService.class);
        if (remotes.isEmpty()) {
            LOGGER.warn("[EJM] ⚠️ Nenhum serviço com @EjmService encontrado.");
            return;
        }

        for (Map.Entry<String, Object> entry : remotes.entrySet()) {
            Object bean = entry.getValue();
            EjmService annotation = bean.getClass().getAnnotation(EjmService.class);

            int registryPort = annotation.port();        // ex: 1099
            int exportPort   = annotation.exportPort();  // ex: 5001

            String name = annotation.name().isEmpty()
                    ? bean.getClass().getInterfaces()[0].getSimpleName()
                    : annotation.name();

            try {
                // 🔹 Sobe um registry na porta informada (1099 normalmente)
                try {
                    LocateRegistry.createRegistry(registryPort);
                    LOGGER.info("[EJM] 🚀 RMI Registry iniciado na porta {}.", registryPort);
                } catch (Exception e) {
                    LOGGER.info("[EJM] ℹ️ RMI Registry já em execução na porta {}.", registryPort);
                }

                // 🔹 Injeta ping() caso a interface não tenha
                Object wrapped = wrapIfMissingPing(bean, bean.getClass().getInterfaces()[0]);

                // 🔹 EXPORTAÇÃO CORRIGIDA — usa porta fixa (NÃO usar 0 no Kubernetes)
                Remote stub = (Remote) UnicastRemoteObject.exportObject((Remote) wrapped, exportPort);

                // 🔹 Define URL com hostname público configurado no inicio
                String host = System.getProperty("java.rmi.server.hostname");
                String rmiUrl = String.format("rmi://%s:%d/%s", host, registryPort, name);

                Naming.rebind(rmiUrl, stub);

                LOGGER.info("[EJM] ✅ Serviço '{}' exportado em '{}'. (exportPort={})",
                        name, rmiUrl, exportPort);

            } catch (Exception e) {
                LOGGER.error("[EJM] ❌ Falha ao exportar '{}': {}", name, e.getMessage(), e);
            }
        }
    }


    /** Injeta automaticamente um método ping() se não existir */
    private Object wrapIfMissingPing(Object target, Class<?> iface) {
        boolean hasPing = false;
        for (Method method : iface.getMethods()) {
            if (method.getName().equals("ping") && method.getParameterCount() == 0) {
                hasPing = true;
                break;
            }
        }

        if (hasPing) {
            LOGGER.debug("[EJM] Interface '{}' já possui ping().", iface.getSimpleName());
            return target;
        }

        LOGGER.debug("[EJM] Interface '{}' sem ping() — injetando ping automático.", iface.getSimpleName());

        return Proxy.newProxyInstance(
                iface.getClassLoader(),
                new Class[]{iface},
                (proxy, method, args) -> {
                    if (method.getName().equals("ping") && method.getParameterCount() == 0) {
                        return true;
                    }
                    return method.invoke(target, args);
                }
        );
    }
}
