package edu.uchicago.cs.ucare.simc.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.uchicago.cs.ucare.simc.event.DiskWrite;
import edu.uchicago.cs.ucare.simc.event.InterceptPacket;
import edu.uchicago.cs.ucare.simc.transition.DiskWriteTransition;
import edu.uchicago.cs.ucare.simc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.simc.transition.Transition;
import edu.uchicago.cs.ucare.simc.util.EnsembleController;
import edu.uchicago.cs.ucare.simc.util.WorkloadFeeder;

public abstract class ModelChecker extends CallbackAckInterceptor {

    private static String CODE_DIR = "code";
    private static String PATH_FILE = "path";
    private static String LOCAL_FILE = "local";
    private static String PROTOCOL_FILE = "protocol";
    private static String RESULT_FILE = "result";

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

    @SuppressWarnings("unchecked")
    public ModelChecker(String interceptorName, String ackName, int numNode,
            String testRecordDirPath, EnsembleController zkController,
            WorkloadFeeder feeder) {
        super(interceptorName, ackName);
        this.numNode = numNode;
        this.testRecordDirPath = testRecordDirPath;
        this.zkController = zkController;
        this.feeder = feeder;
        pathRecordFile = null;
        localRecordFile = null;
        codeRecordFiles = new FileOutputStream[numNode];
        protocolRecordFile = null;
        resultFile = null;
        isNodeOnline = new boolean[numNode];
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        resetTest();
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

    abstract protected boolean isSystemSteady();

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

    @Override
    public void waitForAck(InterceptPacket packet) throws InterruptedException {
        if (isNodeOnline(packet.getToId())) {
            super.waitForAck(packet);
        }
    }

    @SuppressWarnings("unchecked")
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
    }

    public boolean runNode(int id) {
        if (isNodeOnline(id)) {
            return true;
        }
        zkController.startNode(id);
        setNodeOnline(id, true);
        return true;
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
    
    @Override
    public boolean write(DiskWrite write) {
        boolean result = super.write(write);
        return isNodeOnline(write.getNodeId()) ? result : false;
    }

}
