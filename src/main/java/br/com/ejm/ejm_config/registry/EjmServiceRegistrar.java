package br.com.ejm.ejm_config.registry;

import br.com.ejm.ejm_config.annotations.EjmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

@Component
public class EjmServiceRegistrar implements BeanPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger("EJM");

    private final Registry registry;

    public EjmServiceRegistrar() {
        Registry tempRegistry;
        try {
            try {
                tempRegistry = LocateRegistry.getRegistry(1099);
                tempRegistry.list(); // força a conexão
                LOGGER.info("[RMI] Registry existente detectado na porta 1099");
            } catch (RemoteException e) {
                tempRegistry = LocateRegistry.createRegistry(1099);
                LOGGER.error("[RMI] Novo registry criado na porta 1099");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Erro ao inicializar RMI Registry", ex);
        }
        this.registry = tempRegistry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        EjmService annotation = bean.getClass().getAnnotation(EjmService.class);
        if (annotation != null) {
            try {
                String serviceName = !annotation.value().isEmpty()
                        ? annotation.value()
                        : bean.getClass().getInterfaces()[0].getSimpleName();

                if (bean instanceof Remote remoteBean) {
                    Remote stub = (Remote) UnicastRemoteObject.exportObject(remoteBean, 0);
                    registry.rebind(serviceName, stub);
                    LOGGER.info("[EJM] Serviço '{}' registrado com sucesso. {}", serviceName, serviceName);
                } else {
                    LOGGER.info("[EJM] O bean '{}' anotado com @EjmService não implementa java.rmi.Remote.{}", beanName, beanName);
                }
            } catch (RemoteException e) {
                throw new RuntimeException("Erro ao registrar serviço RMI: " + beanName, e);
            }
        }
        return bean;
    }
}
