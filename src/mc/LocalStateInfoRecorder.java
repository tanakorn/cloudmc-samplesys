package mc;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface LocalStateInfoRecorder extends Remote {
	
	public void setLocalState(int nodeId, LocalState localState) throws RemoteException;

}
