package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import com.almworks.sqlite4java.SQLiteException;

import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.ExploredBranchRecorder;
import edu.uchicago.cs.ucare.samc.util.SqliteExploredBranchRecorder;

public class RandomModelChecker extends ModelCheckingServerAbstract {
    
    ExploredBranchRecorder exploredBranchRecorder;
    int numCrash;
    int numReboot;
    int currentCrash;
    int currentReboot;
    String stateDir;
    Random random;
    
    public RandomModelChecker(String inceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, String workingDir,
            WorkloadDriver workloadDriver, String ipcDir) {
        super(inceptorName, ackName, maxId, globalStatePathDir, workingDir, workloadDriver, ipcDir);
        this.numCrash = numCrash;
        this.numReboot = numReboot;
        stateDir = packetRecordDir;
        try {
            exploredBranchRecorder = new SqliteExploredBranchRecorder(packetRecordDir);
        } catch (SQLiteException e) {
            LOG.error("", e);
        }
        random = new Random(System.currentTimeMillis());
        resetTest();
    }

    @Override
    public void resetTest() {
        if (exploredBranchRecorder == null) {
            return;
        }
        super.resetTest();
        currentCrash = 0;
        currentReboot = 0;
        modelChecking = new PathTraversalWorker();
        currentEnabledTransitions = new LinkedList<Transition>();
        exploredBranchRecorder.resetTraversal();
        File waiting = new File(stateDir + "/.waiting");
        try {
            waiting.createNewFile();
        } catch (IOException e) {
            LOG.error("", e);
        }
    }
    
    public Transition nextTransition(LinkedList<Transition> transitions) {
        int i = random.nextInt(transitions.size());
        return transitions.remove(i);
    }
    
    protected void recordTestId() {
        exploredBranchRecorder.noteThisNode(".test_id", testId + "");
    }
    
    protected void adjustCrashAndReboot(LinkedList<Transition> enabledTransitions) {
        int numOnline = 0;
        for (int i = 0; i < numNode; ++i) {
            if (isNodeOnline(i)) {
                numOnline++;
            }
        }
        int numOffline = numNode - numOnline;
        int tmp = numOnline < numCrash - currentCrash ? numOnline : numCrash - currentCrash;
        for (int i = 0; i < tmp; ++i) {
            enabledTransitions.add(new AbstractNodeCrashTransition(this));
            currentCrash++;
            numOffline++;
        }
        tmp = numOffline < numReboot - currentReboot ? numOffline : numReboot - currentReboot;
        for (int i = 0; i < tmp; ++i) {
            enabledTransitions.add(new AbstractNodeStartTransition(this));
            currentReboot++;
        }
    }
    
    protected void removeCrashedSenderPackets(int crashedSender, LinkedList<Transition> enabledTransitions) {
        ListIterator<Transition> iter = enabledTransitions.listIterator();
        while (iter.hasNext()) {
            Transition t = iter.next();
            if (t instanceof PacketSendTransition) {
                if (((PacketSendTransition) t).getPacket().getFromId() == crashedSender) {
                    iter.remove();
                }
            }
        }
    }
    
    class PathTraversalWorker extends Thread {
        
        @Override
        public void run() {
            boolean hasWaited = false;
            while (true) {
                updateSAMCQueue();
                boolean terminationPoint = checkTerminationPoint(currentEnabledTransitions);
                if (terminationPoint && hasWaited) {
                    boolean verifiedResult = verifier.verify();
                    String detail = verifier.verificationDetail();
                    saveResult(verifiedResult + " ; " + detail + "\n");
                    recordTestId();
                    exploredBranchRecorder.markBelowSubtreeFinished();
                	System.out.println("---- End of Path Execution ----");
                    resetTest();
                    break;
                } else if (terminationPoint) {
                    try {
                        hasWaited = true;
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    	e.printStackTrace();
                    }
                    continue;
                }
                hasWaited = false;
                Transition transition;
                // take next path based on initial path or current policy
                boolean recordPath = true;
                if(hasInitialPath && !hasFinishedInitialPath){
                	System.out.println("[INFO] directed by initial path");
                	transition = nextInitialTransition(currentEnabledTransitions);
                	recordPath = false;
                } else {
                	transition = nextTransition(currentEnabledTransitions);
                }
                if (transition != null) {
                	if(recordPath){
	                    exploredBranchRecorder.createChild(transition.getTransitionId());
	                    exploredBranchRecorder.traverseDownTo(transition.getTransitionId());
	                    exploredBranchRecorder.noteThisNode(".packets", transition.toString(), false);
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
                        saveLocalState();
                        if (transition instanceof AbstractNodeOperationTransition) {
                            AbstractNodeOperationTransition nodeOperationTransition = (AbstractNodeOperationTransition) transition;
                            transition = ((AbstractNodeOperationTransition) transition).getRealNodeOperationTransition();
                            if (transition == null) {
                                currentEnabledTransitions.add(nodeOperationTransition);
                                continue;
                            }
                            nodeOperationTransition.id = ((NodeOperationTransition) transition).getId();
                        }
                        if (transition.apply()) {
                            pathRecordFile.write((transition.toString() + "\n").getBytes());
                            updateGlobalState();
                            updateSAMCQueueAfterEventExecution(transition);
                        }
                    } catch (IOException e) {
                        LOG.error("", e);
                    }
                } else if (exploredBranchRecorder.getCurrentDepth() == 0) {
                    LOG.warn("Finished exploring all states");
                } else {
                    try {
                        pathRecordFile.write("duplicated\n".getBytes());
                    } catch (IOException e) {
                        LOG.error("", e);
                    }
                    resetTest();
                    break;
                }
            }
        }

    }

    @SuppressWarnings("serial")
	static abstract class AbstractNodeOperationTransition extends NodeOperationTransition {
        
        static final Random RANDOM = new Random(System.currentTimeMillis());
        
        protected ModelCheckingServerAbstract checker;
        
        public AbstractNodeOperationTransition(ModelCheckingServerAbstract checker) {
            id = -1;
            this.checker = checker;
        }

        public abstract NodeOperationTransition getRealNodeOperationTransition();
        public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus);
        
    }

    @SuppressWarnings("serial")
	static class AbstractNodeCrashTransition extends AbstractNodeOperationTransition {
        
        public AbstractNodeCrashTransition(ModelCheckingServerAbstract checker) {
            super(checker);
        }

        @Override
        public boolean apply() {
            NodeCrashTransition t = getRealNodeOperationTransition();
            if (t == null) {
                return false;
            }
            id = t.getId();
            return t.apply();
        }

        @Override
        public int getTransitionId() {
            return 101;
        }
        
        @Override
        public boolean equals(Object o) {
            return o instanceof AbstractNodeCrashTransition;
        }
        
        @Override 
        public int hashCode() {
            return 101;
        }
        
        public NodeCrashTransition getRealNodeOperationTransition() {
            LinkedList<NodeOperationTransition> allPossible = getAllRealNodeOperationTransitions(checker.isNodeOnline);
            int i = RANDOM.nextInt(allPossible.size());
            return (NodeCrashTransition) allPossible.get(i);
        }
        
        @Override
        public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
            LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
            for (int i = 0; i < onlineStatus.length; ++i) {
                if (onlineStatus[i]) {
                    result.add(new NodeCrashTransition(checker, i));
                }
            }
            return result;
        }

        public String toString() {
            return "abstract_node_crash";
        }
        
    }
    
    @SuppressWarnings("serial")
	static class AbstractNodeStartTransition extends AbstractNodeOperationTransition {
        
        public AbstractNodeStartTransition(ModelCheckingServerAbstract checker) {
            super(checker);
        }

        @Override
        public boolean apply() {
            NodeOperationTransition t = getRealNodeOperationTransition();
            if (t == null) {
                return false;
            }
            id = t.getId();
            return t.apply();
        }

        @Override
        public int getTransitionId() {
            return 112;
        }
        
        @Override
        public boolean equals(Object o) {
            return o instanceof AbstractNodeStartTransition;
        }
        
        @Override 
        public int hashCode() {
            return 112;
        }
        
        @Override
        public NodeStartTransition getRealNodeOperationTransition() {
            LinkedList<NodeOperationTransition> allPossible = getAllRealNodeOperationTransitions(checker.isNodeOnline);
            if (allPossible.isEmpty()) {
                return null;
            }
            int i = RANDOM.nextInt(allPossible.size());
            return (NodeStartTransition) allPossible.get(i);
        }
        
        @Override
        public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
            LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
            for (int i = 0; i < onlineStatus.length; ++i) {
                if (!onlineStatus[i]) {
                    result.add(new NodeStartTransition(checker, i));
                }
            }
            return result;
        }

        public String toString() {
            return "abstract_node_start";
        }
        
    }
    
}
