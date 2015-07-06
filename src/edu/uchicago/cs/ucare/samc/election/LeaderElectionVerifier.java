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
                    } else if (localState.getRole() == LeaderElectionMain.FOLLOWING) {
                        numFollower++;
                    } else if (localState.getRole() == LeaderElectionMain.LOOKING) {
                       numLooking++; 
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
    
    private int numLooking(boolean[] isNodeOnline) {
    	int numLooking = 0;
    	for (int i = 0; i < isNodeOnline.length; ++i) {
    		if (isNodeOnline[i]) {
    			LeaderElectionLocalState localState = modelCheckingServer.localStates[i];
                if (localState.getRole() == LeaderElectionMain.LOOKING) {
                   numLooking++; 
                }
    		}
    	}
    	return numLooking;
    }
    
    public int[] numRole(boolean[] isNodeOnline) {
        int numLeader = 0;
        int numFollower = 0;
        int numLooking = 0;
        for (int j = 0; j < modelCheckingServer.isNodeOnline.length; ++j) {
            if (modelCheckingServer.isNodeOnline[j]) {
                LeaderElectionLocalState localState = modelCheckingServer.localStates[j];
                if (localState.getRole() == LeaderElectionMain.LEADING) {
                    numLeader++;
                } else if (localState.getRole() == LeaderElectionMain.FOLLOWING) {
                    numFollower++;
                } else if (localState.getRole() == LeaderElectionMain.LOOKING) {
                   numLooking++; 
                }
            }
        }
        return new int[] { numLeader, numFollower, numLooking };
    }

    @Override
    public String verificationDetail() {
        return null;
    }
    
}
