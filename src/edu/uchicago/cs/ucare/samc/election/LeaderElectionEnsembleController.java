package edu.uchicago.cs.ucare.samc.election;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;

public class LeaderElectionEnsembleController extends WorkloadDriver {
    
    private final static Logger LOG = LoggerFactory.getLogger(LeaderElectionEnsembleController.class);
    
    String ipcDir;
    
    Process[] node;
    Thread consoleWriter;
    FileOutputStream[] consoleLog;
    
    public LeaderElectionEnsembleController(int numNode, String workingDir, String sIpcDir) {
        super(numNode, workingDir);
        ipcDir = sIpcDir;
        node = new Process[numNode];
        consoleLog = new FileOutputStream[numNode];
        consoleWriter = new Thread(new LogWriter());
        consoleWriter.start();
    }
    
    public void resetTest() {
        for (int i = 0; i < numNode; ++i) {
            if (consoleLog[i] != null) {
                try {
                    consoleLog[i].close();
                } catch (IOException e) {
                    LOG.error("", e);
                }
            }
            try {
                consoleLog[i] = new FileOutputStream(workingDir + "/console/" + i);
            } catch (FileNotFoundException e) {
                LOG.error("", e);
            }
        }
    }
    
    public void startEnsemble() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting ensemble");
        }
        for (int i = 0; i < numNode; ++i) {
            try {
            	startNode(i);
                Thread.sleep(300);
            } catch (InterruptedException e) {
                LOG.error("Error in starting node " + i);
            }
        }
    }
    
    public void stopEnsemble() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping ensemble");
        }
        for (int i=0; i<numNode; i++) {
        	try {
            	stopNode(i);
                Thread.sleep(50);
            } catch (InterruptedException e) {
                LOG.error("Error in stopping node " + i);
            }
        }
    }
    
    public void stopNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping node " + id);
        }
        System.out.println("Stopping node " + id);
        try {
            Runtime.getRuntime().exec(workingDir + "/killNode.sh " + id);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void startNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting node " + id);
        }
        System.out.println("Starting node " + id);
        try {
        	node[id] = Runtime.getRuntime().exec(workingDir + "/startNode.sh " + id + " " + ipcDir);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    class LogWriter implements Runnable {

        @Override
        public void run() {
            byte[] buff = new byte[256];
            while (true) {
                for (int i = 0; i < numNode; ++i) {
                    if (node[i] != null) {
                        int r = 0;
                        InputStream stdout = node[i].getInputStream();
                        InputStream stderr = node[i].getErrorStream();
                        try {
                            while((r = stdout.read(buff)) != -1) {
                                consoleLog[i].write(buff, 0, r);
                                consoleLog[i].flush();
                            }
                            while((r = stderr.read(buff)) != -1) {
                                consoleLog[i].write(buff, 0, r);
                                consoleLog[i].flush();
                            }
                        } catch (IOException e) {
//                            LOG.debug("", e);
                        }
                    }
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    LOG.warn("", e);
                }
            }
        }
        
    }

    @Override
    public void runWorkload() {
        
    }

}
