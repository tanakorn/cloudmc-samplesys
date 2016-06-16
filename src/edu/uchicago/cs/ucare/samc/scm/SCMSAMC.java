package edu.uchicago.cs.ucare.samc.scm;

import edu.uchicago.cs.ucare.samc.election.DporModelChecker;
import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.util.LocalState;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;

public class SCMSAMC extends DporModelChecker {

	public SCMSAMC(String interceptorName, String ackName, int maxId, int numCrash, int numReboot,
			String globalStatePathDir, String packetRecordDir, String cacheDir, WorkloadDriver workloadDriver,
			String ipcDir) {
		super(interceptorName, ackName, maxId, numCrash, numReboot, globalStatePathDir, packetRecordDir, cacheDir,
				workloadDriver, ipcDir);
	}

	@Override
	public boolean isDependent(LocalState state, Event e1, Event e2) {
		SCMState receiverState = (SCMState) state;
		int v1 = (int) e1.getValue(SCMPacket.VOTE);
		int v2 = (int) e2.getValue(SCMPacket.VOTE);
		if(receiverState.getVote() <= v1 || receiverState.getVote() <= v2){
			return true;
		}
		return false;
	}

}
