package edu.uchicago.cs.ucare.samc.election;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;

public class LeaderElectionPacket extends InterceptPacket {

	public LeaderElectionPacket(int id, String callbackName, int fromId,
			int toId, byte[] data) {
		super(id, callbackName, fromId, toId, data);
	}

}
