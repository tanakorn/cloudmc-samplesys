package edu.uchicago.cs.ucare.simc.election;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.simc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.simc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.simc.util.SpecVerifier;

public class LeaderElectionVerifier extends SpecVerifier {
    
    protected static final Logger log = LoggerFactory.getLogger(LeaderElectionVerifier.class);
    
    public ModelCheckingServerAbstract modelCheckingServer;
    
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
        for (int i = 0; i < 200; ++i) {
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
                Thread.sleep(50);
            } catch (InterruptedException e) {

            }
        }
        return false;
    }
    
    public int numLooking(boolean[] isNodeOnline) {
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
    
}
