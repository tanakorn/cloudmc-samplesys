package edu.uchicago.cs.ucare.simc.election;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Set;

import edu.uchicago.cs.ucare.simc.event.InterceptPacket;
import edu.uchicago.cs.ucare.simc.transition.AbstractNodeCrashTransition;
import edu.uchicago.cs.ucare.simc.transition.AbstractNodeOperationTransition;
import edu.uchicago.cs.ucare.simc.transition.AbstractNodeStartTransition;
import edu.uchicago.cs.ucare.simc.transition.DiskWriteTransition;
import edu.uchicago.cs.ucare.simc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.simc.transition.NodeOperationTransition;
import edu.uchicago.cs.ucare.simc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.simc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.simc.transition.Transition;
import edu.uchicago.cs.ucare.simc.transition.TransitionTuple;
import edu.uchicago.cs.ucare.simc.util.EnsembleController;
import edu.uchicago.cs.ucare.simc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.simc.util.WorkloadFeeder;

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

    /*
	@Override
	protected void calculateDPORInitialPaths() {
		
	}
	*/
    
    @SuppressWarnings("unchecked")
    protected void calculateDPORInitialPaths() {
        TransitionTuple lastTransition;
        while ((lastTransition = currentExploringPath.pollLast()) != null) {
            boolean[] oldOnlineStatus = prevOnlineStatus.removeLast();
//            int[] oldServerState = prevRole.removeLast();
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
                LinkedList<NodeOperationTransition> transitions = ((AbstractNodeStartTransition) lastTransition.transition).getAllRealNodeOperationTransitions(oldOnlineStatus);
                for (NodeOperationTransition t : transitions) {
                    LinkedList<TransitionTuple> interestingPath = (LinkedList<TransitionTuple>) tmpPath.clone();
                    interestingPath.add(new TransitionTuple(0, t));
                    addToDporInitialPathList(interestingPath);
                }
            }
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
                                    LeaderElectionVote newVote = new LeaderElectionVote((LeaderElectionPacket) lastPacket);
                                    LeaderElectionVote comparingVote = new LeaderElectionVote((LeaderElectionPacket) tuplePacket.getPacket());

                                    addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                    break;
                                    /*
                                    if (LeaderElectionVote.isMoreInteresting(newVote, oldNoti.get(tuplePacket.getPacket().getToId()), oldServerState[tuplePacket.getPacket().getToId()]) &&
                                            LeaderElectionVote.isMoreInteresting(newVote, comparingVote, oldServerState[tuplePacket.getPacket().getToId()])) {
                                        addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                        break;
                                    }
                                    */
                                }
                            } else if (tuplePacket.getPacket().getToId() == lastPacket.getToId() && tuplePacket.getPacket().getFromId() == lastPacket.getFromId()) {
                                break;
                            }
                        } else if (tuple.transition instanceof NodeOperationTransition || tuple.transition instanceof AbstractNodeOperationTransition) {
                            if (lastPacket.isObsolete()) {
                                if (lastPacket.getObsoleteBy() >= index) {
                                    LeaderElectionVote newNoti = new LeaderElectionVote((LeaderElectionPacket) lastPacket);
                                    /*
                                    if (!LeaderElectionVote.isMoreInteresting(newNoti, oldNoti.get(lastPacket.getToId()), oldServerState[lastPacket.getToId()])) {
                                        continue;
                                    }
                                    */
                                    addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                    break;
                                }
                            } else {
                                LeaderElectionVote newNoti = new LeaderElectionVote((LeaderElectionPacket) lastPacket);
                                /*
                                if (!LeaderElectionVote.isMoreInteresting(newNoti, oldNoti.get(lastPacket.getToId()), oldServerState[lastPacket.getToId()])) {
                                    continue;
                                }
                                */
                                addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                break;
                            }
                        }
                    } else if (lastTransition.transition instanceof AbstractNodeCrashTransition) {
                        if (tuple.transition instanceof NodeOperationTransition) {
                            if (((NodeOperationTransition) tuple.transition).id == ((AbstractNodeOperationTransition) lastTransition.transition).id) {
                                break;
                            } else if (tuple.transition instanceof AbstractNodeCrashTransition || tuple.transition instanceof NodeCrashTransition) {
                                break;
                            }
                        } else if (tuple.transition instanceof PacketSendTransition) {
                            InterceptPacket packet = ((PacketSendTransition) tuple.transition).getPacket();
                            if (packet.isObsolete()) {
                                continue;
                            } else {
                                if (index != 0) {
                                	/*
                                    LeaderElectionVote newNoti = new LeaderElectionVote((LeaderElectionPacket) packet);
                                    HashMap<Integer, LeaderElectionVote> olderNoti = previousInterestingNoti.get(index - 1);
                                    ServerState[] olderServerState = previousServerState.get(index - 1);
                                    if (!LeaderElectionVote.isMoreInteresting(newNoti, olderNoti.get(packet.getToId()), olderServerState[packet.getToId()])) {
                                        continue;
                                    }
                                    */
                                }
                            }
                        } else if (tuple.transition instanceof DiskWriteTransition) {
                            if (((DiskWriteTransition) tuple.transition).isObsolete()) {
                                continue;
                            } else if (((DiskWriteTransition) tuple.transition).getWrite().getNodeId() != ((AbstractNodeCrashTransition) lastTransition.transition).getId()) {
                                continue;
                            }
                        }
                        addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                        break;
                    } else if (lastTransition.transition instanceof AbstractNodeStartTransition) {
                        if (tuple.transition instanceof NodeOperationTransition) {
                            if (((NodeOperationTransition) tuple.transition).id == ((AbstractNodeOperationTransition) lastTransition.transition).id) {
                                break;
                            } else if (tuple.transition instanceof AbstractNodeStartTransition || tuple.transition instanceof NodeStartTransition) {
                                break;
                            }
                        } else if (tuple.transition instanceof PacketSendTransition) {
                            if (((PacketSendTransition) tuple.transition).getPacket().isObsolete()) {
                                continue;
                            }
                        } else if (tuple.transition instanceof NodeStartTransition || tuple.transition instanceof AbstractNodeStartTransition) {
                            break;
                        }
                        addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                        break;
                    } else if (lastTransition.transition instanceof DiskWriteTransition) {
                        DiskWriteTransition write = (DiskWriteTransition) lastTransition.transition;
                        if (tuple.transition instanceof PacketSendTransition) {
                            continue;
                        } else if (tuple.transition instanceof NodeOperationTransition) {
                            if ((!write.isObsolete() && write.getWrite().getNodeId() == ((NodeOperationTransition) tuple.transition).getId()) || 
                                    (write.isObsolete() && index == write.getObsoleteBy())) {
                                addNewDporInitialPath(tmpPath, tuple, new TransitionTuple(0, lastTransition.transition));
                                break;
                            } else {
                                continue;
                            }
                        } else if (tuple.transition instanceof DiskWriteTransition) {
                            continue;
                        }
                    }
                } else {
                    break;
                }
            }
        }
    }
        
}
