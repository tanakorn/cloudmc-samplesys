package edu.uchicago.cs.ucare.simc.server;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.simc.event.DiskWrite;
import edu.uchicago.cs.ucare.simc.event.DiskWriteAck;
import edu.uchicago.cs.ucare.simc.event.InterceptPacket;
import edu.uchicago.cs.ucare.simc.util.PacketReceiveAck;
import edu.uchicago.cs.ucare.simc.util.PacketReleaseCallback;

public abstract class ModelCheckingServerAbstract implements ModelCheckingServer {
    
    protected final Logger log;
    protected String interceptorName;
    protected LinkedBlockingQueue<InterceptPacket> packetQueue;
    protected LinkedBlockingQueue<DiskWrite> writeQueue;
    protected HashMap<DiskWrite, Boolean> writeFinished;
    protected HashMap<String, PacketReleaseCallback> callbackMap;
    
    protected PacketReceiveAck ack;
    protected DiskWriteAck writeAck;
    protected LinkedBlockingQueue<Integer> ackedIds;
    protected LinkedBlockingQueue<Integer> writeAckedIds;
    
    public ModelCheckingServerAbstract(String interceptorName, String ackName) {
        this.interceptorName = interceptorName;
        log = LoggerFactory.getLogger(this.getClass() + "." + interceptorName);
        packetQueue = new LinkedBlockingQueue<InterceptPacket>();
        writeQueue = new LinkedBlockingQueue<DiskWrite>();
        writeFinished = new HashMap<DiskWrite, Boolean>();
        callbackMap = new HashMap<String, PacketReleaseCallback>();
        ack = new PacketReceiveAckImpl();
        writeAck = new DiskWriteAckImpl();
        ackedIds = new LinkedBlockingQueue<Integer>();
        writeAckedIds = new LinkedBlockingQueue<Integer>();
        try {
            PacketReceiveAck ackStub = (PacketReceiveAck) 
                    UnicastRemoteObject.exportObject(ack, 0);
            Registry r = LocateRegistry.getRegistry();
            r.rebind(interceptorName + ackName, ackStub);
            DiskWriteAck writeAckStub = (DiskWriteAck) UnicastRemoteObject.exportObject(writeAck, 0);
            r.rebind(interceptorName + ackName + "DiskWrite", writeAckStub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
    
    public void waitForAck(InterceptPacket packet) throws InterruptedException {
        waitForAck(packet.getId());
    }
    
    public void waitForAck(int packetId) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Ack waiting for packet id " + packetId);
        }
        Integer ackedId = ackedIds.poll(1, TimeUnit.SECONDS);
        if (ackedId == null) {
            log.warn("No ack for packet " + packetId);
        } else if (ackedId != packetId) {
            log.warn("Inconsistent ack, wait for " + packetId + 
                        " but got " + ackedId + ", this might be because of some limitation");
        }
    }
    
    public boolean commitAndWait(InterceptPacket packet) throws InterruptedException {
        if (commit(packet)) {
            waitForAck(packet);
            return true;
        }
        return false;
    }
    
    public void waitForWrite(DiskWrite write) throws InterruptedException {
        waitForWrite(write.getWriteId());
    }

    public void waitForWrite(int writeId) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Ack waiting for write id " + writeId);
        }
        Integer ackedId = writeAckedIds.take();
        if (ackedId != writeId) {
            log.warn("Inconsistent ack, wait for " + writeId + 
                        " but got " + ackedId + ", this might be because of some limitation");
        }
    }
    
    public boolean writeAndWait(DiskWrite write) throws InterruptedException {
        if (write(write)) {
            waitForWrite(write.getWriteId());
            return true;
        }
        return false;
    }
    
    protected class PacketReceiveAckImpl implements PacketReceiveAck {
        
        final Logger LOG = LoggerFactory.getLogger(PacketReceiveAckImpl.class);
        
        @Override
        public void ack(int packetId, int id) throws RemoteException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acking back for packet id " + packetId + " from node id " + id);
            }
            ackedIds.add(packetId);
        }
        
    }
    
    protected class DiskWriteAckImpl implements DiskWriteAck {
        
        final Logger LOG = LoggerFactory.getLogger(DiskWriteAckImpl.class);

        @Override
        public void ack(int writeId, int nodeId) throws RemoteException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acking back for disk write id " + writeId + " from node id " + nodeId);
            }
            writeAckedIds.add(writeId);
        }
        
    }

}
