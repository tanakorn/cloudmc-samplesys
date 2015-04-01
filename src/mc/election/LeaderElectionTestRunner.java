package mc.election;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.LinkedList;
import java.util.Properties;

import mc.ModelChecker;
import mc.SpecVerifier;
import mc.Workload;
import mc.WorkloadFeeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.simc.ModelCheckingServer;

public class LeaderElectionTestRunner {
    
    final static Logger LOG = LoggerFactory.getLogger(LeaderElectionTestRunner.class);
    
    static WorkloadFeeder feeder;
    
    public static void main(String[] argv) throws IOException {
        String testRunnerConf = null;
        if (argv.length == 0) {
            System.err.println("Please specify test config file");
            System.exit(1);
        }
        boolean isPasuedEveryTest = false;
        for (String param : argv) {
            if (param.equals("-p")) {
                isPasuedEveryTest = true;
            } else {
                testRunnerConf = param;
            }
        }
        Properties testRunnerProp = new Properties();
        FileInputStream configInputStream = new FileInputStream(testRunnerConf);
        testRunnerProp.load(configInputStream);
        configInputStream.close();
        String workingDir = testRunnerProp.getProperty("working_dir");
        int numNode = Integer.parseInt(testRunnerProp.getProperty("num_node"));
        LeaderElectionEnsembleController leaderElectionontroller = 
                new LeaderElectionEnsembleController(numNode, workingDir);
        ModelChecker checker = createLeaderElectionModelCheckerFromConf(workingDir + "/mc.conf", workingDir, leaderElectionontroller);
        startExploreTesting(checker, numNode, workingDir, leaderElectionontroller, isPasuedEveryTest);
    }
    
    protected static ModelChecker createLeaderElectionModelCheckerFromConf(String confFile, 
            String workingDir, LeaderElectionEnsembleController leaderElectionController) {
        ModelCheckingServer modelChecker = null;
        try {
            Properties prop = new Properties();
            FileInputStream configInputStream = new FileInputStream(confFile);
            prop.load(configInputStream);
            configInputStream.close();
            String interceptorName = prop.getProperty("mc_name");
            int numNode = Integer.parseInt(prop.getProperty("num_node"));
            String testRecordDir = prop.getProperty("test_record_dir");
            String traversalRecordDir = prop.getProperty("traversal_record_dir");
            String strategy = prop.getProperty("exploring_strategy", "dfs");
            int numCrash = Integer.parseInt(prop.getProperty("num_crash"));
            int numReboot = Integer.parseInt(prop.getProperty("num_reboot"));
            String ackName = "Ack";
            LinkedList<SpecVerifier> specVerifiers = new LinkedList<SpecVerifier>();
            specVerifiers.add(new LeaderElectionVerifier(workingDir, numNode));
            feeder = new WorkloadFeeder(new LinkedList<Workload>(), specVerifiers);
            LOG.info("State exploration strategy is " + strategy);
            modelChecker = new LeaderElectionSemanticAwareModelChecker(interceptorName, ackName, numNode,
            		numCrash, numReboot, testRecordDir, traversalRecordDir, workingDir, leaderElectionController, feeder);
            ModelCheckingServer interceptorStub = (ModelCheckingServer) 
                    UnicastRemoteObject.exportObject(modelChecker, 0);
            Registry r = LocateRegistry.getRegistry();
            r.rebind(interceptorName, interceptorStub);
//            r.rebind(interceptorName + "SteadyState", interceptorStub);
//            r.rebind(interceptorName + "LeaderElectGlobalStateRecorder", interceptorStub);
//            r.rebind(interceptorName + "LeaderElectTestIdRecorder", interceptorStub);
            
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (ModelChecker) modelChecker;
    }
    
    protected static void startExploreTesting(ModelChecker checker, int numNode, String workingDir, 
            LeaderElectionEnsembleController zkController, boolean isPausedEveryTest) throws IOException {
        File gspathDir = new File(workingDir + "/record");
        int testNum = gspathDir.list().length + 1;
        File finishedFlag = new File(workingDir + "/state/.finished");
        File waitingFlag = new File(workingDir + "/state/.waiting");
        try {
            for (; !finishedFlag.exists(); ++testNum) {
                waitingFlag.delete();
                checker.setTestId(testNum);
                Process reset = Runtime.getRuntime().exec("resettest " + numNode + 
                        " " + workingDir);
                reset.waitFor();
                /*
                Process setTestId = Runtime.getRuntime().exec("setzkmc_testid " + 
                        testNum + " " + workingDir);
                setTestId.waitFor();
                */
                zkController.resetTest();
//                zkController.startEnsemble();
                checker.runEnsemble();
                feeder.runAll();
                while (!waitingFlag.exists()) {
                    Thread.sleep(30);
                }
//                zkController.stopEnsemble();
                checker.stopEnsemble();
                if (isPausedEveryTest) {
                    System.out.print("enter to continue");
                    System.in.read();
                }
            }
            System.exit(0);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
