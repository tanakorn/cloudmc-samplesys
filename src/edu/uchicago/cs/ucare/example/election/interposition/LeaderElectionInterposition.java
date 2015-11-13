package edu.uchicago.cs.ucare.example.election.interposition;

import java.rmi.Naming;
import java.util.Map;

import edu.uchicago.cs.ucare.example.election.LeaderElectionMain.Sender;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionAspectProperties;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionCallback;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacket;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacketGenerator;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServer;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.PacketReceiveAck;

public class LeaderElectionInterposition {

    public static boolean SAMC_ENABLED;

	public static int id;
	
	public static boolean isBound;
	
	public static ModelCheckingServer modelCheckingServer;
	
	public static Map<Integer, LeaderElectionPacket> nodeSenderMap;
	public static Map<Integer, Sender> msgSenderMap;
	
	public static LeaderElectionPacketGenerator packetGenerator;
	public static LeaderElectionPacketGenerator packetGenerator2;
	public static LeaderElectionCallback callback;
	
    public static PacketReceiveAck ack;
    
    public static int numNode;
	public static boolean[] isReading;
	
	public static LeaderElectionLocalState localState;
	
    static {
		SAMC_ENABLED = Boolean.parseBoolean(System.getProperty("samc_enabled", "false"));
		if (SAMC_ENABLED) {
		    try {
                modelCheckingServer = (ModelCheckingServer) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName());
                ack = (PacketReceiveAck) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName() + "Ack");
                localState = new LeaderElectionLocalState();
            } catch (Exception e) {
                System.err.println("Cannot find model checking server, switch to no-SAMC mode");
                SAMC_ENABLED = false;
            }
		}
    }
    
//	static void bindCallback() {
//		callback = new LeaderElectionCallback(id, nodeSenderMap, msgSenderMap);
//		callback.bind();
//	}
}
