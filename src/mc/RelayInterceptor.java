package mc;

import java.rmi.RemoteException;

public class RelayInterceptor extends CallbackAckInterceptor {
    
    public RelayInterceptor(String interceptorName, String ackName) {
        super(interceptorName, ackName);
    }

    @Override
    public void offerPacket(InterceptPacket packet) throws RemoteException {
        try {
            commitAndWait(packet);
        } catch (InterruptedException e) {
            log.error("", e);
        }
    }

}
