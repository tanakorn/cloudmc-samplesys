package mc;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface PacketReceiveAck extends Remote {
    
    public void ack(int packetId, int id) throws RemoteException;

}
