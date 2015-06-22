package edu.uchicago.cs.ucare.samc.server;

import java.util.LinkedList;
import java.util.ListIterator;

import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.EnsembleController;
import edu.uchicago.cs.ucare.samc.util.WorkloadFeeder;

public class DporTreeTravelModelChecker extends TreeTravelModelChecker {

    public DporTreeTravelModelChecker(String interceptorName, String ackName,
            int numNode, int numCrash, int numReboot,
            String globalStatePathDir, String packetRecordDir, String cacheDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        super(interceptorName, ackName, numNode, numCrash, numReboot,
                globalStatePathDir, packetRecordDir, zkController, feeder);
    }

    @SuppressWarnings("unchecked")
    public Transition nextTransition(LinkedList<Transition> transitions) {
        LinkedList<Transition>[] groups = new LinkedList[numNode];
        for (int i = 0; i < numNode; ++i) {
            groups[i] = new LinkedList<Transition>();
        }
        LinkedList<NodeCrashTransition> nodeCrashes = new LinkedList<NodeCrashTransition>();
        LinkedList<NodeStartTransition> nodeReboot = new LinkedList<NodeStartTransition>();
        for (Transition t : transitions) {
            if (t instanceof PacketSendTransition) {
                PacketSendTransition p = (PacketSendTransition) t;
                groups[p.getPacket().getToId()].add(p);
            } else if (t instanceof NodeCrashTransition) {
                nodeCrashes.add((NodeCrashTransition) t);
            } else if (t instanceof NodeStartTransition) {
                nodeReboot.add((NodeStartTransition) t);
            }
        }
        for (LinkedList<Transition> group : groups) {
//            if (group.isEmpty()) {
//                continue;
//            }
            group.addAll(nodeCrashes);
        }
        for (LinkedList<Transition> group : groups) {
//            if (group.isEmpty()) {
//                continue;
//            }
            group.addAll(nodeReboot);
        }
        int i = 0;
        while (i < numNode && groups[i].isEmpty()) {
            i++;
        }
        if (i == numNode) {
            return null;
        }
        ListIterator<Transition> iter = groups[i].listIterator();
        while (iter.hasNext()) {
            Transition transition = iter.next();
            if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
                transitions.remove(transition);
                iter.remove();
                return transition;
            }
        }
        /*
        for (LinkedList<Transition> group : groups) {
            ListIterator<Transition> iter = group.listIterator();
            while (iter.hasNext()) {
                Transition transition = iter.next();
                if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
                    iter.remove();
                    return transition;
                }
            }
        }
        */
        return null;
    }
    
    /*
    public void addCrashAndReboot(LinkedList<Transition>[] groups) {
        if (currentCrash < numCrash) {
            LinkedList<NodeCrashTransition> nodeCrash = new LinkedList<NodeCrashTransition>();
            for (int i = 0; i < numNode; ++i) {
                if (isNodeOnline(i)) {
                    nodeCrash.add(new NodeCrashTransition(this, i));
                }
            }
            for (LinkedList<Transition> group : groups) {
                group.addAll(nodeCrash);
            }
        }
        if (currentReboot < numReboot) {
            LinkedList<NodeStartTransition> nodeReboot = new LinkedList<NodeStartTransition>();
            for (int i = 0; i < numNode; ++i) {
                if (!isNodeOnline(i)) {
                    nodeReboot.add(new NodeStartTransition(this, i));
                }
            }
            for (LinkedList<Transition> group : groups) {
                group.addAll(nodeReboot);
            }
        }
    }
    */
    
}
