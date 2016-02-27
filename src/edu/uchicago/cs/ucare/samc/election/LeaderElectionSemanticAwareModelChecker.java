package edu.uchicago.cs.ucare.samc.election;

import java.util.HashMap;
import java.util.Map;

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
            } else if (isFinished(leState)) {
                return true;
            }
        }
        return false;
    }

    public LeaderElectionSemanticAwareModelChecker(String interceptorName,
            String ackName, int maxId, int numCrash, int numReboot,
            String globalStatePathDir, String packetRecordDir,
            WorkloadDriver zkController, String ipcDir) {
        this(interceptorName, ackName, maxId, numCrash, numReboot,
                globalStatePathDir, packetRecordDir, "/tmp", zkController, ipcDir);
    }

    public LeaderElectionSemanticAwareModelChecker(String inceptorName,
            String ackName, int maxId, int numCrash, int numReboot,
            String globalStatePathDir, String packetRecordDir, String workingDir,
            WorkloadDriver zkController, String ipcDir) {
        super(inceptorName, ackName, maxId, numCrash, numReboot,
                globalStatePathDir, packetRecordDir, workingDir, zkController, ipcDir);                
    }

    public boolean isFinished(LeaderElectionLocalState state) {
        int totalNode = this.numNode;
        Map<Integer, Integer> count = new HashMap<Integer, Integer>();
        Map<Integer, Integer> electionTable = state.getElectionTable();
        for (Integer electedLeader : electionTable.values()){
            count.put(electedLeader, count.containsKey(electedLeader) ? count.get(electedLeader) + 1 : 1);
        }
        for (Integer electedLeader : count.keySet()) {
            int totalElect = count.get(electedLeader);
            if (totalElect > totalNode / 2) {
                return true;
            }
        }
        return false;
    }
}
