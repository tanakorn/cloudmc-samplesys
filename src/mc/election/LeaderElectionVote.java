package mc.election;

import java.io.Serializable;
import java.nio.ByteBuffer;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;

public class LeaderElectionVote implements Serializable {
	
	int sender;
	int role;
	int leader;
    
    public LeaderElectionVote() {
        
    }
    
    public LeaderElectionVote(LeaderElectionPacket leaderElectionPacket) {
        ByteBuffer c = ByteBuffer.wrap(leaderElectionPacket.getData());
        sender = c.getInt();
        role = c.getInt();
        leader = c.getInt();
    }
    
    public LeaderElectionVote(int sender, int role, int leader) {
		super();
		this.sender = sender;
		this.role = role;
		this.leader = leader;
	}
    
    /*
    public static boolean isMoreInteresting(LeaderElectionVote newVote, 
            LeaderElectionVote oldVote) {
    	if (oldVote.role == LeaderElectionMain.LOOKING) {
    		if (newVote.role == LeaderElectionMain.LOOKING) {
    			if (newVote.leader > oldVote.leader) {
    				return true;
    			}
    		} else {
    			return true;
    		}
    	}
    	return false;
    }
    */
    
    public static boolean isMoreInteresting(LeaderElectionVote newVote, 
            LeaderElectionVote oldVote, int currentRole, int currentLeader) {
    	if (currentRole == LeaderElectionMain.LOOKING) {
            if (oldVote.role == LeaderElectionMain.LOOKING) {
                if (newVote.role == LeaderElectionMain.LOOKING) {
                    if (newVote.leader > oldVote.leader) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
    	}
    	return false;
    }
    
    public String toString() {
    	return "Vote from " + sender + " : role=" + LeaderElectionMain.getRoleName(role) + ", leader=" + leader;
    }

    
}