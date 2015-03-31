package mc;

import java.io.Serializable;

public class DiskWrite implements Serializable {
    
    int writeId;
    int nodeId;
    int dataHash;
    
    public DiskWrite() {
        
    }
    
    public DiskWrite(int id, int nodeId, int dataHash) {
        super();
        this.writeId = id;
        this.nodeId = nodeId;
        this.dataHash = dataHash;
    }
    
    public int getWriteId() {
        return writeId;
    }
    
    public void setWriteId(int id) {
        this.writeId = id;
    }
    
    public int getNodeId() {
        return nodeId;
    }
    
    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }
    
    public int getDataHash() {
        return dataHash;
    }
    
    public void setDataHash(int dataHash) {
        this.dataHash = dataHash;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + dataHash;
        result = prime * result + writeId;
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
        DiskWrite other = (DiskWrite) obj;
        if (dataHash != other.dataHash)
            return false;
        if (writeId != other.writeId)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DiskWrite id=" + writeId + " nodeId=" + nodeId + 
                " dataHash=" + dataHash;
    }
    
}
