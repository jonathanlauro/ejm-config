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
            System.setProperty("java.rmi.server.hostname", "localhost");
            exportServices();
        } catch (Exception e) {
            LOGGER.error("[EJM] ❌ Erro ao exportar serviços RMI: {}", e.getMessage(), e);
        }
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

            int port = annotation.port();
            String name = annotation.name().isEmpty()
                    ? bean.getClass().getInterfaces()[0].getSimpleName()
                    : annotation.name();

            try {
                // Sobe um registry local se ainda não estiver rodando
                try {
                    LocateRegistry.createRegistry(port);
                    LOGGER.info("[EJM] 🚀 RMI Registry iniciado na porta {}.", port);
                } catch (Exception e) {
                    LOGGER.info("[EJM] ℹ️ RMI Registry já em execução na porta {}.", port);
                }

                Object wrapped = wrapIfMissingPing(bean, bean.getClass().getInterfaces()[0]);

                Remote stub = (Remote) UnicastRemoteObject.exportObject((Remote) wrapped, 0);
                String rmiUrl = String.format("rmi://localhost:%d/%s", port, name);

                Naming.rebind(rmiUrl, stub);
                LOGGER.info("[EJM] ✅ Serviço '{}' exportado em '{}'.", name, rmiUrl);

            } catch (Exception e) {
                LOGGER.error("[EJM] ❌ Falha ao exportar '{}': {}", name, e.getMessage(), e);
            }
        }
    }

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
