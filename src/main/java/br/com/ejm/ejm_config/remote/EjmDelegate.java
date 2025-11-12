package br.com.ejm.ejm_config.remote;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface EjmDelegate extends Remote {
    default boolean ping() throws RemoteException {
        return true;
    }
}
