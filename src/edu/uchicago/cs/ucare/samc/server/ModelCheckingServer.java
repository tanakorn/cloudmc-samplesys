package edu.uchicago.cs.ucare.samc.server;

import java.rmi.Remote;
import java.rmi.RemoteException;

import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.util.LocalState;

public interface ModelCheckingServer extends Remote {
    
    public void registerCallback(int id, String callbackName) throws RemoteException;
    
    public void offerPacket(InterceptPacket packet) throws RemoteException;
    public boolean waitPacket(int toId) throws RemoteException;
    
    public void requestWrite(DiskWrite write) throws RemoteException;

    // Just for debugging, don't use this for real model checking
    public void requestWriteImmediately(DiskWrite write) throws RemoteException;

    public void setTestId(int testId) throws RemoteException;
    public void updateLocalState(int nodeId, int state) throws RemoteException;
    public void recordCodeTrace(int nodeId, int stackTraceHash) throws RemoteException;
    public void recordProtocol(int nodeId, int protocolHash) throws RemoteException;

	public void setLocalState(int nodeId, LocalState localState) throws RemoteException;

    public void informActiveState(int id) throws RemoteException;
    public void informSteadyState(int id, int runningState) throws RemoteException;

}
