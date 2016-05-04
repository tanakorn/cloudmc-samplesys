package edu.uchicago.cs.ucare.samc.scm;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;

@SuppressWarnings("serial")
public class SCMPacket extends InterceptPacket {
	
    public static final String MSG_CONTENT = "msgContent";
	
	public SCMPacket(int id, String callbackName, int fromId,
			int toId, String msgContent) {
		super(id, callbackName, fromId, toId);
		addKeyValue(MSG_CONTENT, msgContent);
	}
	
}