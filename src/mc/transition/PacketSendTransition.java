package mc.transition;

import java.io.Serializable;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import mc.InterceptPacket;
import mc.ModelChecker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketSendTransition extends Transition implements Serializable {
    
    final static Logger LOG = LoggerFactory.getLogger(PacketSendTransition.class);
    
    public static final String ACTION = "packetsend";
    private static final short ACTION_HASH = (short) ACTION.hashCode();
    public static final Comparator<PacketSendTransition> COMPARATOR = new Comparator<PacketSendTransition>() {
        @Override
        public int compare(PacketSendTransition o1, PacketSendTransition o2) {
            Integer i1 = o1.getPacket().getId();
            Integer i2 = o2.getPacket().getId();
            return i1.compareTo(i2);
        }
    };

    protected ModelChecker checker;
    protected InterceptPacket packet;

    public PacketSendTransition(ModelChecker checker, InterceptPacket packet) {
        this.checker = checker;
        this.packet = packet;
    }

    @Override
    public boolean apply() {
        if (packet.isObsolete()) {
            LOG.debug("Trying to commit obsolete packet");
        }
        try {
            boolean result = checker.commitAndWait(packet);
            return result;
        } catch (InterruptedException e) {
            LOG.error(e.getMessage());
            return false;
        }
    }
    
    @Override
    public int getTransitionId() {
        int hash = ((int) ACTION_HASH) << 16;
        hash = hash | (0x0000FFFF & packet.getId());
        return hash;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((packet == null) ? 0 : packet.hashCode());
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
        PacketSendTransition other = (PacketSendTransition) obj;
        if (packet == null) {
            if (other.packet != null)
                return false;
        } else if (!packet.equals(other.packet))
            return false;
        return true;
    }

    public String toString() {
        return "packetsend transition_id=" + getTransitionId() + " " + packet.toString();
    }
    
    public static PacketSendTransition[] buildTransitions(ModelChecker checker, 
            InterceptPacket[] packets) {
        PacketSendTransition[] packetTransitions = new PacketSendTransition[packets.length];
        for (int i = 0; i < packets.length; ++i) {
            packetTransitions[i] = new PacketSendTransition(checker, packets[i]);
        }
        return packetTransitions;
    }
    
    public static LinkedList<PacketSendTransition> buildTransitions(ModelChecker checker, 
            List<InterceptPacket> packets) {
        LinkedList<PacketSendTransition> packetTransitions = 
                new LinkedList<PacketSendTransition>();
        for (InterceptPacket packet : packets) {
            packetTransitions.add(new PacketSendTransition(checker, packet));
        }
        return packetTransitions;
    }
    
    public InterceptPacket getPacket() {
        return packet;
    }

}
