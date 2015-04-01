package edu.uchicago.cs.ucare.simc.server;

import java.util.LinkedList;
import java.util.ListIterator;

import mc.EnsembleController;
import mc.WorkloadFeeder;
import mc.transition.Transition;

public class DfsTreeTravelModelChecker extends TreeTravelModelChecker {

    public DfsTreeTravelModelChecker(String interceptorName, String ackName, int numNode,
            int numCrash, int numReboot, String globalStatePathDir, String packetRecordDir,
            EnsembleController zkController, WorkloadFeeder feeder) {
        super(interceptorName, ackName, numNode, numCrash, numReboot, globalStatePathDir, 
                packetRecordDir, zkController, feeder);
    }
    
    @Override
    public Transition nextTransition(LinkedList<Transition> transitions) {
        ListIterator<Transition> iter = transitions.listIterator();
        while (iter.hasNext()) {
            Transition transition = iter.next();
            if (!exploredBranchRecorder.isSubtreeBelowChildFinished(transition.getTransitionId())) {
                iter.remove();
                return transition;
            }
        }
        return null;
    }
    
}
