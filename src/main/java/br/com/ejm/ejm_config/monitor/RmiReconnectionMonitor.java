package br.com.ejm.ejm_config.monitor;

import br.com.ejm.ejm_config.config.EjmClientAutoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

import java.rmi.Naming;
import java.util.List;

public class RmiReconnectionMonitor implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RmiReconnectionMonitor.class);
    private final ConfigurableApplicationContext context;
    private final List<EjmClientAutoConfig.RmiServiceConfig> services;
    private volatile boolean running = true;

    public RmiReconnectionMonitor(ConfigurableApplicationContext context,
                                  List<EjmClientAutoConfig.RmiServiceConfig> services) {
        this.context = context;
        this.services = services;
    }

    @Override
    public void run() {
        LOGGER.info("[EJM] 🔍 Monitor RMI iniciado — verificando conexões a cada 5 segundos...");

        while (running) {
            try {
                Thread.sleep(5000);

                for (EjmClientAutoConfig.RmiServiceConfig service : services) {
                    verificarServico(service);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("[EJM] 💤 Monitor RMI interrompido.");
                break;
            } catch (Exception e) {
                LOGGER.error("[EJM] ❌ Erro inesperado no monitor RMI: {}", e.getMessage(), e);
            }
        }
    }

    private void verificarServico(EjmClientAutoConfig.RmiServiceConfig service) {
        try {
            Object bean = context.getBean(service.name());
            var pingMethod = bean.getClass().getMethod("ping");
            Object result = pingMethod.invoke(bean);

            if (Boolean.TRUE.equals(result)) {
                LOGGER.debug("[EJM] ✅ Serviço '{}' está ativo.", service.name());
            }

        } catch (Exception ex) {
            LOGGER.warn("[EJM] ⚠️ Erro ao verificar '{}': {}", service.name(), ex.getMessage());
            tentarReconectar(service);
        }
    }

    private void tentarReconectar(EjmClientAutoConfig.RmiServiceConfig service) {
        try {
            Object novoStub = Naming.lookup(service.url());
            DefaultListableBeanFactory factory = (DefaultListableBeanFactory) context.getBeanFactory();

            if (factory.containsSingleton(service.name())) {
                factory.destroySingleton(service.name());
            }

            factory.registerSingleton(service.name(), novoStub);
            LOGGER.info("[EJM] 🔁 Reconectado com sucesso ao serviço '{}'", service.name());

        } catch (Exception e) {
            LOGGER.error("[EJM] ❌ Falha ao reconectar '{}': {}", service.name(), e.getMessage());
        }
    }

    public void stop() {
        this.running = false;
        LOGGER.info("[EJM] 🛑 Monitor RMI finalizado.");
    }
}
