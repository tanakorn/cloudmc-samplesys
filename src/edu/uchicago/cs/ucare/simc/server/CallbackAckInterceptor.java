package edu.uchicago.cs.ucare.simc.server;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import mc.DiskWrite;
import mc.DiskWriteAck;
import mc.InterceptPacket;
import mc.PacketReceiveAck;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class CallbackAckInterceptor  extends CallbackInterceptorAbstract {
    
    protected PacketReceiveAck ack;
    protected DiskWriteAck writeAck;
    protected LinkedBlockingQueue<Integer> ackedIds;
    protected LinkedBlockingQueue<Integer> writeAckedIds;
    
    public CallbackAckInterceptor(String interceptorName, String ackName) {
        super(interceptorName);
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
//            waitForAck(packetId);
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
