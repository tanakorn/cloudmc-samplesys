package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.almworks.sqlite4java.SQLiteException;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.samc.util.SqliteExploredBranchRecorder;

public abstract class TreeTravelModelChecker extends ModelCheckingServerAbstract {
    
    protected String stateDir;
    protected ExploredBranchRecorder exploredBranchRecorder;
    protected int numCrash;
    protected int numReboot;
    protected int currentCrash;
    protected int currentReboot;
    
    public TreeTravelModelChecker(String interceptorName, String ackName, int numNode,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, 
            String workingDir, WorkloadDriver workloadDriver, String ipcDir) {
        super(interceptorName, ackName, numNode, globalStatePathDir, workingDir, workloadDriver, 
        		ipcDir);
        try {
            this.numCrash = numCrash;
            this.numReboot = numReboot;
            this.stateDir = packetRecordDir;
            exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
        } catch (SQLiteException e) {
            LOG.error("", e);
        }
        resetTest();
    }
    
    abstract public Transition nextTransition(LinkedList<Transition> transitions);
    
    @Override
    public void resetTest() {
        if (exploredBranchRecorder == null) {
            return;
        }
        super.resetTest();
        modelChecking = new PathTraversalWorker();
        currentEnabledTransitions = new LinkedList<Transition>();
        exploredBranchRecorder.resetTraversal();
        File waiting = new File(stateDir + "/.waiting");
        try {
            waiting.createNewFile();
        } catch (IOException e) {
            LOG.error("", e);
        }
        currentCrash = 0;
        currentReboot = 0;
    }
    
    protected void adjustCrashAndReboot(LinkedList<Transition> transitions) {
        if (numCurrentCrash < numCrash) {
            for (int i = 0; i < isNodeOnline.length; ++i) {
                if (isNodeOnline(i)) {
                    transitions.add(new NodeCrashTransition(this, i));
                }
            }
        } else {
            ListIterator<Transition> iter = transitions.listIterator();
            while (iter.hasNext()) {
                if (iter.next() instanceof NodeCrashTransition) {
                    iter.remove();
                }
            }
        }
        if (numCurrentReboot < numReboot) {
            for (int i = 0; i < isNodeOnline.length; ++i) {
                if (!isNodeOnline(i)) {
                    transitions.add(new NodeStartTransition(this, i));
                }
            }
        } else {
            ListIterator<Transition> iter = transitions.listIterator();
            while (iter.hasNext()) {
                if (iter.next() instanceof NodeStartTransition) {
                    iter.remove();
                }
            }
        }
    }
    
    protected void recordTestId() {
        exploredBranchRecorder.noteThisNode(".test_id", testId + "");
    }
    
    class PathTraversalWorker extends Thread {

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
        	boolean hasExploredAll = false;
        	boolean hasWaited = false;
            LinkedList<LinkedList<Transition>> pastEnabledTransitionList = 
                    new LinkedList<LinkedList<Transition>>();
            while (true) {
            	getOutstandingTcpPacketTransition(currentEnabledTransitions);
            	adjustCrashAndReboot(currentEnabledTransitions);
            	printTransitionQueues(currentEnabledTransitions);
            	boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);
                if (terminationPoint && hasWaited) {
                	boolean verifiedResult = verifier.verify();
                    String detail = verifier.verificationDetail();
                    saveResult(verifiedResult + " ; " + detail + "\n");
                    recordTestId();
                    exploredBranchRecorder.markBelowSubtreeFinished();
                    for (LinkedList<Transition> pastTransitions : pastEnabledTransitionList) {
                        exploredBranchRecorder.traverseUpward(1);
                        Transition nextTransition = nextTransition(pastTransitions);
                        if (nextTransition == null) {
                            exploredBranchRecorder.markBelowSubtreeFinished();
                        	hasExploredAll = true;
                        } else {
                        	hasExploredAll = false;
                            break;
                        }
                    }
                	System.out.println("---- End of Path Execution ----");
                	if(!hasExploredAll){
                		resetTest();
                        break;
                	}
                } else if(terminationPoint){
                	try {
                    	System.out.println("[DEBUG] wait for any long process");
                        hasWaited = true;
                        Thread.sleep(waitEndExploration);
                    } catch (InterruptedException e) {
                    	e.printStackTrace();
                    }
                    continue;
                }
                hasWaited = false;
                pastEnabledTransitionList.addFirst((LinkedList<Transition>) currentEnabledTransitions.clone());
                Transition transition;
                boolean recordPath = true;
                if(hasInitialPath && !hasFinishedInitialPath){
                	transition = nextInitialTransition(currentEnabledTransitions);
                	System.out.println("[INFO] next transition is directed by initialPath");
                	recordPath = false;
                } else {
                    transition = nextTransition(currentEnabledTransitions);
                }
                if (transition != null) {
                	if(recordPath){
	                    exploredBranchRecorder.createChild(transition.getTransitionId());
	                    exploredBranchRecorder.traverseDownTo(transition.getTransitionId());
	                    exploredBranchRecorder.noteThisNode(".packet", transition.toString());
                	}
                	// check next event execution
                	boolean cleanTransition = verifier.verifyNextTransition(transition);
                	if(!cleanTransition){
                		String detail = verifier.verificationDetail();
                        saveResult(cleanTransition + " ; " + detail + "\n");
                        recordTestId();
                        exploredBranchRecorder.markBelowSubtreeFinished();
                    	System.out.println("[NEXT TRANSITION] False next transition");
                    	System.out.println("---- End of Path Execution ----");
                        resetTest();
                        break;
                	}
                    try {
                        if (transition.apply()) {
                            pathRecordFile.write((transition.toString() + "\n").getBytes());
                            updateGlobalState();
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
                                for (ConcurrentLinkedQueue<InterceptPacket> queue : senderReceiverQueues[crash.getId()]) {
                                    queue.clear();
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOG.error("", e);
                    }
                } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
                	System.out.println("Finished exploring all states");
                    LOG.warn("Finished exploring all states");
                    workloadDriver.stopEnsemble();
                    System.exit(0);
                } else {
                	System.out.println("There might be some errors");
                    LOG.error("There might be some errors");
                    workloadDriver.stopEnsemble();
                    System.exit(1);
                }
            }
        }
    }
    
}
