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
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

/**
 * Exporta automaticamente beans anotados com @EjmRemote como serviços RMI.
 * Injeta automaticamente o método ping() caso não seja implementado.
 */
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
            exportServices();
        } catch (Exception e) {
            LOGGER.error("[EJM] ❌ Erro ao exportar serviços RMI: {}", e.getMessage(), e);
        }
    }

    /**
     * Exporta todos os beans anotados com @EjmRemote para o registro RMI.
     */
    private void exportServices() throws Exception {
        Map<String, Object> remotes = context.getBeansWithAnnotation(EjmService.class);
        if (remotes.isEmpty()) {
            LOGGER.warn("[EJM] ⚠️ Nenhum serviço com @EjmRemote encontrado.");
            return;
        }

        for (Map.Entry<String, Object> entry : remotes.entrySet()) {
            Object bean = entry.getValue();
            Class<?>[] interfaces = bean.getClass().getInterfaces();
            if (interfaces.length == 0) {
                LOGGER.warn("[EJM] ⚠️ Bean '{}' não implementa nenhuma interface remota.", entry.getKey());
                continue;
            }

            Class<?> iface = interfaces[0];
            String serviceName = iface.getSimpleName();

            Object wrapped = wrapIfMissingPing(bean, iface);

            try {
                Remote stub = (Remote) UnicastRemoteObject.exportObject((Remote) wrapped, 0);
                Naming.rebind(serviceName, stub);
                LOGGER.info("[EJM] ✅ Serviço '{}' exportado via RMI com ping automático.", serviceName);
            } catch (Exception e) {
                LOGGER.error("[EJM] ❌ Falha ao exportar '{}': {}", serviceName, e.getMessage(), e);
            }
        }
    }

    /**
     * Se o bean não tiver implementação de ping(), cria um proxy dinâmico que injeta um ping() padrão.
     */
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