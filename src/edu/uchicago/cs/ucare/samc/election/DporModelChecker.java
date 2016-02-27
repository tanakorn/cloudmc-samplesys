package edu.uchicago.cs.ucare.samc.election;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.transition.TransitionTuple;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.LocalState;

public abstract class DporModelChecker extends PrototypeSamc {

    public DporModelChecker(String interceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
            String workingDir, WorkloadDriver zkController) {
        this(interceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, 
                packetRecordDir, "/tmp", workingDir, zkController);
    }
    
    public DporModelChecker(String inceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, 
            String cacheDir, String workingDir, WorkloadDriver zkController) {
        super(inceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, 
                packetRecordDir, cacheDir, workingDir, zkController);
    }
    
    public abstract boolean isDependent(LocalState state, Event e1, Event e2);

    @Override
    protected void calculateDPORInitialPaths() {
        TransitionTuple lastTransition;
        while ((lastTransition = currentExploringPath.pollLast()) != null) {
            boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
            LeaderElectionLocalState[] oldLocalStates = prevLocalStates.removeLast();
            LinkedList<TransitionTuple> tmpPath = (LinkedList<TransitionTuple>) currentExploringPath.clone();
            if (lastTransition.transition instanceof AbstractNodeCrashTransition) {
                AbstractNodeCrashTransition abstractNodeCrashTransition = (AbstractNodeCrashTransition) lastTransition.transition;
                LinkedList<NodeOperationTransition> transitions = abstractNodeCrashTransition.getAllRealNodeOperationTransitions(oldOnlineStatus);
                for (NodeOperationTransition t : transitions) {
                    if (abstractNodeCrashTransition.id != t.id) {
                        LinkedList<TransitionTuple> interestingPath = (LinkedList<TransitionTuple>) tmpPath.clone();
                        interestingPath.add(new TransitionTuple(0, t));
                        addToDporInitialPathList(interestingPath);
                    }
                }
            } else if (lastTransition.transition instanceof AbstractNodeStartTransition) {
                LinkedList<NodeOperationTransition> transitions = 
                        ((AbstractNodeStartTransition) lastTransition.transition).getAllRealNodeOperationTransitions(oldOnlineStatus);
                for (NodeOperationTransition t : transitions) {
                    LinkedList<TransitionTuple> interestingPath = (LinkedList<TransitionTuple>) tmpPath.clone();
                    interestingPath.add(new TransitionTuple(0, t));
                    addToDporInitialPathList(interestingPath);
                }
            }
            Iterator<TransitionTuple> reverseIter = currentExploringPath.descendingIterator();
            Iterator<LeaderElectionLocalState[]> reverseLocalStateIter = prevLocalStates.descendingIterator();
            Iterator<boolean[]> reverseOnlineStatusIter = prevOnlineStatus.descendingIterator();
            int index = currentExploringPath.size();
            while (reverseIter.hasNext()) {
                index--;
                TransitionTuple tuple = reverseIter.next();
                oldLocalStates = reverseLocalStateIter.next();
                oldOnlineStatus = reverseOnlineStatusIter.next();
                Set<Transition> enabledPackets = enabledPacketTable.get(tuple.state);
                if (enabledPackets.contains(lastTransition.transition)) {
                    tmpPath.pollLast();
                    if (lastTransition.transition instanceof PacketSendTransition) {
                        InterceptPacket lastPacket = ((PacketSendTransition) lastTransition.transition).getPacket();
                        if (tuple.transition instanceof PacketSendTransition) {
                            PacketSendTransition tuplePacket = (PacketSendTransition) tuple.transition;
                            if (lastPacket.isObsolete() || tuplePacket.getPacket().isObsolete()) {
                                continue;
                            } else if (!oldOnlineStatus[lastPacket.getFromId()]) {
                                break;
                            } else if (tuplePacket.getPacket().getToId() != lastPacket.getToId() 
                                    || tuplePacket.getPacket().getFromId() != lastPacket.getFromId()) {
                                if (tuplePacket.getPacket().getToId() == lastPacket.getToId()) {
                                    int toId = tuplePacket.getPacket().getToId();
                                    if (isDependent(oldLocalStates[toId], lastPacket, tuplePacket.getPacket())) {
                                        addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                    }
                                    break;
                                }
                            } else if (tuplePacket.getPacket().getToId() == lastPacket.getToId() && tuplePacket.getPacket().getFromId() == lastPacket.getFromId()) {
                                break;
                            }
                        } else {
                            if (lastPacket.isObsolete()) {
                                if (tuple.transition instanceof NodeCrashTransition) {
                                    NodeCrashTransition crashTransition = (NodeCrashTransition) tuple.transition;
                                    if (lastPacket.getObsoleteBy() == crashTransition.getId()) {
                                        lastPacket.setObsolete(false);
                                        addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                        break;
                                    }
                                }
                            } else {
                                addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                break;
                            }
                        }
                    }
                } else {
                    break;
                }
            }
        }
    }

}
