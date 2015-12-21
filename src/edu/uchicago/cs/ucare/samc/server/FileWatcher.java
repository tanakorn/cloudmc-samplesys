package edu.uchicago.cs.ucare.samc.server;

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

public class FileWatcher implements Runnable{
	
	Path path;
	private boolean running;
	
	public FileWatcher(String sPath){
		path = Paths.get(sPath + "/send");
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
						System.out.println("[DEBUG] New file received: " + newFile);
					}
					
					if(!key.reset()) {
						break; //loop
					}
				}
				Thread.sleep(100);
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
}