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
            WorkloadDriver zkController) {
        super(interceptorName, ackName, numNode, globalStatePathDir, zkController);
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
                    transitions.add(new NodeCrashTransition(this, i));
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
            LinkedList<LinkedList<Transition>> pastEnabledTransitionList = 
                    new LinkedList<LinkedList<Transition>>();
            getOutstandingTcpPacketTransition(enabledTransitionList);
            while (true) {
                adjustCrashAndReboot(enabledTransitionList);
                if (enabledTransitionList.isEmpty()) {
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
                        } else {
                            break;
                        }
                    }
                    resetTest();
                    break;
                }
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
                            getOutstandingTcpPacketTransition(enabledTransitionList);
                        }
                    } catch (Exception e) {
                        log.error("", e);
                    }
                } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
                    log.warn("Finished exploring all states");
                    zkController.stopEnsemble();
                    System.exit(0);
                } else {
                    log.error("There might be some errors");
                    zkController.stopEnsemble();
                    System.exit(1);
                }
            }
        }
    }
    
}
