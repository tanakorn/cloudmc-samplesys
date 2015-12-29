package edu.uchicago.cs.ucare.samc.election;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.LocalState;

public class LeaderElectionSemanticAwareModelChecker extends DporModelChecker {

    public boolean isDependent(LocalState state, Event e1, Event e2) {
        LeaderElectionLocalState leState = (LeaderElectionLocalState) state;
        if (leState.getRole() == LeaderElectionMain.LOOKING) {
            int currSup = leState.getLeader();
            int sup1 = (int) e1.getValue(LeaderElectionPacket.LEADER_KEY);
            int sup2 = (int) e2.getValue(LeaderElectionPacket.LEADER_KEY);
            if (currSup < sup1 || currSup < sup2) {
                return true;
            }
        }
        return false;
    }

    public LeaderElectionSemanticAwareModelChecker(String interceptorName,
            String ackName, int maxId, int numCrash, int numReboot,
            String globalStatePathDir, String packetRecordDir,
            WorkloadDriver zkController, boolean useIPC) {
        this(interceptorName, ackName, maxId, numCrash, numReboot,
                globalStatePathDir, packetRecordDir, "/tmp", zkController, useIPC);
    }

    public LeaderElectionSemanticAwareModelChecker(String inceptorName,
            String ackName, int maxId, int numCrash, int numReboot,
            String globalStatePathDir, String packetRecordDir, String cacheDir,
            WorkloadDriver zkController, boolean useIPC) {
        super(inceptorName, ackName, maxId, numCrash, numReboot,
                globalStatePathDir, packetRecordDir, cacheDir, zkController, useIPC);
    }

}
