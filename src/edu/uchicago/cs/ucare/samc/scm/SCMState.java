package edu.uchicago.cs.ucare.samc.scm;

import edu.uchicago.cs.ucare.samc.util.LocalState;

@SuppressWarnings("serial")
public class SCMState extends LocalState {
	
	private int vote;
	
	public SCMState() {
		vote = 0;
	}
	
	public void setVote(int vote) {
		this.vote = vote;
	}
	
	public int getVote() {
		return vote;
	}
	
}
