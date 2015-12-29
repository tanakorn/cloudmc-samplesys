package edu.uchicago.cs.ucare.samc.election;

import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.ElectionMessage;
import edu.uchicago.cs.ucare.example.election.interposition.LeaderElectionInterposition;

public class LeaderElectionPacketGenerator {
	
    private HashMap<Integer, Integer> packetCount;
    
    public LeaderElectionPacketGenerator() {
        packetCount = new HashMap<Integer, Integer>();
    }
    
    public LeaderElectionPacket createNewLeaderElectionPacket(String callbackName, int fromId, int toId, int role, int leader) {
        int hash = leaderElectionHashCodeWithoutId(fromId, toId, role, leader);
        Integer count = packetCount.get(hash);
        if (count == null) {
            count = 0;
        }
        ++count;
        int id = 31 * hash + count;
        packetCount.put(hash, count);
        return new LeaderElectionPacket(id, callbackName, fromId, toId, role, leader);
    }
    
    private static int leaderElectionHashCodeWithoutId(int fromId, int toId, int role, int leader) {
        final int prime = 31;
        int result = 1;
        result = prime * result + fromId;
        result = prime * result + toId;
        result = prime * result + role;
        result = prime * result + leader;
        return result;
    }
    
    public int getHash(ElectionMessage msg, int toId) {
        int hash = leaderElectionHashCodeWithoutId(msg.getSender(), toId, msg.getRole(), msg.getLeader());
        Integer count = packetCount.get(hash);
        if (count == null) {
            count = 0;
        }
        ++count;
        int id = 31 * hash + count;
        packetCount.put(hash, count);
        return id;
    }
    
}
