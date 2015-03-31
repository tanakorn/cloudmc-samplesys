package mc;

import java.rmi.RemoteException;

public interface CallbackInterceptor extends Interceptor {
    
    public void registerCallback(int id, String callbackName) throws RemoteException;

}
