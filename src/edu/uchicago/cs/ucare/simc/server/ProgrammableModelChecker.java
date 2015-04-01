package edu.uchicago.cs.ucare.simc.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.simc.event.InterceptPacket;
import edu.uchicago.cs.ucare.simc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.simc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.simc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.simc.transition.Transition;
import edu.uchicago.cs.ucare.simc.util.EnsembleController;
import edu.uchicago.cs.ucare.simc.util.LocalState;
import edu.uchicago.cs.ucare.simc.util.WorkloadFeeder;

public class ProgrammableModelChecker extends SteadyStateInformedModelChecker {
    
    protected ProgramParser parser;
    protected LinkedList<InterceptPacket> enabledPackets;
    protected Thread afterProgramModelChecker;
    protected File program;
    
    public ProgrammableModelChecker(String interceptorName, String ackName, 
            int numNode, String globalStatePathDir, File program, 
            EnsembleController zkController, WorkloadFeeder feeder) throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, zkController, feeder);
        this.program = program;
        afterProgramModelChecker = null;
        resetTest();
    }
    
    @Override
    public void resetTest() {
        super.resetTest();
        try {
            if (program != null) {
                parser = new ProgramParser(this, program);
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e.getMessage());
        }
        modelChecking = new ProgramExecutor(this);
        enabledPackets = new LinkedList<InterceptPacket>();
    }
    
    class ProgramExecutor extends SteadyStateInformedModelChecker.Explorer {

        public ProgramExecutor(SteadyStateInformedModelChecker checker) {
            super(checker);
        }
        
        @Override
        public void run() {
            InstructionTransition instruction;
            while (parser != null && (instruction = parser.readNextInstruction()) != null) {
//                getOutstandingTcpPacket(enabledPackets);
                getOutstandingTcpPacketTransition(currentEnabledTransitions);
//                log.info("korn " + enabledPackets.toString());
//                log.info("korn " + currentEnabledTransitions.toString());
                Transition transition = instruction.getRealTransition(checker);
                if (transition == null) {
                    break;
                }
                if (transition.apply()) {
                    updateGlobalState();
                } else {
                    
                }
                if (transition instanceof PacketSendTransition) {
//                    enabledPackets.remove(((PacketSendTransition) transition).getPacket());
                    currentEnabledTransitions.remove(transition);
                }
            }
            if (afterProgramModelChecker != null) {
                afterProgramModelChecker.start();
            }
        }
        
    }
    
    class ProgramParser {
        
        BufferedReader programReader;
        
        public ProgramParser(ModelChecker checker, File program) throws FileNotFoundException {
            this.programReader = new BufferedReader(new FileReader(program));
        }
        
        public InstructionTransition readNextInstruction() {
            try {
                String transitionString = programReader.readLine();
                if (transitionString == null) {
                    return null;
                }
                String[] tokens = transitionString.split(" ");
                if (tokens[0].equals("packetsend")) {
                    String packetIdString = tokens[1].split("=")[1];
                    if (packetIdString.equals("*")) {
                        return new PacketSendInstuctionTransition(0);
                    } else {
                        long packetId = Long.parseLong(packetIdString);
                        return new PacketSendInstuctionTransition(packetId);
                    }
                } else if (tokens[0].equals("nodecrash")) {
                    int id = Integer.parseInt(tokens[1].split("=")[1]);
                    return new NodeCrashInstructionTransition(id);
                } else if (tokens[0].equals("nodestart")) {
                    int id = Integer.parseInt(tokens[1].split("=")[1]);
                    return new NodeStartInstructionTransition(id);
                } else if (tokens[0].equals("sleep")) {
                    long sleep = Long.parseLong(tokens[1].split("=")[1]);
                    return new SleepInstructionTransition(sleep);
                } else if (tokens[0].equals("stop")) {
                    return new ExitInstructionTransaction();
                }
            } catch (IOException e) {
                return null;
            }
            return null;
        }
        
    }
    
    abstract class InstructionTransition {

        abstract Transition getRealTransition(ModelChecker checker);

    }
    
    class PacketSendInstuctionTransition extends InstructionTransition {
        
        long packetId;
        
        public PacketSendInstuctionTransition(long packetId) {
            this.packetId = packetId;
        }
        
        @Override
        Transition getRealTransition(ModelChecker checker) {
            if (packetId == 0) {
                return (Transition) currentEnabledTransitions.peekFirst();
            }
            for (int i = 0; i < 15; ++i) {
//                for (InterceptPacket packet : enabledPackets) {
//                    if (packet.getId() == packetId) {
//                        return new PacketSendTransition(checker, packet);
//                    }
//                }
                for (Object t : currentEnabledTransitions) {
                    PacketSendTransition p = (PacketSendTransition) t;
                    if (p.getPacket().getId() == packetId) {
                        return p;
                    }
                }
                try {
                    log.info("korn wait for new packet");
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error("", e);
                }
//                getOutstandingTcpPacket(enabledPackets);
                getOutstandingTcpPacketTransition(currentEnabledTransitions);
            }
            throw new RuntimeException("No expected enabled packet for " + packetId);
        }
        
    }
    
    class NodeCrashInstructionTransition extends InstructionTransition {
        
        int id;

        private NodeCrashInstructionTransition(int id) {
            this.id = id;
        }

        @Override
        Transition getRealTransition(ModelChecker checker) {
            return new NodeCrashTransition(checker, id);
        }
        
    }
    
    class NodeStartInstructionTransition extends InstructionTransition {
        
        int id;

        private NodeStartInstructionTransition(int id) {
            this.id = id;
        }

        @Override
        Transition getRealTransition(ModelChecker checker) {
            return new NodeStartTransition(checker, id);
        }
        
    }
    
    class SleepInstructionTransition extends InstructionTransition {
        
        long sleep;
        
        private SleepInstructionTransition(long sleep) {
            this.sleep = sleep;
        }

        @Override
        Transition getRealTransition(ModelChecker checker) {
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

        @Override
        Transition getRealTransition(ModelChecker checker) {
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

	@Override
	public void setLocalState(int nodeId, LocalState localState)
			throws RemoteException {
		// TODO Auto-generated method stub
		
	}
    
}