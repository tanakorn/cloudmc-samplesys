package mc.election;

import mc.InterceptPacket;

public class LeaderElectionPacket extends InterceptPacket {

	public LeaderElectionPacket(int id, String callbackName, int fromId,
			int toId, byte[] data) {
		super(id, callbackName, fromId, toId, data);
	}

}
