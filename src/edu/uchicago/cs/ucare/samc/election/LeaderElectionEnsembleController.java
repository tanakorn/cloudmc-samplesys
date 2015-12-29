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
    
    static final String[] CMD = { "java", "-cp", System.getenv("CLASSPATH"), 
    	"-Delection.log.dir=%s/log/%d", "-Dlog4j.configuration=%s",
    	"edu.uchicago.cs.ucare.example.election.LeaderElectionMain", "%d", "%s/conf/config", "%s" };
    
    String ipcDir;
    
    Process[] leaderElection;
    Thread consoleWriter;
    FileOutputStream[] consoleLog;
    
    public LeaderElectionEnsembleController(int numNode, String workingDir, String sIpcDir) {
        super(numNode, workingDir);
        ipcDir = sIpcDir;
        leaderElection = new Process[numNode];
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
                LOG.error("", e);
                throw new RuntimeException(e);
            }
        }
    }
    
    public void stopEnsemble() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping ensemble");
        }
        for (int i=0; i<numNode; i++) {
            stopNode(i);
        }
    }
    
    public void stopNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping node " + id);
        }
        leaderElection[id].destroyForcibly();
        try {
            leaderElection[id].waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void startNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting node " + id);
        }
        System.out.println("Starting node " + id);
        ProcessBuilder builder = new ProcessBuilder();
        builder.environment().put("MC_CONFIG", workingDir + "/lemc.conf");
        builder.directory(new File(workingDir));
        String[] cmd = Arrays.copyOf(CMD, CMD.length);
        cmd[3] = String.format(cmd[3], workingDir, id);
        cmd[4] = String.format(cmd[4], "le_log.properties");
        cmd[6] = String.format(cmd[6], id);
        cmd[7] = String.format(cmd[7], workingDir);
        cmd[8] = String.format(cmd[8], ipcDir);
        try {
            leaderElection[id] = builder.command(cmd).start();
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
                    if (leaderElection[i] != null) {
                        int r = 0;
                        InputStream stdout = leaderElection[i].getInputStream();
                        InputStream stderr = leaderElection[i].getErrorStream();
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
