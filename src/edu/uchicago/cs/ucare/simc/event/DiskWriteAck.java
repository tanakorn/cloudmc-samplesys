package edu.uchicago.cs.ucare.simc.event;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface DiskWriteAck extends Remote {
    
    public void ack(int writeId, int nodeId) throws RemoteException;

}
