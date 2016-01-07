package edu.uchicago.cs.ucare.samc.scm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public class SCMVerifier extends SpecVerifier {
	
	protected static final Logger log = LoggerFactory.getLogger(SCMVerifier.class);
    
	boolean pathState;
	String pathOrder;
	
    public SCMVerifier() {
    	pathState = true;
    	pathOrder = "";
    }
    
    public SCMVerifier(ModelCheckingServerAbstract modelCheckingServer) {
    	this.modelCheckingServer = modelCheckingServer;
    }

    public boolean verify(){
    	pathOrder = modelCheckingServer.scmStates;
    	if(modelCheckingServer.scmStates.length() > 2){
    		if(modelCheckingServer.scmStates.indexOf("bac") >= 0){
    			pathState = false;
    			return false;
    		}
    	}
    	pathState = true;
    	return true;
    }
    
    public String verificationDetail(){
    	if(pathState){
        	return "Current path (" + pathOrder +") doesn't have 'bac' order in its order.";
    	} else {
        	return "Current path (" + pathOrder +") has 'bac' order in its order. In this SCM example, this is an error order.";
    	}
    }

}