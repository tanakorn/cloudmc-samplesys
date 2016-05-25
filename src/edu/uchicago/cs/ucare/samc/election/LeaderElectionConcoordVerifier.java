package edu.uchicago.cs.ucare.samc.election;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public class LeaderElectionConcoordVerifier extends SpecVerifier {
    
    protected static final Logger log = LoggerFactory.getLogger(LeaderElectionVerifier.class);
    
    
    public LeaderElectionConcoordVerifier() {
    	
    }
    
    public LeaderElectionConcoordVerifier(ModelCheckingServerAbstract modelCheckingServer) {
    	this.modelCheckingServer = modelCheckingServer;
    }
    
    @Override
    public boolean verify() {
        //Dummy version will always return true
        return true;
    }
    
    //Not yet used
    @Override
    public String verificationDetail() {
        /*StringBuilder strBuilder = new StringBuilder();
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
        return strBuilder.toString();*/
	return "true";
    }
    
}
