package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacket;
import edu.uchicago.cs.ucare.samc.scm.SCMPacket;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;

public class FileWatcher implements Runnable{
	
	File path;
	ModelCheckingServerAbstract checker;
	private HashMap<Integer, Integer> packetCount;
	
	public FileWatcher(String sPath, ModelCheckingServerAbstract modelChecker){
		path = new File(sPath + "/send");
		checker = modelChecker;
		resetPacketCount();
		
		if (!path.isDirectory()) {
			throw new IllegalArgumentException("Path: " + path + " is not a folder");
		}
	}
	
	public void run(){
		System.out.println("[DEBUG] FileWatcher is looking after: " + path);
		
		while(!Thread.interrupted()){
			if(path.listFiles().length > 0){
				for(File file : path.listFiles()){
					processNewFile(file.getName());
				}
			}
			try {
				Thread.sleep(50);
			} catch (InterruptedException ie) {
				ie.printStackTrace();
			}
		}
	}
	
	public void resetPacketCount(){
		packetCount = new HashMap<Integer, Integer>();
	}
	
	public synchronized void processNewFile(String filename){
		try{
			Properties ev = new Properties();
			FileInputStream evInputStream = new FileInputStream(path + "/" + filename);
	    	ev.load(evInputStream);
	    	
	    	int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
	    	
	    	// we can inform the steady state manually to dmck to make the
	    	// dmck response's quicker, but it means we need to know when 
	    	// our target system node gets into steady state.
	    	// the current setting is, the target-sys nodes will get into steady state
	    	// after some time, specified by initSteadyStateTimeout
	    	/*
	    	if(filename.startsWith("s-")){
	    		System.out.println("[DEBUG] Receive steady state " + filename);
	    		checker.informSteadyState(sendNode, 0);
	    	} else
	    	*/ 
	    	// SAMPLE-LE
	    	if(filename.startsWith("u-")){
	    		int sendRole = Integer.parseInt(ev.getProperty("sendRole"));
	    		int leader = Integer.parseInt(ev.getProperty("leader"));
	    		String sElectionTable = ev.getProperty("electionTable").substring(0, ev.getProperty("electionTable").length()-1);
	    		String[] electionTableValues = sElectionTable.split(",");
	    		Map<Integer, Integer> electionTable = new HashMap<Integer, Integer>();
	    		for (String value : electionTableValues){
	    			String[] temp = value.split(":");
	    			electionTable.put(Integer.parseInt(temp[0]), Integer.parseInt(temp[1]));
	    		}
	    		
	    		System.out.println("[DEBUG] Receive update state " + filename);
	    		
	    		LeaderElectionLocalState state = new LeaderElectionLocalState(leader, sendRole, electionTable);
	    		checker.setLocalState(sendNode, state);
	    	} else if(filename.startsWith("le-")) {
		    	String callbackName = ev.getProperty("callbackName");
		    	int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
		    	int sendRole = Integer.parseInt(ev.getProperty("sendRole"));
		    	int leader = Integer.parseInt(ev.getProperty("leader"));
		    	int eventId = Integer.parseInt(filename.substring(3));
		    	int hashId = commonHashId(eventId);
		    	
		    	System.out.println("[DEBUG] Process new File " + filename + " : hashId-" + hashId +  " callbackName-" + callbackName +
		    			" sendNode-" + sendNode + " sendRole-" + sendRole + " recvNode-" + recvNode + 
		    			" leader-" + leader);
		    	
		    	// create eventPacket and store it to DMCK queue
		    	LeaderElectionPacket packet = new LeaderElectionPacket(hashId, callbackName, 
		    			sendNode, recvNode, filename, sendRole, leader);
		    	checker.offerPacket(packet);
	    	} else
	    	// SCM
	    	if(filename.startsWith("scm-")){
		    	String callbackName = ev.getProperty("callbackName");
	    		int eventId = Integer.parseInt(filename.substring(4));
	    		int hashId = commonHashId(eventId);
		    	int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
		    	String msgContent = ev.getProperty("msgContent");
	    		
		    	System.out.println("[DEBUG] Receive msg " + filename + " : hashId-" + hashId +  " from node-" + sendNode +
		    			" to node-" + recvNode + " callbackName-" + callbackName + " msgContent-" + msgContent);
		    	
		    	SCMPacket packet = new SCMPacket(hashId, callbackName, sendNode, recvNode, filename, msgContent);
		    	checker.offerPacket(packet);
	    	} else if (filename.startsWith("updatescm-")){
		    	String msgContent = filename.substring(10);
		    	
		    	System.out.println("[DEBUG] Receive state update from node-" + sendNode + " msgContent-" + msgContent);
		    	
		    	checker.setSCMState(msgContent);
	    	}
	    	
	    	// remove the received msg
	    	Runtime.getRuntime().exec("rm " + path + "/" + filename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private int commonHashId(int eventId){
		Integer count = packetCount.get(eventId);
        if (count == null) {
            count = 0;
        }
        count++;
        packetCount.put(eventId, count);
        return 31 * eventId + count;
	}
}