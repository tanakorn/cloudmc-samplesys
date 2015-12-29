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
			// Folder does not exists
			ioe.printStackTrace();
		}
	}
	
	public void run(){
		System.out.println("[DEBUG] Watching path: " + path);
		
		// We obtain the file system of the Path
		FileSystem fs = path.getFileSystem();
		
		try{
			// We create the new WatchService using the new try() block
			WatchService service = fs.newWatchService();
			
			// We register the path to the service
			// We watch for creation events
			path.register(service, StandardWatchEventKinds.ENTRY_CREATE);
			
			// Start the infinite polling loop
			WatchKey key = null;
			while(running) {
				key = service.take();
				
				// Dequeueing events
				Kind<?> kind = null;
				for(WatchEvent<?> watchEvent : key.pollEvents()) {
					// Get the type of the event
					kind = watchEvent.kind();
					
					if (StandardWatchEventKinds.ENTRY_CREATE == kind) {
						// A new Path was created 
						Path newFile = ((WatchEvent<Path>) watchEvent).context();
						// Output
//						System.out.println("[DEBUG] New file received: " + newFile);
						processNewFile(newFile.toString());
					}
					
					if(!key.reset()) {
						break; //loop
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
	    	if(filename.startsWith("s")){
	    		System.out.println("[DEBUG] Receive steady state " + filename);
	    		checker.informSteadyState(sendNode, 0);
	    	} else if(filename.startsWith("u")){
	    		int sendRole = Integer.parseInt(ev.getProperty("sendRole"));
	    		int leader = Integer.parseInt(ev.getProperty("leader"));
	    		String sElectionTable = ev.getProperty("electionTable").substring(0, ev.getProperty("electionTable").length()-1);
//	    		System.out.println("[DEBUG] Vote's Map : " + sElectionTable);
	    		String[] electionTableValues = sElectionTable.split(",");
	    		Map<Integer, Integer> electionTable = new HashMap<Integer, Integer>();
	    		for (String value : electionTableValues){
	    			String[] temp = value.split(":");
	    			electionTable.put(Integer.parseInt(temp[0]), Integer.parseInt(temp[1]));
	    		}
	    		
	    		System.out.println("[DEBUG] Receive update state " + filename);
	    		
	    		LeaderElectionLocalState state = new LeaderElectionLocalState(leader, sendRole, electionTable);
	    		checker.setLocalState(sendNode, state);
	    	} else {
		    	String callbackName = ev.getProperty("callbackName");
		    	int recvNode = Integer.parseInt(ev.getProperty("recvNode"));
		    	int sendRole = Integer.parseInt(ev.getProperty("sendRole"));
		    	int leader = Integer.parseInt(ev.getProperty("leader"));
		    	
		    	System.out.println("[DEBUG] Process new File : eventId-"+ filename + " callbackName-" + callbackName +
		    			" sendNode-" + sendNode + " sendRole-" + sendRole + " recvNode-" + recvNode + 
		    			" leader-" + leader);
		    	
		    	// create eventPacket and store it to DMCK queue
		    	LeaderElectionPacket packet = new LeaderElectionPacket(Integer.parseInt(filename), callbackName, 
		    			sendNode, recvNode, sendRole, leader);
		    	checker.offerPacket(packet);
	    	}
	    	
	    	// remove the received msg
	    	Runtime.getRuntime().exec("rm " + path + "/" + filename);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}