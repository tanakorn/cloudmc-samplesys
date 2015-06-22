package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.util.EnsembleController;
import edu.uchicago.cs.ucare.samc.util.WorkloadFeeder;

public class RelayModelChecker extends ProgrammableModelChecker {
    
    protected LinkedList<PacketSendTransition> currentLevelPackets;
    
    public RelayModelChecker(String interceptorName, String ackName,
            int numNode, String globalStatePathDir,
            EnsembleController zkController, WorkloadFeeder feeder)
            throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, null,
                zkController, feeder);
        currentLevelPackets = new LinkedList<PacketSendTransition>();
        resetTest();
    }

    public RelayModelChecker(String interceptorName, String ackName,
            int numNode, String globalStatePathDir, File program,
            EnsembleController zkController, WorkloadFeeder feeder)
            throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, program,
                zkController, feeder);
        currentLevelPackets = new LinkedList<PacketSendTransition>();
        resetTest();
    }
    
    @Override
    public void resetTest() {
        if (currentLevelPackets == null) {
            return;
        }
        super.resetTest();
        afterProgramModelChecker = new RelayWorker(this);
        currentLevelPackets.clear();
    }
    
    protected class RelayWorker extends ModelCheckingServerAbstract.Explorer {

        public RelayWorker(ModelCheckingServerAbstract checker) {
            super(checker);
        }
        
        public void run() {
            currentLevelPackets = PacketSendTransition.buildTransitions(checker, enabledPackets); 
            LinkedList<InterceptPacket> thisLevelPackets = new LinkedList<InterceptPacket>();
            while (true) {
                while (!writeQueue.isEmpty()) {
                    DiskWrite write = writeQueue.peek();
                    try {
                        writeAndWait(write);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
                thisLevelPackets.clear();
                getOutstandingTcpPacket(thisLevelPackets);
                currentLevelPackets.addAll(PacketSendTransition.buildTransitions(checker, thisLevelPackets));
                for (PacketSendTransition packet : currentLevelPackets) {
                    if (packet.apply()) {
                        updateGlobalState();
                    }
                }
                currentLevelPackets.clear();
            }
        }
        
    }

}