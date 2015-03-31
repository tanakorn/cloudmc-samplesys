package mc;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Interceptor extends Remote {
    
    public void offerPacket(InterceptPacket packet) throws RemoteException;
    public boolean waitPacket(int toId) throws RemoteException;
    
    public void requestWrite(DiskWrite write) throws RemoteException;

    // Just for debugging, don't use this for real model checking
    public void requestWriteImmediately(DiskWrite write) throws RemoteException;
}
