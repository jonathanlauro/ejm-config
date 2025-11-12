package br.com.ejm.ejm_config.config;

import br.com.ejm.ejm_config.monitor.RmiReconnectionMonitor;
import br.com.ejm.ejm_config.utils.PackageScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.rmi.Naming;
import java.util.ArrayList;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class EjmClientAutoConfig implements BeanDefinitionRegistryPostProcessor, SmartInitializingSingleton, ApplicationContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(EjmClientAutoConfig.class);

    private final List<RmiServiceConfig> services = new ArrayList<>();
    private ConfigurableApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        if (applicationContext instanceof ConfigurableApplicationContext configurable) {
            this.context = configurable;
        } else {
            throw new IllegalStateException("[EJM] Contexto Spring não é configurável!");
        }
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            LOGGER.info("[EJM] 🔍 Iniciando registro de RMI Clients...");

            InputStream input = getClass().getClassLoader().getResourceAsStream("config-ejm.xml");
            if (input == null) {
                LOGGER.warn("[EJM] ⚠️ Arquivo config-ejm.xml não encontrado em resources!");
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(input);
            NodeList rmiNodes = doc.getElementsByTagName("rmi");

            for (int i = 0; i < rmiNodes.getLength(); i++) {
                Element rmiElem = (Element) rmiNodes.item(i);
                String basePackage = rmiElem.getElementsByTagName("base-package").item(0).getTextContent();
                String host = rmiElem.getElementsByTagName("host").item(0).getTextContent();
                int port = Integer.parseInt(rmiElem.getElementsByTagName("port").item(0).getTextContent());

                for (Class<?> iface : PackageScanner.findInterfaces(basePackage)) {
                    String serviceName = iface.getSimpleName();
                    String rmiUrl = String.format("rmi://%s:%d/%s", host, port, serviceName);
                    LOGGER.info("[EJM] 📡 Registrando stub: {} -> {}", serviceName, rmiUrl);

                    try {
                        Object stub = Naming.lookup(rmiUrl);
                        RootBeanDefinition def = new RootBeanDefinition(iface);
                        def.setInstanceSupplier(() -> iface.cast(stub));
                        registry.registerBeanDefinition(serviceName, def);

                        services.add(new RmiServiceConfig(serviceName, rmiUrl, iface));
                        LOGGER.info("[EJM] ✅ Bean '{}' registrado como RMI stub.", serviceName);
                    } catch (Exception ex) {
                        LOGGER.error("[EJM] ❌ Falha ao conectar '{}': {}", serviceName, ex.getMessage());
                    }
                }
            }

            LOGGER.info("[EJM] ✅ Registro de RMI Clients concluído.");
        } catch (Exception e) {
            LOGGER.error("[EJM] ❌ Erro geral ao registrar RMI clients: {}", e.getMessage(), e);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        // não utilizado
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (services.isEmpty()) {
            LOGGER.warn("[EJM] ⚠️ Nenhum serviço RMI registrado, monitor não iniciado.");
            return;
        }

        LOGGER.info("[EJM] 🧠 Iniciando monitoramento RMI em background...");

        Thread monitorThread = new Thread(new RmiReconnectionMonitor(context, services), "ejm-rmi-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    public List<RmiServiceConfig> getServices() {
        return services;
    }

    // Classe auxiliar para armazenar configs de cada serviço
    public record RmiServiceConfig(String name, String url, Class<?> iface) {}
}
