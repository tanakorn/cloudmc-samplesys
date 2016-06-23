package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.LinkedList;

import edu.uchicago.cs.ucare.samc.event.DiskWrite;
import edu.uchicago.cs.ucare.samc.event.InterceptPacket;
import edu.uchicago.cs.ucare.samc.transition.PacketSendTransition;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;

public class RelayModelChecker extends GuideModelChecker {
    
    protected LinkedList<PacketSendTransition> currentLevelPackets;
    
    public RelayModelChecker(String interceptorName, String ackName,
            int numNode, String globalStatePathDir,
            String workingDir, WorkloadDriver workloadDriver, String ipcDir)
            throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, null, workingDir, workloadDriver, ipcDir);
        currentLevelPackets = new LinkedList<PacketSendTransition>();
        resetTest();
    }

    public RelayModelChecker(String interceptorName, String ackName,
            int numNode, String globalStatePathDir, File program,
            String workingDir, WorkloadDriver workloadDriver, String ipcDir)
            throws FileNotFoundException {
        super(interceptorName, ackName, numNode, globalStatePathDir, program, workingDir, workloadDriver, ipcDir);
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
                        LOG.error(e.getMessage());
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
