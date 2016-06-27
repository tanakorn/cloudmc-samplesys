package edu.uchicago.cs.ucare.samc.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.DiskWriteAck;
import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.DiskWriteTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.LocalState;
import edu.uchicago.cs.ucare.samc.util.PacketReceiveAck;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public abstract class ModelCheckingServerAbstract implements ModelCheckingServer {
    
	private static String CODE_DIR = "code";
    private static String PATH_FILE = "path";
    private static String LOCAL_FILE = "local";
    private static String PROTOCOL_FILE = "protocol";
    private static String RESULT_FILE = "result";
	
    protected final Logger LOG;
    protected String interceptorName;
    protected LinkedBlockingQueue<Event> packetQueue;
    protected LinkedBlockingQueue<DiskWrite> writeQueue;
    protected HashMap<DiskWrite, Boolean> writeFinished;
    
    protected PacketReceiveAck ack;
    protected DiskWriteAck writeAck;
    protected LinkedBlockingQueue<Integer> ackedIds;
    protected LinkedBlockingQueue<Integer> writeAckedIds;
    
    public int numNode;
    public int numCurrentCrash;
    public int numCurrentReboot;
    protected int[] localState;
    public boolean[] isNodeOnline;

    protected ConcurrentLinkedQueue<Event>[][] messagesQueues;
    protected LinkedList<Event> localEventQueue;

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
    
    public LocalState[] localStates;
    
    protected String ipcDir;

	@SuppressWarnings("unchecked")
	public ModelCheckingServerAbstract(String interceptorName, String ackName, int numNode,
            String testRecordDirPath, String workingDirPath, WorkloadDriver workloadDriver, 
            String ipcDir) {
        this.interceptorName = interceptorName;
        LOG = LoggerFactory.getLogger(this.getClass() + "." + interceptorName);
        packetQueue = new LinkedBlockingQueue<Event>();
        writeQueue = new LinkedBlockingQueue<DiskWrite>();
        writeFinished = new HashMap<DiskWrite, Boolean>();
        ack = new PacketReceiveAckImpl();
        writeAck = new DiskWriteAckImpl();
        ackedIds = new LinkedBlockingQueue<Integer>();
        writeAckedIds = new LinkedBlockingQueue<Integer>();
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
        messagesQueues = new ConcurrentLinkedQueue[numNode][numNode];
        localEventQueue = new LinkedList<Event>();
        localStates = new LocalState[numNode];
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
	    	initialPathReader.close();
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
    	localStates[nodeId] = localState;
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
    
    public void offerPacket(Event event){
    	messagesQueues[(int)event.getValue("sendNode")][(int)event.getValue("recvNode")].add(event);
    	LOG.info("Intercept event " + event.toString() +" to messagesQueue");
    }
    
    public void offerLocalEvent(Event event){
    	localEventQueue.add(event);
    }
    
    abstract protected void adjustCrashAndReboot(LinkedList<Transition> transitions);
    
    public void updateSAMCQueue(){
    	getOutstandingTcpPacketTransition(currentEnabledTransitions);
    	adjustCrashAndReboot(currentEnabledTransitions);
    	printTransitionQueues(currentEnabledTransitions);
    }
    
    public void updateSAMCQueueAfterEventExecution(Transition transition){
    	if (transition instanceof NodeCrashTransition) {
            NodeCrashTransition crash = (NodeCrashTransition) transition;
            ListIterator<Transition> iter = currentEnabledTransitions.listIterator();
            while (iter.hasNext()) {
                Transition t = iter.next();
                if (t instanceof PacketSendTransition) {
                    PacketSendTransition p = (PacketSendTransition) t;
                    if (p.getPacket().getFromId() == crash.getId()) {
                        iter.remove();
                    }
                }
            }
            for (ConcurrentLinkedQueue<Event> queue : messagesQueues[crash.getId()]) {
                queue.clear();
            }
        }
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
            	if (filter[i][j] && !messagesQueues[i][j].isEmpty()) {
            		buffer.add(new PacketSendTransition(this, messagesQueues[i][j].remove()));
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
    
    public void getOutstandingTcpPacket(LinkedList<Event> packetList) {
        boolean[][] filter = new boolean[numNode][numNode];
        for (int i = 0; i < numNode; ++i) {
            Arrays.fill(filter[i], true);
        }
        for (Event p : packetList) {
            filter[p.getFromId()][p.getToId()] = false;
        }
        LinkedList<Event> buffer = new LinkedList<Event>();
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
            	// reorder
            	if (filter[i][j] && !messagesQueues[i][j].isEmpty()) {
                  buffer.add(messagesQueues[i][j].remove());
                }
            }
        }
        Collections.sort(buffer, new Comparator<Event>() {
        	@Override
        	public int compare(Event o1, Event o2) {
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
    	try {
	    	System.out.println("-----------------------------");
	        localRecordFile.write(("-----------------------------\n").getBytes());
	        
	        System.out.println("[DEBUG] Events in Queue : " + transitionList.size());
	        localRecordFile.write(("Events in Queue : " + transitionList.size() + "\n").getBytes());
	        
	        int counter = 1;
	        for (Transition t : transitionList) {
	        	if(t != null){
	        		System.out.println(counter + ". " + t.toString());
	    	        localRecordFile.write((counter + ". " + t.toString() + "\n").getBytes());
	        	} else {
	        		System.out.println(counter + ". null event");
	    	        localRecordFile.write((counter + ". null event" + "\n").getBytes());
	        	}
	        	counter++;
	        }
	        System.out.println("-----------------------------");
	        localRecordFile.write(("-----------------------------\n").getBytes());
    	} catch (IOException e) {
            LOG.error("", e);
        }
    }
    
    public void printPacketQueues(LinkedList<Event> packetList){
    	System.out.println("-----------------------------");
        System.out.println("[DEBUG] Packets in Queue : " + packetList.size());
        int counter = 1;
        for (Event p : packetList) {
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
//                if (!senderReceiverQueues[i][j].isEmpty()) {
            	if (!messagesQueues[i][j].isEmpty()) {
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
        
        int fromHash = Arrays.hashCode(messagesQueues[nodeId]);
        int toHash = 1;
        for (ConcurrentLinkedQueue<Event>[] toQueue : messagesQueues) {
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

    public void waitForAck(Event packet) throws InterruptedException {
        if (isNodeOnline(packet.getToId())) {
        	waitForAck(packet.getId());
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
    
    public boolean killNode(int id) {
        workloadDriver.stopNode(id);
        setNodeOnline(id, false);
        for (int i = 0; i < numNode; ++i) {
            messagesQueues[i][id].clear();
            messagesQueues[id][i].clear();
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
            	messagesQueues[i][j].clear();
                messagesQueues[j][i].clear();
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
    
    public boolean commit(Event packet) {
    	boolean result;
    	try {
			try{
				PrintWriter writer = new PrintWriter(ipcDir + "/new/" + packet.getValue(Event.FILENAME), "UTF-8");
	        	writer.println("eventId=" + packet.getId());
		        writer.close();
		        
		    	System.out.println("Enable event with ID : " + packet.getId());
		        
		        Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + packet.getValue(Event.FILENAME) + " " + 
		        		ipcDir + "/ack/" + packet.getValue(Event.FILENAME));
        	} catch (Exception e) {
        		System.out.println("[DEBUG] error in creating new file : " + packet.getValue(Event.FILENAME));
        	}
        	
			result = true;
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
                num += messagesQueues[i][j].size();
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
    
    public boolean commitAndWait(Event packet) throws InterruptedException {
        setNodeSteady(packet.getToId(), false);
        boolean result = false;
        if (commit(packet)) {
            waitForAck(packet.getId());
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
        messagesQueues = new ConcurrentLinkedQueue[numNode][numNode];
        testId = -1;
        numCurrentCrash = 0;
        numCurrentReboot = 0;
        initialPathCounter = 0;
        this.hasFinishedInitialPath = !hasInitialPath;
        localState = new int[numNode];
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
                messagesQueues[i][j] = new ConcurrentLinkedQueue<Event>();
            }
        }
        isNodeSteady = new boolean[numNode];
        isStarted = false;
        numPacketSentToId = new int[numNode];
        for (int i = 0; i < localStates.length; ++i) {
        	localStates[i] = new LocalState(i);
        	localStates[i].addKeyValue("role", LeaderElectionMain.LOOKING);
        	localStates[i].addKeyValue("leader", i);
        }
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
        Transition transition = instruction.getRealTransition(this, currentEnabledTransitions);
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
    
}
