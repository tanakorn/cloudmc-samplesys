package edu.uchicago.cs.ucare.samc.election;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.transition.TransitionTuple;
import edu.uchicago.cs.ucare.samc.util.EnsembleController;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.WorkloadFeeder;

// Semantic aware of packet content and dependency between packet and crash
public class LeaderElectionSemanticAwareModelChecker extends PrototypeSamc {
    
    public LeaderElectionSemanticAwareModelChecker(String interceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        this(interceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, packetRecordDir, "/tmp", zkController, feeder);
    }
    
    public LeaderElectionSemanticAwareModelChecker(String inceptorName, String ackName, int maxId,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir, String cacheDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        super(inceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir, zkController, feeder);
    }

    @SuppressWarnings("unchecked")
    protected void calculateDPORInitialPaths() {
        TransitionTuple lastTransition;
        while ((lastTransition = currentExploringPath.pollLast()) != null) {
            boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
//            int[] oldServerState = prevRole.removeLast();
            LeaderElectionLocalState[] oldLocalStates = prevLocalStates.removeLast();
            LinkedList<TransitionTuple> tmpPath = (LinkedList<TransitionTuple>) currentExploringPath.clone();
            Iterator<TransitionTuple> reverseIter = currentExploringPath.descendingIterator();
//            prevLeader.removeLast();
//            Iterator<int[]> reverseLeaderIter = prevLeader.descendingIterator();
//            Iterator<int[]> reverseRoleIter = prevRole.descendingIterator();
            Iterator<LeaderElectionLocalState[]> reverseLocalStateIter = prevLocalStates.descendingIterator();
            Iterator<boolean[]> reverseOnlineStatusIter = prevOnlineStatus.descendingIterator();
            int index = currentExploringPath.size();
            while (reverseIter.hasNext()) {
                index--;
                TransitionTuple tuple = reverseIter.next();
                oldLocalStates = reverseLocalStateIter.next();
//                int[] oldLeader = reverseLeaderIter.next();
//                oldServerState = reverseRoleIter.next();
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
                                    LeaderElectionVote newVote = new LeaderElectionVote((LeaderElectionPacket) lastPacket);
                                    LeaderElectionVote comparingVote = new LeaderElectionVote((LeaderElectionPacket) tuplePacket.getPacket());

                                    if (oldLocalStates[toId].getRole() == LeaderElectionMain.LOOKING) {
                                        if (oldLocalStates[toId].getLeader() < newVote.leader || oldLocalStates[toId].getLeader() < comparingVote.leader) {
                                            addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                        }
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
