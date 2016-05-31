package edu.uchicago.cs.ucare.samc.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
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
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.DiskWriteAck;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.DiskWriteTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.LocalState;
import edu.uchicago.cs.ucare.samc.util.PacketReceiveAck;
import edu.uchicago.cs.ucare.samc.util.PacketReleaseCallback;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public abstract class ModelCheckingServerAbstract implements ModelCheckingServer {
    
	private static String CODE_DIR = "code";
    private static String PATH_FILE = "path";
    private static String LOCAL_FILE = "local";
    private static String PROTOCOL_FILE = "protocol";
    private static String RESULT_FILE = "result";
	
    protected final Logger LOG;
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
    protected LinkedList<InterceptPacket> localEventQueue;

    protected int testId;

    protected boolean isInitGlobalState;
    protected int initialGlobalState;
    protected int globalState;

    protected String testRecordDirPath;
    protected String workingDirPath;
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

    protected WorkloadDriver workloadDriver;
    protected SpecVerifier verifier;
    
    protected LinkedList<Transition> currentEnabledTransitions = new LinkedList<Transition>();
    protected boolean[] isNodeSteady;
    protected Boolean isStarted;
    protected Thread modelChecking;
    protected int[] numPacketSentToId;
    
    protected LinkedList<String> initialPath = new LinkedList<String>();
    protected int initialPathCounter;
    protected boolean hasInitialPath;
    protected boolean hasFinishedInitialPath;
    
    // dmck config
    protected int steadyStateTimeout;
    protected int initSteadyStateTimeout;
    protected int waitEndExploration;
    
    public LeaderElectionLocalState[] localStates;
    public String scmStates;
    
    protected String ipcDir;

    @SuppressWarnings("unchecked")
	public ModelCheckingServerAbstract(String interceptorName, String ackName, int numNode,
            String testRecordDirPath, String workingDirPath, WorkloadDriver workloadDriver) {
        this.interceptorName = interceptorName;
        LOG = LoggerFactory.getLogger(this.getClass() + "." + interceptorName);
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
        this.workingDirPath = workingDirPath;
        this.workloadDriver = workloadDriver;
        this.verifier = workloadDriver.verifier;
        pathRecordFile = null;
        localRecordFile = null;
        codeRecordFiles = new FileOutputStream[numNode];
        protocolRecordFile = null;
        resultFile = null;
        isNodeOnline = new boolean[numNode];
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        localEventQueue = new LinkedList<InterceptPacket>();
        localStates = new LeaderElectionLocalState[numNode];
        scmStates = "";
        ipcDir = "";
        getDMCKConfig();
        this.resetTest();
    }
    
    @SuppressWarnings("unchecked")
	public ModelCheckingServerAbstract(String interceptorName, String ackName, int numNode,
            String testRecordDirPath, String workingDirPath, WorkloadDriver workloadDriver, 
            String ipcDir) {
        this.interceptorName = interceptorName;
        LOG = LoggerFactory.getLogger(this.getClass() + "." + interceptorName);
        packetQueue = new LinkedBlockingQueue<InterceptPacket>();
        writeQueue = new LinkedBlockingQueue<DiskWrite>();
        writeFinished = new HashMap<DiskWrite, Boolean>();
        callbackMap = new HashMap<String, PacketReleaseCallback>();
        ack = new PacketReceiveAckImpl();
        writeAck = new DiskWriteAckImpl();
        ackedIds = new LinkedBlockingQueue<Integer>();
        writeAckedIds = new LinkedBlockingQueue<Integer>();
        if(ipcDir == ""){
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
        this.numNode = numNode;
        this.testRecordDirPath = testRecordDirPath;
        this.workingDirPath = workingDirPath;
        this.workloadDriver = workloadDriver;
        this.verifier = workloadDriver.verifier;
        pathRecordFile = null;
        localRecordFile = null;
        codeRecordFiles = new FileOutputStream[numNode];
        protocolRecordFile = null;
        resultFile = null;
        isNodeOnline = new boolean[numNode];
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        localEventQueue = new LinkedList<InterceptPacket>();
        localStates = new LeaderElectionLocalState[numNode];
        scmStates = "";
        this.ipcDir = ipcDir;
        getDMCKConfig();
        this.resetTest();
    }
    
    public void getDMCKConfig(){
    	try{
	    	String dmckConfigFile = workingDirPath + "/dmck.conf";
	    	Properties dmckConf = new Properties();
	        FileInputStream configInputStream = new FileInputStream(dmckConfigFile);
	        dmckConf.load(configInputStream);
	        configInputStream.close();

	        initSteadyStateTimeout = Integer.parseInt(dmckConf.getProperty("initSteadyStateTimeout"));
	        steadyStateTimeout = Integer.parseInt(dmckConf.getProperty("steadyStateTimeout"));
	        waitEndExploration = Integer.parseInt(dmckConf.getProperty("waitEndExploration"));
        } catch (Exception e){
    		LOG.error("Error in reading dmck config file");
    	}
    }
    
    public void setInitialPath(String initialPath){
    	this.hasInitialPath = !initialPath.isEmpty();
    	this.hasFinishedInitialPath = !hasInitialPath;
    	if(hasInitialPath){
        	System.out.println("[INFO] initialPath: " + initialPath);
    		readInitialPath(initialPath);
    	}
    }
    
    public void readInitialPath(String initialPath){
    	// read file from initialPath file
    	try{
	    	BufferedReader initialPathReader = new BufferedReader(new FileReader(initialPath));
	    	String line;
	    	while ((line = initialPathReader.readLine()) != null){
	    		this.initialPath.add(line);
	    	}
    	} catch (Exception e){
    		LOG.error("Error in readInitialPath");
    		System.out.println("Error in readInitialPath");
    	}
    }
    
    public void requestWrite(DiskWrite write) {
        LOG.info("Intercept disk write " + write.toString());
        writeFinished.put(write, false);
        synchronized (writeQueue) {
            writeQueue.add(write);
        }
        while (!writeFinished.get(write)) {
            synchronized (write) {
                try {
                    write.wait();
                } catch (InterruptedException e) {
                    LOG.error("", e);
                }
            }
        }
        LOG.debug("Enable write " + write.toString());
        writeFinished.remove(write);
    }
    
    public void requestWriteImmediately(DiskWrite write) {
        
    }
    
    public void registerCallback(int id, String callbackName) throws RemoteException {
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Registering callback from node " + id + 
                        ", callbackname " + callbackName);
            }
            PacketReleaseCallback callback =  (PacketReleaseCallback) 
                    Naming.lookup(interceptorName + callbackName);
            callbackMap.put(callbackName, callback);
        } catch (Exception e) {
            LOG.error("", e);
        }
    }
    
    public void waitForAck(int packetId) throws InterruptedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ack waiting for packet id " + packetId);
        }
        Integer ackedId = ackedIds.poll(1, TimeUnit.SECONDS);
        if (ackedId == null) {
            LOG.warn("No ack for packet " + packetId);
        } else if (ackedId != packetId) {
            LOG.warn("Inconsistent ack, wait for " + packetId + 
                        " but got " + ackedId + ", this might be because of some limitation");
        }
    }
    
    public void setLocalState(int nodeId, LocalState localState) throws RemoteException {
    	localStates[nodeId] = (LeaderElectionLocalState) localState;
    }
    
    public void setSCMState(String msg){
    	scmStates += msg;
    }
    
    public void waitForWrite(DiskWrite write) throws InterruptedException {
        waitForWrite(write.getWriteId());
    }

    public void waitForWrite(int writeId) throws InterruptedException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Ack waiting for write id " + writeId);
        }
        Integer ackedId = writeAckedIds.take();
        if (ackedId != writeId) {
            LOG.warn("Inconsistent ack, wait for " + writeId + 
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
    
    public void offerPacket(InterceptPacket packet) throws RemoteException {
        senderReceiverQueues[packet.getFromId()][packet.getToId()].add(packet);
        LOG.info("Intercept packet " + packet.toString());
    }
    
    public void offerLocalEvent(InterceptPacket packet) {
    	localEventQueue.add(packet);
        LOG.info("Intercept packet " + packet.toString());
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
            	// reorder
                if (filter[i][j] && !senderReceiverQueues[i][j].isEmpty()) {
                    buffer.add(new PacketSendTransition(this, senderReceiverQueues[i][j].remove()));
                }
            }
        }
        Collections.sort(buffer, new Comparator<PacketSendTransition>() {
            public int compare(PacketSendTransition o1, PacketSendTransition o2) {
                Integer i1 = o1.getPacket().getId();
                Integer i2 = o2.getPacket().getId();
                return i1.compareTo(i2);
            }
        });
        transitionList.addAll(buffer);
        
        // add local events to queue
        getLocalEvents(transitionList);
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
            	// reorder
                if (filter[i][j] && !senderReceiverQueues[i][j].isEmpty()) {
                    buffer.add(senderReceiverQueues[i][j].remove());
                }
            }
        }
        Collections.sort(buffer, new Comparator<InterceptPacket>() {
            public int compare(InterceptPacket o1, InterceptPacket o2) {
                Integer i1 = o1.getId();
                Integer i2 = o2.getId();
                return i1.compareTo(i2);
            }
        });
        packetList.addAll(buffer);
    }
    
    public void getLocalEvents(LinkedList<Transition> transitionList){
    	LinkedList<PacketSendTransition> buffer = new LinkedList<PacketSendTransition>();
    	for(int i = localEventQueue.size() - 1; i>-1; i--){
    		buffer.add(new PacketSendTransition(this, localEventQueue.remove(i)));
    	}
    	transitionList.addAll(buffer);
    }
    
    public void printTransitionQueues(LinkedList<Transition> transitionList){
    	System.out.println("-----------------------------");
        System.out.println("[DEBUG] Events in Queue : " + transitionList.size());
        int counter = 1;
        for (Transition t : transitionList) {
        	if(t != null){
        		System.out.println(counter + ". " + t.toString());
        	} else {
        		System.out.println(counter + ". " + "null event");
        	}
        	counter++;
        }
        System.out.println("-----------------------------");
    }
    
    public void printPacketQueues(LinkedList<InterceptPacket> packetList){
    	System.out.println("-----------------------------");
        System.out.println("[DEBUG] Packets in Queue : " + packetList.size());
        int counter = 1;
        for (InterceptPacket p : packetList) {
        	if(p != null){
        		System.out.println(counter + ". " + p.toString());
        	} else {
        		System.out.println(counter + ". " + "null packet");
        	}
        	counter++;
        }
        System.out.println("-----------------------------");
    }
    
    public void getOutstandingDiskWrite(LinkedList<Transition> list) {
        DiskWrite[] tmp = new DiskWrite[writeQueue.size()];
        synchronized (writeQueue) {
            writeQueue.toArray(tmp);
            writeQueue.clear();
        }
        Arrays.sort(tmp, new Comparator<DiskWrite>() {
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
            	// reorder
                if (!senderReceiverQueues[i][j].isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    public void setTestId(int testId) {
        LOG.info("This test has id = " + testId);
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

    public void updateLocalState(int id, int state) throws RemoteException {
        localState[id] = state;
        if (LOG.isDebugEnabled()) {
            LOG.debug("Node " + id + " update its local state to be " + state);
        }
    }

    public void recordCodeTrace(int nodeId, int stackTraceHash)
            throws RemoteException {
        try {
            if (codeRecordFiles[nodeId] == null) {
                codeRecordFiles[nodeId] = new FileOutputStream(
                        codeRecordDirPath + "/" + nodeId);
            }
            codeRecordFiles[nodeId].write((stackTraceHash + "\n").getBytes());
        } catch (IOException e) {
            LOG.error("", e);
            throw new RemoteException("Cannot create or write code record file");
        }
    }

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
            LOG.error("", e);
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
            LOG.error("", e);
        }
    }

    public void updateGlobalState() {
        int[] tmp = new int[numNode];
        for (int i = 0; i < numNode; ++i) {
            tmp[i] = isNodeOnline[i] ? localState[i] : 0;
        }
        globalState = Arrays.hashCode(tmp);
        LOG.debug("System update its global state to be " + globalState);
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
            LOG.error("", e);
        }
    }

    public void waitForAck(InterceptPacket packet) throws InterruptedException {
        if (isNodeOnline(packet.getToId())) {
        	waitForAck(packet.getId());
        }
    }

    public boolean killNode(int id) {
        workloadDriver.stopNode(id);
        setNodeOnline(id, false);
        for (int i = 0; i < numNode; ++i) {
            senderReceiverQueues[i][id].clear();
            senderReceiverQueues[id][i].clear();
        }
        return true;
    }

    public boolean runEnsemble() {
        for (int i = 0; i < numNode; ++i) {
            setNodeOnline(i, true);
        }
        workloadDriver.startEnsemble();
        return true;
    }

    public boolean stopEnsemble() {
        workloadDriver.stopEnsemble();
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
            LOG.error("", e);
        }
    }
    
    public boolean write(DiskWrite write) {
        boolean result = false;
    	if (writeQueue.contains(write)) {
            LOG.info("Enable write " + write.getWriteId());
            synchronized (write) {
                writeFinished.put(write, true);
                write.notify();
            }
            writeQueue.remove(write);
            result = true;
        }
        return isNodeOnline(write.getNodeId()) ? result : false;
    }
    
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
                LOG.error("", e);
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
    		if(ipcDir != ""){
    			try{
    	        	PrintWriter writer = new PrintWriter(ipcDir + "/new/" + packet.getId(), "UTF-8");
    	        	writer.println("eventId=" + packet.getId());
    		        writer.close();
    		        
    		    	System.out.println("Enable event with ID : " + packet.getId());
    		        
    		        Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + packet.getId() + " " + 
    		        		ipcDir + "/ack/" + packet.getId());
            	} catch (Exception e) {
            		System.out.println("[DEBUG] error in creating new file : " + packet.getId());
            	}
            	
    			result = true;
    		} else {
    			PacketReleaseCallback callback = callbackMap.get(packet.getCallbackId());
                LOG.info("Commiting " + packet.toString());
                result = callback.callback(packet.getId());
    		}
        } catch (Exception e) {
            LOG.warn("There is an error when committing this packet, " + packet.toString());
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

    public void informSteadyState(int id, int runningState) throws RemoteException {
        setNodeSteady(id, true);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Node " + id + " is in steady state");
        }
        synchronized (isStarted) {
            if (!isStarted && isSystemSteady()) {
                isStarted = true;
                initGlobalState();
                LOG.info("First system steady state, start model checker thread");
            	System.out.println("Start Reordering");
        		modelChecking.start();
            }
        }
    }
    
    public void waitOnSteadyStatesByTimeout(){
    	System.out.println("Starts wait on first steady states");
    	LOG.info("Starts wait on first steady states");
    	try{
    		Thread.sleep(initSteadyStateTimeout);
    		for(int i=0; i<numNode; i++){
    			informSteadyState(i, 0);
    		}
    	} catch (Exception e){
    		LOG.error("Error while waiting on the first steady states timeout");
    	}
    }
    
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
        if (LOG.isDebugEnabled()) {
            LOG.debug("Waiting node " + id + " to be in steady state");
        }
        
        int timeoutCounter = 0;
        int timeoutFraction = 20;
        while (!isNodeSteady(id) && timeoutCounter >= timeoutFraction) {
            Thread.sleep(steadyStateTimeout/timeoutFraction);
            timeoutCounter++;
        }
        
        if(timeoutCounter >= timeoutFraction){
            LOG.warn("Steady state for node " + id + " triggered by timeout");
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
    
    @SuppressWarnings("unchecked")
	public void resetTest() {
        LOG.debug("Test reset");
        writeQueue.clear();
        senderReceiverQueues = new ConcurrentLinkedQueue[numNode][numNode];
        testId = -1;
        numCurrentCrash = 0;
        numCurrentReboot = 0;
        initialPathCounter = 0;
        localState = new int[numNode];
        scmStates = "";
        globalState = 0;
        isInitGlobalState = false;
        if (pathRecordFile != null) {
            try {
                pathRecordFile.close();
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        if (localRecordFile != null) {
            try {
                localRecordFile.close();
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        if (protocolRecordFile != null) {
            try {
                protocolRecordFile.close();
                protocolRecordFile = null;
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        if (resultFile != null) {
            try {
                resultFile.close();
                resultFile = null;
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        if (local2File != null) {
            try {
                local2File.close();
                local2File = null;
            } catch (IOException e) {
                LOG.error("", e);
            }
        }
        for (int i = 0; i < numNode; ++i) {
            if (codeRecordFiles[i] != null) {
                try {
                    codeRecordFiles[i].close();
                    codeRecordFiles[i] = null;
                } catch (IOException e) {
                    LOG.error("", e);
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
        workloadDriver.startNode(id);
        setNodeOnline(id, true);
        setNodeSteady(id, false);
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Waiting new started node " + id + " to be in real steady state");
            }
       
            int timeoutCounter = 0;
            int timeoutFraction = 20;
            while (!isNodeSteady(id) && timeoutCounter >= timeoutFraction) {
                Thread.sleep(initSteadyStateTimeout/timeoutFraction);
                timeoutCounter++;
            }
            
            if(timeoutCounter >= timeoutFraction){
                LOG.warn("Steady state for new started node " + id + " triggered by timeout");
            }
            
            setNodeSteady(id, true);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return true;
    }
    
    protected Transition nextInitialTransition(LinkedList<Transition> queue){
		InstructionTransition instruction;
		String[] tokens = initialPath.get(initialPathCounter).split(" ");
		initialPathCounter++;
		if(initialPathCounter >= initialPath.size()){
	    	hasFinishedInitialPath = true;
		}
        if (tokens[0].equals("packetsend")) {
            String packetTransitionIdString = tokens[1].split("=")[1];
            if (packetTransitionIdString.equals("*")) {
                instruction = new PacketSendInstructionTransition(0);
            } else {
                long packetTransitionId = Long.parseLong(packetTransitionIdString);
                instruction = new PacketSendInstructionTransition(packetTransitionId);
            }
        } else if (tokens[0].equals("nodecrash")) {
            int id = Integer.parseInt(tokens[1].split("=")[1]);
            instruction = new NodeCrashInstructionTransition(id);
        } else if (tokens[0].equals("nodestart")) {
            int id = Integer.parseInt(tokens[1].split("=")[1]);
            instruction = new NodeStartInstructionTransition(id);
        } else if (tokens[0].equals("sleep")) {
            long sleep = Long.parseLong(tokens[1].split("=")[1]);
            instruction = new SleepInstructionTransition(sleep);
        } else if (tokens[0].equals("stop")) {
        	instruction = new ExitInstructionTransaction();
        } else {
        	return null;
        }
        Transition transition = instruction.getRealTransition(this);
        int id = -1;
        for(int i=0; i<queue.size(); i++){
        	// replace abstract with real one based on id
        	Transition eventInQueue = queue.get(i);
        	if((transition instanceof NodeCrashTransition && eventInQueue instanceof AbstractNodeCrashTransition) ||
        			(transition instanceof NodeStartTransition && eventInQueue instanceof AbstractNodeStartTransition)){
        		System.out.println("replace abstract with real event");
        		queue.set(i, transition);
        		eventInQueue = queue.get(i);
        	}
        	if(transition.getTransitionId() == eventInQueue.getTransitionId()){
        		id = i;
        		break;
        	}
        }
        return currentEnabledTransitions.remove(id);
    }
    
    protected boolean checkTerminationPoint(LinkedList<Transition> queue){
		if(queue.isEmpty()){
			return true;
		}
		return false;
	}
    
    abstract protected static class Explorer extends Thread {
        
        protected ModelCheckingServerAbstract checker;
        
        public Explorer(ModelCheckingServerAbstract checker) {
            this.checker = checker;
        }
        
    }
    
    protected class PacketReceiveAckImpl implements PacketReceiveAck {
        
        final Logger LOG = LoggerFactory.getLogger(PacketReceiveAckImpl.class);
        
        public void ack(int packetId, int id) throws RemoteException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acking back for packet id " + packetId + " from node id " + id);
            }
            ackedIds.add(packetId);
        }
        
    }
    
    protected class DiskWriteAckImpl implements DiskWriteAck {
        
        final Logger LOG = LoggerFactory.getLogger(DiskWriteAckImpl.class);

        public void ack(int writeId, int nodeId) throws RemoteException {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acking back for disk write id " + writeId + " from node id " + nodeId);
            }
            writeAckedIds.add(writeId);
        }
        
    }
    
 // for initialPaths
    abstract class InstructionTransition {

        abstract Transition getRealTransition(ModelCheckingServerAbstract checker);

    }
    
    class PacketSendInstructionTransition extends InstructionTransition {
        
        long packetId;
        
        public PacketSendInstructionTransition(long packetId) {
            this.packetId = packetId;
        }
        
        @Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            if (packetId == 0) {
                return (Transition) currentEnabledTransitions.peekFirst();
            }
            for (int i = 0; i < 25; ++i) {
                for (Object t : currentEnabledTransitions) {
                	if(t instanceof PacketSendTransition){
	                    PacketSendTransition p = (PacketSendTransition) t;
	                    if (p.getTransitionId() == packetId) {
	                        return p;
	                    }
                	}
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    LOG.error("", e);
                }
                getOutstandingTcpPacketTransition(currentEnabledTransitions);
            }
            throw new RuntimeException("No expected enabled packet for " + packetId);
        }
        
    }
    
    class NodeCrashInstructionTransition extends InstructionTransition {
        
        int id;

        protected NodeCrashInstructionTransition(int id) {
            this.id = id;
        }

        @Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new NodeCrashTransition(checker, id);
        }
        
    }
    
    class NodeStartInstructionTransition extends InstructionTransition {
        
        int id;

        protected NodeStartInstructionTransition(int id) {
            this.id = id;
        }

        @Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new NodeStartTransition(checker, id);
        }
        
    }
    
    class SleepInstructionTransition extends InstructionTransition {
        
        long sleep;
        
        protected SleepInstructionTransition(long sleep) {
            this.sleep = sleep;
        }

        @SuppressWarnings("serial")
		@Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new Transition() {

                @Override
                public boolean apply() {
                    try {
                        Thread.sleep(sleep);
                    } catch (InterruptedException e) {
                        return false;
                    }
                    return true;
                }

                @Override
                public int getTransitionId() {
                    return 0;
                }
                
            };
        }
    }
    
    class ExitInstructionTransaction extends InstructionTransition {

        @SuppressWarnings("serial")
		@Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new Transition() {
                
                @Override
                public int getTransitionId() {
                    return 0;
                }
                
                @Override
                public boolean apply() {
                    System.exit(0);
                    return true;
                }
            };
        }
        
    }

}
