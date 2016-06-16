package edu.uchicago.cs.ucare.samc.scm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public class SCMVerifier extends SpecVerifier {
	
	protected static final Logger LOG = LoggerFactory.getLogger(SCMVerifier.class);
    
	boolean error;
	SCMState receiverVote;
	
    public SCMVerifier() {
    	error = false;
    	receiverVote = new SCMState();
    }
    
    public SCMVerifier(ModelCheckingServerAbstract modelCheckingServer) {
    	this.modelCheckingServer = modelCheckingServer;
    }

    @Override
    public boolean verify(){
    	receiverVote = modelCheckingServer.scmStates[0];
    	if(receiverVote.getVote() != 4){
    		error = true;
    		return false;
    	} else {
    		return true;
    	}
    }
    
    @Override
	public boolean verifyNextTransition(Transition transition) {
    	// none
		return true;
	}

    @Override
    public String verificationDetail(){
    	if(error){
        	return "Receiver vote is not 4, but " + receiverVote;
    	} else {
        	return "Receiver is in correct state. The vote is " + receiverVote;
    	}
    }

}