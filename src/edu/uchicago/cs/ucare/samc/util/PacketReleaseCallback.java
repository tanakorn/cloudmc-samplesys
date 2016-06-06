package edu.uchicago.cs.ucare.samc.util;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PacketReleaseCallback extends Remote {
    
    public boolean callback(int packetId) throws RemoteException;

}
