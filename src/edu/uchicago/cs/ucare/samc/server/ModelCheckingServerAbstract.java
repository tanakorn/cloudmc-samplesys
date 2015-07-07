package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.DiskWriteAck;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.DiskWriteTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.EnsembleController;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.LocalState;
import edu.uchicago.cs.ucare.samc.util.PacketReceiveAck;
import edu.uchicago.cs.ucare.samc.util.PacketReleaseCallback;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;
import edu.uchicago.cs.ucare.samc.util.WorkloadFeeder;

public abstract class ModelCheckingServerAbstract implements ModelCheckingServer {
    
	private static String CODE_DIR = "code";
    private static String PATH_FILE = "path";
    private static String LOCAL_FILE = "local";
    private static String PROTOCOL_FILE = "protocol";
    private static String RESULT_FILE = "result";
	
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
    
    public int numNode;
    public int numCurrentCrash;
    public int numCurrentReboot;
    protected int[] localState;
    public boolean[] isNodeOnline;

    protected ConcurrentLinkedQueue<InterceptPacket>[][] senderReceiverQueues;

    protected int testId;

    protected boolean isInitGlobalState;
    protected int initialGlobalState;
    protected int globalState;

    protected String testRecordDirPath;
    protected String idRecordDirPath;
    protected String codeRecordDirPath;
    protected String pathRecordFilePath;
    protected String localRecordFilePath;
    protected String protocolRecordPath;
    protected String resultFilePath;
    protected FileOutputStream pathRecordFile;
    protected FileOutputStream localRecordFile;
    protected FileOutputStream[] codeRecordFiles;
    protected FileOutputStream protocolRecordFile;
    protected FileOutputStream local2File;
    protected FileOutputStream resultFile;

    protected EnsembleController zkController;
    protected WorkloadFeeder feeder;
    protected SpecVerifier verifier;
    
    protected LinkedList<Transition> currentEnabledTransitions = new LinkedList<Transition>();
    protected boolean[] isNodeSteady;
    protected Boolean isStarted;
    protected Thread modelChecking;
    protected int[] numPacketSentToId;
    
    public LeaderElectionLocalState[] localStates;

    public ModelCheckingServerAbstract(String interceptorName, String ackName, int numNode,
            String testRecordDirPath, EnsembleController zkController,
            WorkloadFeeder feeder) {
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
        this.numNode = numNode;
        this.testRecordDirPath = testRecordDirPath;
        this.zkController = zkController;
        this.feeder = feeder;
        this.verifier = (SpecVerifier) feeder.allVerifiers.peek();
        pathRecordFile = null;
        localRecordFile = null;
        codeRecordFiles = new FileOutputStream[numNode];
        protocolRecordFile = null;
        resultFile = null;
        isNodeOnline = new boolean[numNode];
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        localStates = new LeaderElectionLocalState[numNode];
        this.resetTest();
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
    
    @Override
    public void setLocalState(int nodeId, LocalState localState) throws RemoteException {
    	localStates[nodeId] = (LeaderElectionLocalState) localState;
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
    
    @Override
    public void offerPacket(InterceptPacket packet) throws RemoteException {
        senderReceiverQueues[packet.getFromId()][packet.getToId()].add(packet);
        log.info("Intercept packet " + packet.toString());
    }
    
    public void getOutstandingTcpPacketTransition(LinkedList<Transition> transitionList) {
        boolean[][] filter = new boolean[numNode][numNode];
        for (int i = 0; i < numNode; ++i) {
            Arrays.fill(filter[i], true);
        }
        for (Transition t : transitionList) {
            if (t instanceof PacketSendTransition) {
                PacketSendTransition p = (PacketSendTransition) t;
                filter[p.getPacket().getFromId()][p.getPacket().getToId()] = false;
            }
        }
        LinkedList<PacketSendTransition> buffer = new LinkedList<PacketSendTransition>();
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                if (filter[i][j] && !senderReceiverQueues[i][j].isEmpty()) {
                    buffer.add(new PacketSendTransition(this, senderReceiverQueues[i][j].remove()));
                }
            }
        }
        Collections.sort(buffer, new Comparator<PacketSendTransition>() {
            @Override
            public int compare(PacketSendTransition o1, PacketSendTransition o2) {
                Integer i1 = o1.getPacket().getId();
                Integer i2 = o2.getPacket().getId();
                return i1.compareTo(i2);
            }
        });
        transitionList.addAll(buffer);
    }
    
    public void getOutstandingTcpPacket(LinkedList<InterceptPacket> packetList) {
        boolean[][] filter = new boolean[numNode][numNode];
        for (int i = 0; i < numNode; ++i) {
            Arrays.fill(filter[i], true);
        }
        for (InterceptPacket p : packetList) {
            filter[p.getFromId()][p.getToId()] = false;
        }
        LinkedList<InterceptPacket> buffer = new LinkedList<InterceptPacket>();
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                if (filter[i][j] && !senderReceiverQueues[i][j].isEmpty()) {
                    buffer.add(senderReceiverQueues[i][j].remove());
                }
            }
        }
        Collections.sort(buffer, new Comparator<InterceptPacket>() {
            @Override
            public int compare(InterceptPacket o1, InterceptPacket o2) {
                Integer i1 = o1.getId();
                Integer i2 = o2.getId();
                return i1.compareTo(i2);
            }
        });
        packetList.addAll(buffer);
    }
    
    public void getOutstandingDiskWrite(LinkedList<Transition> list) {
        DiskWrite[] tmp = new DiskWrite[writeQueue.size()];
        synchronized (writeQueue) {
            writeQueue.toArray(tmp);
            writeQueue.clear();
        }
        Arrays.sort(tmp, new Comparator<DiskWrite>() {
            @Override
            public int compare(DiskWrite o1, DiskWrite o2) {
                Integer i1 = o1.getWriteId();
                Integer i2 = o2.getWriteId();
                return i1.compareTo(i2);
            }
        });
        for (DiskWrite write : tmp) {
            list.add(new DiskWriteTransition(this, write));
        }
    }
    
    protected boolean isThereEnabledPacket() {
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                if (!senderReceiverQueues[i][j].isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setTestId(int testId) {
        log.info("This test has id = " + testId);
        this.testId = testId;
        idRecordDirPath = testRecordDirPath + "/" + testId;
        File testRecordDir = new File(idRecordDirPath);
        if (!testRecordDir.exists()) {
            testRecordDir.mkdir();
        }
        protocolRecordPath = idRecordDirPath + "/" + PROTOCOL_FILE;
        pathRecordFilePath = idRecordDirPath + "/" + PATH_FILE;
        localRecordFilePath = idRecordDirPath + "/" + LOCAL_FILE;
        codeRecordDirPath = idRecordDirPath + "/" + CODE_DIR;
        resultFilePath = idRecordDirPath + "/" + RESULT_FILE;
        File codeRecordDir = new File(codeRecordDirPath);
        if (!codeRecordDir.exists()) {
            codeRecordDir.mkdir();
        }
    }

    @Override
    public void updateLocalState(int id, int state) throws RemoteException {
        localState[id] = state;
        if (log.isDebugEnabled()) {
            log.debug("Node " + id + " update its local state to be " + state);
        }
    }

    @Override
    public void recordCodeTrace(int nodeId, int stackTraceHash)
            throws RemoteException {
        try {
            if (codeRecordFiles[nodeId] == null) {
                codeRecordFiles[nodeId] = new FileOutputStream(
                        codeRecordDirPath + "/" + nodeId);
            }
            codeRecordFiles[nodeId].write((stackTraceHash + "\n").getBytes());
        } catch (IOException e) {
            log.error("", e);
            throw new RemoteException("Cannot create or write code record file");
        }
    }

    @Override
    public void recordProtocol(int nodeId, int protocolHash)
            throws RemoteException {
        
        int fromHash = Arrays.hashCode(senderReceiverQueues[nodeId]);
        int toHash = 1;
        for (ConcurrentLinkedQueue<InterceptPacket>[] toQueue : senderReceiverQueues) {
            toHash = toHash * 31 + toQueue[nodeId].hashCode();
        }
        int protocol2Hash = protocolHash;
        protocol2Hash = protocol2Hash * 31 + fromHash;
        protocol2Hash = protocol2Hash * 31 + toHash;
        try {
            if (protocolRecordFile == null) {
                protocolRecordFile = new FileOutputStream(protocolRecordPath);
            }
            protocolRecordFile.write((nodeId + "," + protocolHash + "," + protocol2Hash + "\n").getBytes());
        } catch (IOException e) {
            log.error("", e);
            throw new RemoteException("Cannot create or write protocol record file");
        }
    }
    
    public void saveResult(String result) {
        try {
            if (resultFile == null) {
                resultFile = new FileOutputStream(resultFilePath);
            }
            resultFile.write(result.getBytes());
        } catch (IOException e) {
            log.error("", e);
        }
    }

    public void updateGlobalState() {
        int[] tmp = new int[numNode];
        for (int i = 0; i < numNode; ++i) {
            tmp[i] = isNodeOnline[i] ? localState[i] : 0;
        }
        globalState = Arrays.hashCode(tmp);
        log.debug("System update its global state to be " + globalState);
    }

    public int getGlobalState() {
        return globalState;
    }

    protected void initGlobalState() {
        updateGlobalState();
        initialGlobalState = globalState;
        try {
            pathRecordFile = new FileOutputStream(pathRecordFilePath);
            localRecordFile = new FileOutputStream(localRecordFilePath);
        } catch (FileNotFoundException e) {
            log.error("", e);
        }
    }

    public void waitForAck(InterceptPacket packet) throws InterruptedException {
        if (isNodeOnline(packet.getToId())) {
        	waitForAck(packet.getId());
        }
    }

    public boolean killNode(int id) {
        zkController.stopNode(id);
        setNodeOnline(id, false);
        for (int i = 0; i < numNode; ++i) {
            senderReceiverQueues[i][id].clear();
            senderReceiverQueues[id][i].clear();
        }
        return true;
    }

    public boolean runEnsemble() {
        zkController.startEnsemble();
        for (int i = 0; i < numNode; ++i) {
            setNodeOnline(i, true);
        }
        return true;
    }

    public boolean stopEnsemble() {
        zkController.stopEnsemble();
        for (int i = 0; i < numNode; ++i) {
            setNodeOnline(i, false);
            for (int j = 0; j < numNode; ++j) {
                senderReceiverQueues[i][j].clear();
                senderReceiverQueues[j][i].clear();
            }
        }
        return true;
    }

    public void setNodeOnline(int id, boolean isOnline) {
        isNodeOnline[id] = isOnline;
    }

    public boolean isNodeOnline(int id) {
        return isNodeOnline[id];
    }
    
    public void saveLocalState() {
        String tmp = "";
        for (int i = 0 ; i < numNode; ++i) {
            tmp += !isNodeOnline[i] ? 0 : localState[i];
            tmp += ",";
        }
        tmp += "\n";
        try {
            localRecordFile.write(tmp.getBytes());
        } catch (IOException e) {
            log.error("", e);
        }
    }
    
    public boolean write(DiskWrite write) {
        boolean result = false;
    	if (writeQueue.contains(write)) {
            log.info("Enable write " + write.getWriteId());
            synchronized (write) {
                writeFinished.put(write, true);
                write.notify();
            }
            writeQueue.remove(write);
            result = true;
        }
        return isNodeOnline(write.getNodeId()) ? result : false;
    }
    
    @Override
    public boolean waitPacket(int toId) throws RemoteException {
        while (isNodeOnline(toId)) {
            if (isSystemSteady() && !isThereOutstandingPacketTransition()) {
                return false;
            }
            synchronized (numPacketSentToId) {
                if (numPacketSentToId[toId] > 0) {
                    numPacketSentToId[toId]--;
                    return true;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("", e);
            }
        }
        return false;
    }
    
    public boolean isThereOutstandingPacketTransition() {
        boolean isThereProcessingEnabledPacket = false;
        for (Transition t : currentEnabledTransitions) {
            if (t instanceof PacketSendTransition && !((PacketSendTransition) t).getPacket().isObsolete()) {
                isThereProcessingEnabledPacket = true;
                break;
            }
        }
        return numPacketInSenderReceiverQueue() != 0 || isThereProcessingEnabledPacket;
    }
    
    public boolean commit(InterceptPacket packet) {
    	boolean result;
    	try {
            PacketReleaseCallback callback = callbackMap.get(packet.getCallbackId());
            log.info("Commiting " + packet.toString());
            result = callback.callback(packet.getId());
        } catch (Exception e) {
            log.warn("There is an error when committing this packet, " + packet.toString());
            result = false;
        }
        if (result) {
            synchronized (numPacketSentToId) {
                numPacketSentToId[packet.getToId()]++;
            }
            return true;
        }
        return false;
    }

    protected boolean isSystemSteady() {
        for (int i = 0; i < numNode; ++i) {
            if (!isNodeSteady(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void informSteadyState(int id, int runningState) throws RemoteException {
        setNodeSteady(id, true);
        if (log.isDebugEnabled()) {
            log.debug("Node " + id + " is in steady state");
        }
        synchronized (isStarted) {
            if (!isStarted && isSystemSteady()) {
                isStarted = true;
                initGlobalState();
                log.info("First system steady state, start model checker thread");
                modelChecking.start();
            }
        }
    }
    
    @Override
    public void informActiveState(int id) throws RemoteException {
        setNodeSteady(id, false);
    }
    
    protected int numPacketInSenderReceiverQueue() {
        int num = 0;
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                num += senderReceiverQueues[i][j].size();
            }
        }
        return num;
    }
    
    protected void setNodeSteady(int id, boolean isSteady) {
        isNodeSteady[id] = isSteady;
        
    }

    protected boolean isNodeSteady(long id) {
        return isNodeSteady[(int) id] || !isNodeOnline[(int) id];
    }
    
    protected void waitNodeSteady(int id) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Waiting node " + id + " to be in steady state");
        }
        int waitTick = 10;
        int i = 0;
        while (!isNodeSteady(id) && i++ < waitTick) {
//        while (!isNodeSteady(id)) {
            Thread.sleep(50);
        }
        if (i >= waitTick) {
            log.warn("Steady state for node " + id + " triggered by timeout");
        }
        setNodeSteady(id, true);
    }
    
    public boolean commitAndWait(InterceptPacket packet) throws InterruptedException {
        setNodeSteady(packet.getToId(), false);
        boolean result = false;
        if (commit(packet)) {
            waitForAck(packet);
            result = true;
        }
        if (result) {
            waitNodeSteady(packet.getToId());
            return true;
        } else {
            setNodeSteady(packet.getToId(), true);
            return false;
        }
    }
    
    public void resetTest() {
        log.debug("Test reset");
        writeQueue.clear();
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        testId = -1;
        numCurrentCrash = 0;
        numCurrentReboot = 0;
        localState = new int[numNode];
        globalState = 0;
        isInitGlobalState = false;
        if (pathRecordFile != null) {
            try {
                pathRecordFile.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
        if (localRecordFile != null) {
            try {
                localRecordFile.close();
            } catch (IOException e) {
                log.error("", e);
            }
        }
        if (protocolRecordFile != null) {
            try {
                protocolRecordFile.close();
                protocolRecordFile = null;
            } catch (IOException e) {
                log.error("", e);
            }
        }
        if (resultFile != null) {
            try {
                resultFile.close();
                resultFile = null;
            } catch (IOException e) {
                log.error("", e);
            }
        }
        if (local2File != null) {
            try {
                local2File.close();
                local2File = null;
            } catch (IOException e) {
                log.error("", e);
            }
        }
        for (int i = 0; i < numNode; ++i) {
            if (codeRecordFiles[i] != null) {
                try {
                    codeRecordFiles[i].close();
                    codeRecordFiles[i] = null;
                } catch (IOException e) {
                    log.error("", e);
                }
            }
        }
        Arrays.fill(isNodeOnline, true);
        synchronized (this) {
            this.notifyAll();
        }
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                senderReceiverQueues[i][j] = new ConcurrentLinkedQueue<InterceptPacket>();
            }
        }
        isNodeSteady = new boolean[numNode];
        isStarted = false;
        numPacketSentToId = new int[numNode];
        for (int i = 0; i < localStates.length; ++i) {
        	localStates[i] = new LeaderElectionLocalState();
        	localStates[i].setLeader(i);
        	localStates[i].setRole(LeaderElectionMain.LOOKING);
        }
    }

    public boolean runNode(int id) {
    	if (isNodeOnline(id)) {
            return true;
        }
        zkController.startNode(id);
        setNodeOnline(id, true);
        setNodeSteady(id, false);
        try {
//          waitNodeSteady(id);
            // I'm sorry for this, waitNodeSteady now means wait for timeout 200 ms
            if (log.isDebugEnabled()) {
                log.debug("Waiting node " + id + " to be in real steady state");
            }
            int waitTick = 60;
            int i = 0;
            while (!isNodeSteady(id) && i++ < waitTick) {
//              while (!isNodeSteady(id)) {
                Thread.sleep(50);
            }
            if (i >= waitTick) {
                log.warn("Steady state for node " + id + " triggered by timeout");
            }
            setNodeSteady(id, true);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }
    
    abstract protected static class Explorer extends Thread {
        
        protected ModelCheckingServerAbstract checker;
        
        public Explorer(ModelCheckingServerAbstract checker) {
            this.checker = checker;
        }
        
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
