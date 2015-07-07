package edu.uchicago.cs.ucare.samc.election;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public class LeaderElectionVerifier extends SpecVerifier {
    
    protected static final Logger log = LoggerFactory.getLogger(LeaderElectionVerifier.class);
    
    
    public LeaderElectionVerifier() {
    	
    }
    
    public LeaderElectionVerifier(ModelCheckingServerAbstract modelCheckingServer) {
    	this.modelCheckingServer = modelCheckingServer;
    }
    
    @Override
    public boolean verify() {
    	int onlineNode = 0;
    	int[] supportTable = new int[modelCheckingServer.isNodeOnline.length];
        for (boolean status : modelCheckingServer.isNodeOnline) {
            if (status) {
                onlineNode++;
            }
        }
        for (int i = 0; i < 3; ++i) {
            int numLeader = 0;
            int numFollower = 0;
            int numLooking = 0;
            for (int j = 0; j < modelCheckingServer.isNodeOnline.length; ++j) {
                if (modelCheckingServer.isNodeOnline[j]) {
                    LeaderElectionLocalState localState = modelCheckingServer.localStates[j];
                    if (localState.getRole() == LeaderElectionMain.LEADING) {
                        numLeader++;
                        supportTable[j] = j;
                    } else if (localState.getRole() == LeaderElectionMain.FOLLOWING) {
                        numFollower++;
                        supportTable[j] = localState.getLeader();
                    } else if (localState.getRole() == LeaderElectionMain.LOOKING) {
                        numLooking++; 
                        supportTable[j] = -1;
                    }
                }
            }
            int quorum = modelCheckingServer.numNode / 2 + 1;
            if (onlineNode < quorum) {
                if (numLeader == 0 && numFollower == 0 && numLooking == onlineNode) {
                    return true;
                }
            } else {
                if (numLeader == 1 && numFollower == (onlineNode - 1)) {
                    int leader = -1;
                    for (int j = 0; j < modelCheckingServer.isNodeOnline.length; ++j) {
                        if (modelCheckingServer.isNodeOnline[j]) {
                            if (leader == -1) {
                                leader = supportTable[j];
                            } else if (supportTable[j] != leader) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {

            }
        }
        return false;
    }
    
    @Override
    public String verificationDetail() {
        StringBuilder strBuilder = new StringBuilder();
        for (int i = 0; i < modelCheckingServer.isNodeOnline.length; ++i) {
            if (modelCheckingServer.isNodeOnline[i]) {
                LeaderElectionLocalState localState = modelCheckingServer.localStates[i];
                if (localState.getRole() == LeaderElectionMain.LEADING) {
                    strBuilder.append("node " + i + " is LEADING ; ");
                } else if (localState.getRole() == LeaderElectionMain.FOLLOWING) {
                    strBuilder.append("node " + i + " is FOLLOWING to " + localState.getLeader() + " ; ");
                } else if (localState.getRole() == LeaderElectionMain.LOOKING) {
                    strBuilder.append("node " + i + " is still LOOKING ; ");
                }
            } else {
                strBuilder.append("node " + i + " is down ; ");
            }
        }
        return strBuilder.toString();
    }
    
}
