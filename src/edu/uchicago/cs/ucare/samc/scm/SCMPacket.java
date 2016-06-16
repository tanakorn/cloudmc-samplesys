package edu.uchicago.cs.ucare.samc.scm;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;

@SuppressWarnings("serial")
public class SCMPacket extends InterceptPacket {
	
    public static final String VOTE = "vote";
	
	public SCMPacket(int id, String callbackName, int fromId,
			int toId, String filename, int vote) {
		super(id, callbackName, fromId, toId, filename);
		addKeyValue(VOTE, vote);
	}
	
}