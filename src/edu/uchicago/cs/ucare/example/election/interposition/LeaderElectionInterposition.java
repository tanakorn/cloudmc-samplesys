package edu.uchicago.cs.ucare.example.election.interposition;

import java.rmi.Naming;
import java.util.HashMap;
import java.util.Map;

import edu.uchicago.cs.ucare.example.election.ElectionMessage;
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
	
	public static boolean firstSent = false;

    static {
		SAMC_ENABLED = Boolean.parseBoolean(System.getProperty("samc_enabled", "false"));
		if (SAMC_ENABLED) {
            nodeSenderMap = new HashMap<Integer, LeaderElectionPacket>();
            msgSenderMap = new HashMap<Integer, Sender>();
            packetGenerator = new LeaderElectionPacketGenerator();
            packetGenerator2 = new LeaderElectionPacketGenerator();
            isBound = false;
            localState = new LeaderElectionLocalState();
		    try {
                modelCheckingServer = (ModelCheckingServer) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName());
                ack = (PacketReceiveAck) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName() + "Ack");
            } catch (Exception e) {
                System.err.println("Cannot find model checking server, switch to no-SAMC mode");
                SAMC_ENABLED = false;
            }
		}
    }
    
	public static void bindCallback() {
		callback = new LeaderElectionCallback(id, nodeSenderMap, msgSenderMap);
		callback.bind();
	}

	public static boolean isReadingForAll() {
        for (int i = 0; i < numNode; ++i) {
            if (i != id) {
                if (!isReading[i]) {
                    return false;
                }
            }
        }
        return true;
    }

	public static boolean isThereSendingMessage() {
        if (!firstSent) return true;
        for (Sender sender : msgSenderMap.values()) {
            synchronized (sender.queue) {
                if (!sender.queue.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

	public static Integer hash(ElectionMessage msg, int toId) {
        return packetGenerator.getHash(msg, toId);
    }

}
