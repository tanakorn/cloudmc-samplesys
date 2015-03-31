package mc;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface SteadyStateListener extends Remote {
    
    public void informActiveState(int id) throws RemoteException;
    public void informSteadyState(int id, int runningState) throws RemoteException;

}
