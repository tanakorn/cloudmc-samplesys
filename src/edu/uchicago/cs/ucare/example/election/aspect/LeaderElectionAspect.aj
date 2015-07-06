package edu.uchicago.cs.ucare.example.election.aspect;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.example.election.ElectionMessage;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain.Processor;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain.Receiver;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain.Sender;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionAspectProperties;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionCallback;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacket;
import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacketGenerator;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServer;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;
import edu.uchicago.cs.ucare.samc.util.PacketReceiveAck;

public aspect LeaderElectionAspect {
	
	Logger logger = LoggerFactory.getLogger(LeaderElectionAspect.class);
	
	int id;
	LeaderElectionLocalState localState;
	
	boolean isBound;
	
	ModelCheckingServer modelCheckingServer;
	
	Map<Integer, LeaderElectionPacket> nodeSenderMap;
	Map<Integer, Sender> msgSenderMap;
	
	LeaderElectionPacketGenerator packetGenerator;
	LeaderElectionPacketGenerator packetGenerator2;
	LeaderElectionCallback callback;
	
    PacketReceiveAck ack;
    
    int numNode;
	boolean[] isReading;


	public LeaderElectionAspect() {
		nodeSenderMap = new HashMap<Integer, LeaderElectionPacket>();
		msgSenderMap = new HashMap<Integer, Sender>();
		packetGenerator = new LeaderElectionPacketGenerator();
		packetGenerator2 = new LeaderElectionPacketGenerator();
		localState = new LeaderElectionLocalState();
		isBound = false;
		try {
			modelCheckingServer = (ModelCheckingServer) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName());
            ack = (PacketReceiveAck) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName() + "Ack");
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	pointcut setId(int id) : set(static int LeaderElectionMain.id) && args(id);
	
	after(int id) : setId(id) {
		this.id = id;
	}
	
	pointcut setRole(int role) : set(static int LeaderElectionMain.role) && args(role);
	
	after(int role) : setRole(role) {
		this.localState.setRole(role);
		try {
		    logger.info("node " + id + " sets its role to be " + role);
			modelCheckingServer.setLocalState(id, localState);
			modelCheckingServer.updateLocalState(id, getLocalState());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	pointcut setLeader(int leader) : set(static int LeaderElectionMain.leader) && args(leader);
	
	after(int leader) : setLeader(leader) {
		this.localState.setLeader(leader);
		try {
			modelCheckingServer.setLocalState(id, localState);
			modelCheckingServer.updateLocalState(id, getLocalState());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	pointcut setElectionTable(Map<Integer, Integer> electionTable) : 
		set(static Map<Integer, Integer> LeaderElectionMain.electionTable) && 
        args(electionTable);
	
	after(Map<Integer, Integer> electionTable) : setElectionTable(electionTable) {
		this.localState.setElectionTable(electionTable);
		try {
			modelCheckingServer.setLocalState(id, localState);
			modelCheckingServer.updateLocalState(id, getLocalState());
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	pointcut startWorking() : execution(public static void LeaderElectionMain.work());

	before() : startWorking() {
		numNode = LeaderElectionMain.nodeMap.size();
		isReading = new boolean[numNode];
		Arrays.fill(isReading, false);
	}
	
	after() : startWorking() {
		bindCallback();
		isBound = true;
		if (isReadingForAll() && !isThereSendingMessage() && isBound) {
			try {
				logger.info("node " + id + " is in steady state "); 
				modelCheckingServer.informSteadyState(id, getLocalState());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	void bindCallback() {
		callback = new LeaderElectionCallback(id, nodeSenderMap, msgSenderMap);
		callback.bind();
	}
	
	pointcut write(Sender sender, ElectionMessage msg) : call(public void Sender.write(ElectionMessage)) 
        && this(sender) && args(msg) && within(Sender);
	
	void around(Sender sender, ElectionMessage msg) : write(sender, msg) {
	    try {
	    LeaderElectionPacket packet = new LeaderElectionPacket("LeaderElectionCallback" + id);
	    packet.addKeyValue(LeaderElectionPacket.EVENT_ID_KEY, hash(msg, sender.otherId));
	    packet.addKeyValue(LeaderElectionPacket.SOURCE_KEY, id);
	    packet.addKeyValue(LeaderElectionPacket.DESTINATION_KEY, sender.otherId);
	    packet.addKeyValue(LeaderElectionPacket.LEADER_KEY, msg.getLeader());
	    packet.addKeyValue(LeaderElectionPacket.ROLE_KEY, msg.getRole());
		nodeSenderMap.put(packet.getId(), packet);
		msgSenderMap.put(packet.getId(), sender);
            try {
                modelCheckingServer.offerPacket(packet);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
	    } catch (Exception e) {
	        logger.error("", e);
	    }
	}
	
	Integer hash(ElectionMessage msg, int toId) {
	    return packetGenerator.getHash(msg, toId);
	}

	pointcut process(ElectionMessage msg) : call(public void Processor.process(ElectionMessage)) && args(msg);
	
	before(ElectionMessage msg) : process(msg) {
		LeaderElectionPacket packet = packetGenerator2.createNewLeaderElectionPacket("LeaderElectionCallback" + id, msg.getSender(), id, msg.getRole(), msg.getLeader());
		try {
			ack.ack(packet.getId(), id);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public int getLocalState() {
		return localState.hashCode();
	}
	
	/* --- compute steady state --- */

	pointcut reading(Receiver receiver) : call(void Receiver.read(DataInputStream, byte[]) throws IOException) && this(receiver);
	
	before(Receiver receiver) : reading(receiver) {
		isReading[receiver.otherId] = true;
		logger.info("Reading for " + receiver.otherId + " : " + isReadingForAll() + " " + !isThereSendingMessage());
		if (isReadingForAll() && !isThereSendingMessage() && isBound) {
			try {
				logger.info("node " + id + " is in steady state "); 
				modelCheckingServer.informSteadyState(id, getLocalState());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	after(Receiver receiver) : reading(receiver) {
		isReading[receiver.otherId] = false;
		logger.info("Finished reading for " + receiver.otherId + " : " + isReadingForAll() + " " + !isThereSendingMessage());
	}

	after(Sender sender, ElectionMessage msg) : write(sender, msg) {
		logger.info("Finished writing for " + sender.otherId + " " + isReadingForAll() + " " + !isThereSendingMessage());
		if (isReadingForAll() && !isThereSendingMessage() && isBound) {
			try {
				logger.info("node " + id + " is in steady state "); 
				modelCheckingServer.informSteadyState(id, getLocalState());
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	boolean firstSent = false;
	
	pointcut firstSendAll() : call(public void Processor.sendAll(ElectionMessage)) && 
	    within(LeaderElectionMain) && !within(Processor);
	
	after() : firstSendAll() {
		firstSent = true;
	}
	
	public boolean isThereSendingMessage() {
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
	
	public boolean isReadingForAll() {
		for (int i = 0; i < numNode; ++i) {
			if (i != id) {
				if (!isReading[i]) {
					return false;
				}
			}
		}
		return true;
	}
	
}

