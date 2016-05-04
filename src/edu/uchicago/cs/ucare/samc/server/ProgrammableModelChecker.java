package edu.uchicago.cs.ucare.samc.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.NodeCrashTransition;
import edu.uchicago.cs.ucare.samc.transition.NodeStartTransition;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.transition.Transition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.LocalState;

public class ProgrammableModelChecker extends ModelCheckingServerAbstract {
    
    protected ProgramParser parser;
    protected LinkedList<InterceptPacket> enabledPackets;
    protected Thread afterProgramModelChecker;
    protected File program;
    
    public ProgrammableModelChecker(String interceptorName, String ackName, 
            int numNode, String globalStatePathDir, File program, 
            String workingDir, WorkloadDriver zkController) throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, workingDir, zkController);
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
    
    class ProgramExecutor extends ModelCheckingServerAbstract.Explorer {

        public ProgramExecutor(ModelCheckingServerAbstract checker) {
            super(checker);
        }
        
        @Override
        public void run() {
            InstructionTransition instruction;
            while (parser != null && (instruction = parser.readNextInstruction()) != null) {
                getOutstandingTcpPacketTransition(currentEnabledTransitions);
                Transition transition = instruction.getRealTransition(checker);
                if (transition == null) {
                    break;
                }
                if (transition.apply()) {
                    updateGlobalState();
                } else {
                    
                }
                if (transition instanceof PacketSendTransition) {
                    currentEnabledTransitions.remove(transition);
                }
            }
            if (afterProgramModelChecker != null) {
                afterProgramModelChecker.start();
            } else {
                checker.stopEnsemble();
                System.exit(0);
            }
        }
        
    }
    
    class ProgramParser {
        
        BufferedReader programReader;
        
        public ProgramParser(ModelCheckingServerAbstract checker, File program) throws FileNotFoundException {
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
                    String packetTransitionIdString = tokens[1].split("=")[1];
                    if (packetTransitionIdString.equals("*")) {
                        return new PacketSendInstuctionTransition(0);
                    } else {
                        long packetTransitionId = Long.parseLong(packetTransitionIdString);
                        return new PacketSendInstuctionTransition(packetTransitionId);
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

        abstract Transition getRealTransition(ModelCheckingServerAbstract checker);

    }
    
    class PacketSendInstuctionTransition extends InstructionTransition {
        
        long packetId;
        
        public PacketSendInstuctionTransition(long packetId) {
            this.packetId = packetId;
        }
        
        @Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            if (packetId == 0) {
                return (Transition) currentEnabledTransitions.peekFirst();
            }
            for (int i = 0; i < 15; ++i) {
                for (Object t : currentEnabledTransitions) {
                    PacketSendTransition p = (PacketSendTransition) t;
                    if (p.getTransitionId() == packetId) {
                        return p;
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    log.error("", e);
                }
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
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new NodeCrashTransition(checker, id);
        }
        
    }
    
    class NodeStartInstructionTransition extends InstructionTransition {
        
        int id;

        private NodeStartInstructionTransition(int id) {
            this.id = id;
        }

        @Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
            return new NodeStartTransition(checker, id);
        }
        
    }
    
    class SleepInstructionTransition extends InstructionTransition {
        
        long sleep;
        
        private SleepInstructionTransition(long sleep) {
            this.sleep = sleep;
        }

        @SuppressWarnings("serial")
		@Override
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
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
        Transition getRealTransition(ModelCheckingServerAbstract checker) {
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