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
    	"-Delection.log.dir=%s/log/%d", "-Dlog4j.configuration=%s", "-Dsamc_enabled=true",
    	"edu.uchicago.cs.ucare.example.election.LeaderElectionMain", "%d", "%s/conf/config" };
    
    Process[] leaderElection;
    Thread consoleWriter;
    FileOutputStream[] consoleLog;
    
    public LeaderElectionEnsembleController(int numNode, String workingDir) {
        super(numNode, workingDir);
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
        ProcessBuilder builder = new ProcessBuilder();
        builder.environment().put("MC_CONFIG", workingDir + "/lemc.conf");
        builder.directory(new File(workingDir));
        for (int i = 0; i < numNode; ++i) {
            String[] cmd = Arrays.copyOf(CMD, CMD.length);
            cmd[3] = String.format(cmd[3], workingDir, i);
            cmd[4] = String.format(cmd[4], "le_log.properties");
            cmd[7] = String.format(cmd[7], i);
            cmd[8] = String.format(cmd[8], workingDir);
            try {
                LOG.debug("Starting node " + i);
                leaderElection[i] = builder.command(cmd).start();
//                Thread.sleep(300);
//            } catch (InterruptedException e) {
//                LOG.error("", e);
//                throw new RuntimeException(e);
            } catch (IOException e) {
                LOG.error("", e);
                throw new RuntimeException(e);
            }
        }
    }
    
    public void stopEnsemble() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping ensemble");
        }
        for (Process node : leaderElection) {
            node.destroy();
        }
        for (Process node : leaderElection) {
            try {
                int wait = node.waitFor();
            } catch (InterruptedException e) {
                LOG.error("", e);
            }
        }
    }
    
    public void stopNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Stopping node " + id);
        }
        leaderElection[id].destroy();
        try {
            leaderElection[id].waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void startNode(int id) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting node" + id);
        }
        ProcessBuilder builder = new ProcessBuilder();
        builder.environment().put("MC_CONFIG", workingDir + "/lemc.conf");
        builder.directory(new File(workingDir));
        String[] cmd = Arrays.copyOf(CMD, CMD.length);
        cmd[3] = String.format(cmd[3], workingDir, id);
        cmd[4] = String.format(cmd[4], "le_log.properties");
        cmd[6] = String.format(cmd[6], id);
        cmd[7] = String.format(cmd[7], workingDir);
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
