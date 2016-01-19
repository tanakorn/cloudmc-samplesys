package edu.uchicago.cs.ucare.samc.server;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.WatchEvent.Kind;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;

import edu.uchicago.cs.ucare.samc.election.LeaderElectionPacket;
import edu.uchicago.cs.ucare.samc.scm.SCMPacket;
import edu.uchicago.cs.ucare.samc.util.LeaderElectionLocalState;

public class FileWatcher implements Runnable{
	
	Path path;
	ModelCheckingServerAbstract checker;
	private boolean running;
	
	public FileWatcher(String sPath, ModelCheckingServerAbstract modelChecker){
		path = Paths.get(sPath + "/send");
		checker = modelChecker;
		running = true;
		
		try {
			Boolean isFolder = (Boolean) Files.getAttribute(path,
					"basic:isDirectory", NOFOLLOW_LINKS);
			if (!isFolder) {
				throw new IllegalArgumentException("Path: " + path + " is not a folder");
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	public void run(){
		System.out.println("[DEBUG] Watching path: " + path);
		FileSystem fs = path.getFileSystem();
		
		try{
			WatchService service = fs.newWatchService();
			path.register(service, StandardWatchEventKinds.ENTRY_CREATE);
			
			WatchKey key = null;
			while(running) {
				key = service.take();
				Kind<?> kind = null;
				for(WatchEvent<?> watchEvent : key.pollEvents()) {
					kind = watchEvent.kind();
					if (StandardWatchEventKinds.ENTRY_CREATE == kind) {
						// A new Path was created 
						Path newFile = ((WatchEvent<Path>) watchEvent).context();
						processNewFile(newFile.toString());
					}
					
					if(!key.reset()) {
						break;
					}
				}
			}
		} catch(IOException ioe) {
			ioe.printStackTrace();
		} catch(InterruptedException ie){
			ie.printStackTrace();
		}
	}
	
	public void terminate(){
		running = false;
	}
	
	public synchronized void processNewFile(String filename){
		try{
			Properties ev = new Properties();
			FileInputStream evInputStream = new FileInputStream(path + "/" + filename);
	    	ev.load(evInputStream);
	    	
	    	int sendNode = Integer.parseInt(ev.getProperty("sendNode"));
	    	
	    	// we can inform the steady state manually to dmck to make the
	    	// dmck response's quicker, but it's more complicated because
	    	// it means we need to know when our target system node gets into
	    	// steady state. - for now we have made it get into steady state
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
		    	
		    	System.out.println("[DEBUG] Process new File : eventId-"+ eventId + " callbackName-" + callbackName +
		    			" sendNode-" + sendNode + " sendRole-" + sendRole + " recvNode-" + recvNode + 
		    			" leader-" + leader);
		    	
		    	// create eventPacket and store it to DMCK queue
		    	LeaderElectionPacket packet = new LeaderElectionPacket(eventId, callbackName, 
		    			sendNode, recvNode, sendRole, leader);
		    	checker.offerPacket(packet);
	    	} else
	    	// SCM
	    	if(filename.startsWith("scm-")){
		    	String callbackName = ev.getProperty("callbackName");
	    		int eventId = Integer.parseInt(filename.substring(4));
		    	int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
		    	String msgContent = ev.getProperty("msgContent");
	    		
		    	System.out.println("[DEBUG] Receive msg no " + eventId + " from node-" + sendNode +
		    			" to node-" + recvNode + " callbackName-" + callbackName + " msgContent-" + msgContent);
		    	
		    	SCMPacket packet = new SCMPacket(eventId, callbackName, sendNode, recvNode, msgContent);
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
}