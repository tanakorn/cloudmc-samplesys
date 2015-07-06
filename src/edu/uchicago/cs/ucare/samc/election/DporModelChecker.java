package edu.uchicago.cs.ucare.samc.election;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.transition.TransitionTuple;
import edu.uchicago.cs.ucare.samc.util.EnsembleController;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.LocalState;
import edu.uchicago.cs.ucare.samc.util.WorkloadFeeder;

public abstract class DporModelChecker extends PrototypeSamc {

    public DporModelChecker(String interceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        this(interceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, packetRecordDir, "/tmp", zkController, feeder);
    }
    
    public DporModelChecker(String inceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, String cacheDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        super(inceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir, zkController, feeder);
    }
    
    public abstract boolean isDependent(LocalState state, Event e1, Event e2);

    @Override
    protected void calculateDPORInitialPaths() {
        TransitionTuple lastTransition;
        while ((lastTransition = currentExploringPath.pollLast()) != null) {
            boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
            LeaderElectionLocalState[] oldLocalStates = prevLocalStates.removeLast();
            LinkedList<TransitionTuple> tmpPath = (LinkedList<TransitionTuple>) currentExploringPath.clone();
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
                            } else if (tuplePacket.getPacket().getToId() != lastPacket.getToId() || tuplePacket.getPacket().getFromId() != lastPacket.getFromId()) {
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
                        }
                    }
                } else {
                    break;
                }
            }
        }
    }

}
