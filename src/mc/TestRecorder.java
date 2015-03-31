package mc;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TestRecorder extends Remote {

    public void setTestId(int testId) throws RemoteException;
    public void updateLocalState(int nodeId, int state) throws RemoteException;
    public void recordCodeTrace(int nodeId, int stackTraceHash) throws RemoteException;
    public void recordProtocol(int nodeId, int protocolHash) throws RemoteException;
    
}
