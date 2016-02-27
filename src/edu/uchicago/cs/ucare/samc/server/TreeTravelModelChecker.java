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
    protected LinkedList<InterceptPacket> enabledPacketList;
    protected LinkedList<Transition> enabledTransitionList;
    protected int numCrash;
    protected int numReboot;
    protected int currentCrash;
    protected int currentReboot;
    
    public TreeTravelModelChecker(String interceptorName, String ackName, int numNode,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, 
            String workingDir, WorkloadDriver zkController, String ipcDir) {
        super(interceptorName, ackName, numNode, globalStatePathDir, workingDir, zkController, 
        		ipcDir);
        try {
            this.numCrash = numCrash;
            this.numReboot = numReboot;
            this.stateDir = packetRecordDir;
            exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
        } catch (SQLiteException e) {
            log.error("", e);
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
        modelChecking = new PacketExplorer();
        enabledPacketList = new LinkedList<InterceptPacket>();
        enabledTransitionList = new LinkedList<Transition>();
        exploredBranchRecorder.resetTraversal();
        File waiting = new File(stateDir + "/.waiting");
        try {
            waiting.createNewFile();
        } catch (IOException e) {
            log.error("", e);
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
    
    class PacketExplorer extends Thread {

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
        	boolean hasExploredAll = false;
        	boolean hasWaited = false;
            LinkedList<LinkedList<Transition>> pastEnabledTransitionList = 
                    new LinkedList<LinkedList<Transition>>();
            while (true) {
            	getOutstandingTcpPacketTransition(enabledTransitionList);
            	adjustCrashAndReboot(enabledTransitionList);
                if (enabledTransitionList.isEmpty() && hasWaited) {
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
                } else if(enabledTransitionList.isEmpty()){
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
                pastEnabledTransitionList.addFirst((LinkedList<Transition>) enabledTransitionList.clone());
                Transition nextTransition = nextTransition(enabledTransitionList);
                if (nextTransition != null) {
                    exploredBranchRecorder.createChild(nextTransition.getTransitionId());
                    exploredBranchRecorder.traverseDownTo(nextTransition.getTransitionId());
                    exploredBranchRecorder.noteThisNode(".packet", nextTransition.toString());
                    try {
                        if (nextTransition.apply()) {
                            pathRecordFile.write((nextTransition.toString() + "\n").getBytes());
                            updateGlobalState();
                            if (nextTransition instanceof NodeCrashTransition) {
                                NodeCrashTransition crash = (NodeCrashTransition) nextTransition;
                                ListIterator<Transition> iter = enabledTransitionList.listIterator();
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
                        log.error("", e);
                    }
                } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
                	System.out.println("Finished exploring all states");
                    log.warn("Finished exploring all states");
                    zkController.stopEnsemble();
                    System.exit(0);
                } else {
                	System.out.println("There might be some errors");
                    log.error("There might be some errors");
                    zkController.stopEnsemble();
                    System.exit(1);
                }
            }
        }
    }
    
}
