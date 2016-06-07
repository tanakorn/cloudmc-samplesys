package edu.uchicago.cs.ucare.samc.election;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;

@SuppressWarnings("serial")
public class LeaderElectionPacket extends InterceptPacket {
    
    public static final String ROLE_KEY = "role";
    public static final String LEADER_KEY = "leader";

	public LeaderElectionPacket(int id, String callbackName, int fromId,
			int toId, String filename, int role, int leader) {
		super(id, callbackName, fromId, toId, filename);
		addKeyValue(ROLE_KEY, role);
		addKeyValue(LEADER_KEY, leader);
	}
	
	public LeaderElectionPacket(String callbackId) {
	    super(callbackId);
    }

    public void setRole(int role) {
		addKeyValue(ROLE_KEY, role);
	}
	
	public int getRole() {
	    return (Integer) getValue(ROLE_KEY);
	}

	public void setLeader(int leader) {
		addKeyValue(LEADER_KEY, leader);
	}
	
	public int getLeader() {
	    return (Integer) getValue(LEADER_KEY);
	}

}
