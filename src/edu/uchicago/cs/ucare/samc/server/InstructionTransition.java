package edu.uchicago.cs.ucare.samc.server;

import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;

abstract class InstructionTransition {

    abstract Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue);

}

class PacketSendInstructionTransition extends InstructionTransition {
    
	protected final Logger LOG = LoggerFactory.getLogger(this.getClass());;
	
    long packetId;
    
    public PacketSendInstructionTransition(long packetId) {
        this.packetId = packetId;
    }
    
    @Override
    Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue) {
        if (packetId == 0) {
            return (Transition) queue.peekFirst();
        }
        for (int i = 0; i < 25; ++i) {
            for (Object t : queue) {
            	if(t instanceof PacketSendTransition){
                    PacketSendTransition p = (PacketSendTransition) t;
                    if (p.getTransitionId() == packetId) {
                        return p;
                    }
            	}
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                LOG.error("", e);
            }
            checker.getOutstandingTcpPacketTransition(queue);
        }
        throw new RuntimeException("No expected enabled packet for " + packetId);
    }
    
}

class NodeCrashInstructionTransition extends InstructionTransition {
    
    int id;

    protected NodeCrashInstructionTransition(int id) {
        this.id = id;
    }

    @Override
    Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue) {
        return new NodeCrashTransition(checker, id);
    }
    
}

class NodeStartInstructionTransition extends InstructionTransition {
    
    int id;

    protected NodeStartInstructionTransition(int id) {
        this.id = id;
    }

    @Override
    Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue) {
        return new NodeStartTransition(checker, id);
    }
    
}

class SleepInstructionTransition extends InstructionTransition {
    
    long sleep;
    
    protected SleepInstructionTransition(long sleep) {
        this.sleep = sleep;
    }

    @SuppressWarnings("serial")
	@Override
    Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue) {
        return new Transition() {

            @Override
            public boolean apply() {
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    return false;
                }
                return true;
            }

            @Override
            public int getTransitionId() {
                return 0;
            }
            
        };
    }
    
}

class ExitInstructionTransaction extends InstructionTransition {

    @SuppressWarnings("serial")
	@Override
    Transition getRealTransition(ModelCheckingServerAbstract checker, LinkedList<Transition> queue) {
        return new Transition() {
            
            @Override
            public int getTransitionId() {
                return 0;
            }
            
            @Override
            public boolean apply() {
                System.exit(0);
                return true;
            }
        };
    }
    
}
