package edu.uchicago.cs.ucare.samc.event;

import java.util.List;

public class InterceptPacket extends Event {
    
    public static final String SOURCE_KEY = "sourceNode";
    public static final String DESTINATION_KEY = "destinationNode";
    
    public InterceptPacket() {
    }
    
    public InterceptPacket(String callbackId) {
        super(callbackId);
    }
    
    public InterceptPacket(int id, String callbackId, int fromId, int toId) {
        super(id, callbackId);
        addKeyValue(SOURCE_KEY, fromId);
        addKeyValue(DESTINATION_KEY, toId);
        obsolete = false;
        obsoleteBy = -1;
    }
    
    public int getFromId() {
        return (Integer) getValue(SOURCE_KEY);
    }

    public void setFromId(int fromId) {
        addKeyValue(SOURCE_KEY, fromId);
    }

    public int getToId() {
        return (Integer) getValue(DESTINATION_KEY);
    }

    public void setToId(int toId) {
        addKeyValue(DESTINATION_KEY, toId);
    }

    public int getId() {
        return (Integer) getValue(EVENT_ID_KEY);
    }

    public void setId(int id) {
        addKeyValue(EVENT_ID_KEY, id);
    }

    public static String packetsToString(InterceptPacket[] packets) {
        String result = "";
        for (InterceptPacket packet : packets) {
            result += packet.toString() + "\n";
        }
        return result.substring(0, result.length() - 1);
    }
    
    public static String packetsToString(List<InterceptPacket> packets) {
        String result = "";
        for (InterceptPacket packet : packets) {
            result += packet.toString() + "\n";
        }
        return result.substring(0, result.length() - 1);
    }

}
