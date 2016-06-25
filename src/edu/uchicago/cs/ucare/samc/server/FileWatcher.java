package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.event.Event;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;

public class FileWatcher implements Runnable{
	
	private final static Logger LOG = LoggerFactory.getLogger(FileWatcher.class);
	
	String ipcDir;
	File path;
	ModelCheckingServerAbstract checker;
	private HashMap<Integer, Integer> packetCount;
	
	public FileWatcher(String sPath, ModelCheckingServerAbstract modelChecker){
		ipcDir = sPath;
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
	    		LOG.info("[DEBUG] Receive update state " + filename);
	    		
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
		    	LOG.info("[DEBUG] Process new File " + filename + " : hashId-" + hashId +  " callbackName-" + callbackName +
		    			" sendNode-" + sendNode + " sendRole-" + sendRole + " recvNode-" + recvNode + 
		    			" leader-" + leader);
		    	
		    	// create eventPacket and store it to DMCK queue
//		    	LeaderElectionPacket packet = new LeaderElectionPacket(hashId, callbackName, 
//		    			sendNode, recvNode, filename, sendRole, leader);
		    	Event packet = new Event(hashId);
		    	packet.addKeyValue("sendNode", sendNode);
		    	packet.addKeyValue("recvNode", recvNode);
		    	packet.addKeyValue("filename", filename);
		    	packet.addKeyValue("role", sendRole);
		    	packet.addKeyValue("leader", leader);
		    	checker.offerPacket(packet);
	    	} else
	    	// SCM
	    	if(filename.startsWith("scm-")){
		    	String callbackName = ev.getProperty("callbackName");
	    		int eventId = Integer.parseInt(filename.substring(4));
	    		int hashId = commonHashId(eventId);
		    	int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
		    	int vote = Integer.parseInt(ev.getProperty("vote"));
	    		
		    	System.out.println("[DEBUG] Receive msg " + filename + " : hashId-" + hashId +  " from node-" + sendNode +
		    			" to node-" + recvNode + " callbackName-" + callbackName + " vote-" + vote);
		    	LOG.info("[DEBUG] Receive msg " + filename + " : hashId-" + hashId +  " from node-" + sendNode +
		    			" to node-" + recvNode + " callbackName-" + callbackName + " vote-" + vote);
		    	
//		    	SCMPacket packet = new SCMPacket(hashId, callbackName, sendNode, recvNode, filename, vote);
		    	Event packet = new Event(hashId);
		    	packet.addKeyValue(Event.FROM_ID, sendNode);
		    	packet.addKeyValue(Event.TO_ID, recvNode);
		    	packet.addKeyValue(Event.FILENAME, filename);
		    	packet.addKeyValue("vote", vote);
		    	checker.offerPacket(packet);
	    	} else if (filename.startsWith("updatescm-")){
	    		int vote = Integer.parseInt(ev.getProperty("vote"));
		    	
		    	System.out.println("[DEBUG] Update receiver node-" + sendNode + " with vote-" + vote);
		    	LOG.info("[DEBUG] Update receiver node-" + sendNode + " with vote-" + vote);
		    	
		    	checker.setSCMState(0, vote);
	    	}
	    	
	    	// remove the received msg
	    	Runtime.getRuntime().exec("rm " + path + "/" + filename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	// if we would like to directly release an event, 
	// use this function instead of offering the packet to SAMC
	public void ignoreEvent(String filename){
		try{
        	PrintWriter writer = new PrintWriter(ipcDir + "/new/" + filename, "UTF-8");
        	writer.println("eventId=" + filename);
	        writer.close();
	        
	        System.out.println("DMCK for now ignores event with ID : " + filename);
	        LOG.info("DMCK for now ignores event with ID : " + filename);
	        
	        Runtime.getRuntime().exec("mv " + ipcDir + "/new/" + filename + " " + 
	        		ipcDir + "/ack/" + filename);
    	} catch (Exception e) {
    		LOG.error("Error in ignoring event with file : " + filename);
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