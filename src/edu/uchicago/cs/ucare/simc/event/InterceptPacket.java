package edu.uchicago.cs.ucare.simc.event;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class InterceptPacket implements Serializable {
    
    protected int id;
    protected String callbackId;
    protected int fromId;
    protected int toId;
    protected byte[] data;
    protected boolean obsolete;
    protected int obsoleteBy;
    
    public InterceptPacket() {
        obsolete = false;
    }
    
    public InterceptPacket(int id, String callbackId, int fromId, int toId, byte[] data) {
        this.id = id;
        this.callbackId = callbackId;
        this.fromId = fromId;
        this.toId = toId;
        this.data = data;
        obsolete = false;
        obsoleteBy = -1;
    }
    
    public String getCallbackId() {
        return callbackId;
    }

    public void setCallbackId(String callbackId) {
        this.callbackId = callbackId;
    }

    public int getFromId() {
        return fromId;
    }

    public void setFromId(int fromId) {
        this.fromId = fromId;
    }

    public int getToId() {
        return toId;
    }

    public void setToId(int toId) {
        this.toId = toId;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }
    
    public boolean isObsolete() {
        return obsolete;
    }

    public void setObsolete(boolean obsolete) {
        this.obsolete = obsolete;
    }
    
    public int getObsoleteBy() {
        return obsoleteBy;
    }

    public void setObsoleteBy(int obsoleteBy) {
        if (this.obsoleteBy == -1) {
            this.obsoleteBy = obsoleteBy;
        }
    }

    public String toString() {
        return "packet id=" + id + " from=" + fromId + " to=" + toId + 
                " obselete=" + obsolete + " obsoleteby=" + obsoleteBy;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((callbackId == null) ? 0 : callbackId.hashCode());
        result = prime * result + Arrays.hashCode(data);
        result = prime * result + fromId;
        result = prime * result + id;
//        result = prime * result + (obsolete ? 1231 : 1237);
        result = prime * result + toId;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InterceptPacket other = (InterceptPacket) obj;
        if (callbackId == null) {
            if (other.callbackId != null)
                return false;
        } else if (!callbackId.equals(other.callbackId))
            return false;
        if (!Arrays.equals(data, other.data))
            return false;
        if (fromId != other.fromId)
            return false;
        if (id != other.id)
            return false;
//        if (obsolete != other.obsolete)
//            return false;
        if (toId != other.toId)
            return false;
        return true;
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
