package edu.uchicago.cs.ucare.samc.election;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;

import edu.uchicago.cs.ucare.example.election.ElectionMessage;
import edu.uchicago.cs.ucare.example.election.LeaderElectionMain.Sender;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServer;
import edu.uchicago.cs.ucare.samc.util.PacketReleaseCallback;

public class LeaderElectionCallback implements PacketReleaseCallback {
	
	int id;
	Map<Integer, LeaderElectionPacket> nodeSenderMap;
	Map<Integer, Sender> msgSenderMap;
	
	public LeaderElectionCallback(int id, 
			Map<Integer, LeaderElectionPacket> nodeSenderMap, 
			Map<Integer, Sender> msgSenderMap) {
		this.id = id;
		this.nodeSenderMap = nodeSenderMap;
		this.msgSenderMap = msgSenderMap;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	@Override
	public boolean callback(int packetId) throws RemoteException {
		LeaderElectionPacket packet = nodeSenderMap.get(packetId);
		Sender sender = msgSenderMap.get(packetId);
		ElectionMessage message = new ElectionMessage(id, packet.getRole(), packet.getLeader());
		sender.write(message);
		return true;
	}
	
	// TODO correct binding name
	public void bind() {
		try {
			PacketReleaseCallback callbackStub = (PacketReleaseCallback) 
			        UnicastRemoteObject.exportObject(this, 0);
            Registry r = LocateRegistry.getRegistry();
            r.rebind(LeaderElectionAspectProperties.getInterceptorName() + "LeaderElectionCallback" + id, callbackStub);
            ModelCheckingServer callbackInterceptor = (ModelCheckingServer) Naming.lookup(LeaderElectionAspectProperties.getInterceptorName());
            callbackInterceptor.registerCallback(id, "LeaderElectionCallback" + id);
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
