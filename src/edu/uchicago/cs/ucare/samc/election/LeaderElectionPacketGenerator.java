package edu.uchicago.cs.ucare.samc.election;

import java.util.Arrays;
import java.util.HashMap;

public class LeaderElectionPacketGenerator {
    
    private HashMap<Integer, Integer> packetCount;
    
    public LeaderElectionPacketGenerator() {
        packetCount = new HashMap<Integer, Integer>();
    }
    
    public LeaderElectionPacket createNewLeaderElectionPacket(String callbackName, int fromId, int toId, byte[] data) {
        int hash = leaderElectionHashCodeWithoutId(fromId, toId, data);
        Integer count = packetCount.get(hash);
        if (count == null) {
            count = 0;
        }
        ++count;
        int id = 31 * hash + count;
        packetCount.put(hash, count);
        return new LeaderElectionPacket(id, callbackName, fromId, toId, data);
    }
    
    private static int leaderElectionHashCodeWithoutId(int fromId, int toId, byte[] data) {
        final int prime = 31;
        int result = 1;
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + fromId;
        result = prime * result + toId;
        return result;
    }
    
}
