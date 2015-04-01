package edu.uchicago.cs.ucare.simc.server;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.simc.event.DiskWrite;
import edu.uchicago.cs.ucare.simc.event.InterceptPacket;
import edu.uchicago.cs.ucare.simc.util.PacketReleaseCallback;

public abstract class CallbackInterceptorAbstract implements ModelCheckingServer {
    
    protected final Logger log;
    protected String interceptorName;
    protected LinkedBlockingQueue<InterceptPacket> packetQueue;
    protected LinkedBlockingQueue<DiskWrite> writeQueue;
    protected HashMap<DiskWrite, Boolean> writeFinished;
    protected HashMap<String, PacketReleaseCallback> callbackMap;
    
    public CallbackInterceptorAbstract(String interceptorName) {
        this.interceptorName = interceptorName;
        log = LoggerFactory.getLogger(this.getClass() + "." + interceptorName);
        packetQueue = new LinkedBlockingQueue<InterceptPacket>();
        writeQueue = new LinkedBlockingQueue<DiskWrite>();
        writeFinished = new HashMap<DiskWrite, Boolean>();
        callbackMap = new HashMap<String, PacketReleaseCallback>();
    }
    
    @Override
    public void offerPacket(InterceptPacket packet) throws RemoteException {
        try {
            packetQueue.put(packet);
            log.info("Intercept packet " + packet.toString());
        } catch (InterruptedException e) {
            throw new RemoteException(e.toString());
        }
    }
    
    @Override
    public boolean waitPacket(int toId) throws RemoteException {
        return true;
    }
    
    @Override
    public void requestWrite(DiskWrite write) {
        log.info("Intercept disk write " + write.toString());
        writeFinished.put(write, false);
        synchronized (writeQueue) {
            writeQueue.add(write);
        }
        while (!writeFinished.get(write)) {
            synchronized (write) {
                try {
                    write.wait();
                } catch (InterruptedException e) {
                    log.error("", e);
                }
            }
        }
        log.debug("Enable write " + write.toString());
        writeFinished.remove(write);
    }
    
    @Override
    public void requestWriteImmediately(DiskWrite write) {
        
    }
    
    @Override
    public void registerCallback(int id, String callbackName) throws RemoteException {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Registering callback from node " + id + 
                        ", callbackname " + callbackName);
            }
            PacketReleaseCallback callback =  (PacketReleaseCallback) 
                    Naming.lookup(interceptorName + callbackName);
            callbackMap.put(callbackName, callback);
        } catch (Exception e) {
            log.error("", e);
        }
    }
    
    public boolean commit(InterceptPacket packet) {
        try {
            PacketReleaseCallback callback = callbackMap.get(packet.getCallbackId());
            log.info("Commiting " + packet.toString());
            return callback.callback(packet.getId());
        } catch (Exception e) {
            log.warn("There is an error when committing this packet, " + packet.toString());
            return false;
        }
    }
    
    public boolean write(DiskWrite write) {
        if (writeQueue.contains(write)) {
            log.info("Enable write " + write.getWriteId());
            synchronized (write) {
                writeFinished.put(write, true);
                write.notify();
            }
            writeQueue.remove(write);
            return true;
        }
        return false;
    }

}
