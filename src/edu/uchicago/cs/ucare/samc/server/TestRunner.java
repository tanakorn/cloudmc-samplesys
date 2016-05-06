package edu.uchicago.cs.ucare.samc.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uchicago.cs.ucare.samc.server.FileWatcher;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServer;
import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;
import edu.uchicago.cs.ucare.samc.util.WorkloadDriver;
import edu.uchicago.cs.ucare.samc.util.SpecVerifier;

public class TestRunner {
    
    final static Logger LOG = LoggerFactory.getLogger(TestRunner.class);
    
    static WorkloadDriver ensembleController;
    
    public static void main(String[] argv) throws IOException, ClassNotFoundException, 
            NoSuchMethodException, SecurityException, InstantiationException, 
            IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	String testRunnerConf = null;
        if (argv.length == 0) {
            System.err.println("Please specify test config file");
            System.exit(1);
        }
        boolean isPausedEveryTest = false;
        for (String param : argv) {
            if (param.equals("-p")) {
                isPausedEveryTest = true;
            } else {
                testRunnerConf = param;
            }
        }
        
        prepareModelChecker(testRunnerConf, isPausedEveryTest);
        
    }
    
    public static void prepareModelChecker(String testRunnerConf, boolean isPausedEveryTest){
    	try{
	    	Properties testRunnerProp = new Properties();
	        FileInputStream configInputStream = new FileInputStream(testRunnerConf);
	        testRunnerProp.load(configInputStream);
	        configInputStream.close();
	        
	        String workingDir = testRunnerProp.getProperty("working_dir");
	        int numNode = Integer.parseInt(testRunnerProp.getProperty("num_node"));
	        String workload = testRunnerProp.getProperty("workload_driver");
	        boolean useIPC = Integer.parseInt(testRunnerProp.getProperty("use_ipc")) == 1;
	        String ipcDir = "";
	        if(useIPC){
	        	ipcDir = testRunnerProp.getProperty("ipc_dir");
	        }
	        @SuppressWarnings("unchecked")
	        Class<? extends WorkloadDriver> ensembleControllerClass = (Class<? extends WorkloadDriver>) Class.forName(workload);
	        Constructor<? extends WorkloadDriver> ensembleControllerConstructor = ensembleControllerClass.getConstructor(Integer.TYPE, String.class, String.class);
	        ensembleController = ensembleControllerConstructor.newInstance(numNode, workingDir, ipcDir);
	        ModelCheckingServerAbstract checker = createModelCheckerFromConf(workingDir + "/target-sys.conf", workingDir, ensembleController, ipcDir);
	        if(useIPC){
	        	// activate Directory Watcher
	            Thread dirWatcher;
	            
	        	dirWatcher = new Thread(new FileWatcher(ipcDir, checker));
	        	dirWatcher.start();
	        	
	        	Thread.sleep(500);
	        }
	        startExploreTesting(checker, numNode, workingDir, ensembleController, isPausedEveryTest);
    	} catch (Exception e){
    		e.printStackTrace();
    	}
    }
    
    @SuppressWarnings("unchecked")
	protected static ModelCheckingServerAbstract createModelCheckerFromConf(String confFile, 
            String workingDir, WorkloadDriver ensembleController, String ipcDir) throws ClassNotFoundException, 
            NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, 
            IllegalArgumentException, InvocationTargetException {
        ModelCheckingServerAbstract modelCheckingServerAbstract = null;
        try {
            Properties prop = new Properties();
            FileInputStream configInputStream = new FileInputStream(confFile);
            prop.load(configInputStream);
            configInputStream.close();
            String interceptorName = prop.getProperty("mc_name");
            int numNode = Integer.parseInt(prop.getProperty("num_node"));
            String testRecordDir = prop.getProperty("test_record_dir");
            String traversalRecordDir = prop.getProperty("traversal_record_dir");
            String strategy = prop.getProperty("exploring_strategy");
            int numCrash = Integer.parseInt(prop.getProperty("num_crash"));
            int numReboot = Integer.parseInt(prop.getProperty("num_reboot"));
            String verifierName = prop.getProperty("verifier");
            String ackName = "Ack";
            Class<? extends SpecVerifier> verifierClass = (Class<? extends SpecVerifier>) Class.forName(verifierName);
            Constructor<? extends SpecVerifier> verifierConstructor = verifierClass.getConstructor();
            SpecVerifier verifier = verifierConstructor.newInstance();
            ensembleController.setVerifier(verifier);
            LOG.info("State exploration strategy is " + strategy);
            Class<? extends ModelCheckingServerAbstract> modelCheckerClass = (Class<? extends ModelCheckingServerAbstract>) Class.forName(strategy);
            
            if(ProgrammableModelChecker.class.isAssignableFrom(modelCheckerClass)){
            	String programFileName = prop.getProperty("program");
                if (programFileName == null) {
                    throw new RuntimeException("No program file specified");
                }
                LOG.info("Inspect potential bug in: " + programFileName);
                File program = new File(programFileName);
                modelCheckingServerAbstract = new ProgrammableModelChecker(interceptorName, ackName, numNode, 
                        testRecordDir, program, workingDir, ensembleController);
            } else {
                Constructor<? extends ModelCheckingServerAbstract> modelCheckerConstructor = modelCheckerClass.getConstructor(
                		String.class, String.class, Integer.TYPE, Integer.TYPE, Integer.TYPE, String.class, String.class, 
                        String.class, WorkloadDriver.class, String.class);
                modelCheckingServerAbstract = modelCheckerConstructor.newInstance(interceptorName, ackName, 
                        numNode, numCrash, numReboot, testRecordDir, traversalRecordDir, workingDir,
                        ensembleController, ipcDir);
            }
            
            verifier.modelCheckingServer = modelCheckingServerAbstract;
            if(ipcDir == ""){
	            ModelCheckingServer interceptorStub = (ModelCheckingServer) 
	                    UnicastRemoteObject.exportObject(modelCheckingServerAbstract, 0);
	            Registry r = LocateRegistry.getRegistry();
	            r.rebind(interceptorName, interceptorStub);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (ModelCheckingServerAbstract) modelCheckingServerAbstract;
    }
    
    protected static void startExploreTesting(final ModelCheckingServerAbstract checker, int numNode, String workingDir,
            WorkloadDriver zkController, boolean isPausedEveryTest) throws IOException {
        File gspathDir = new File(workingDir + "/record");
        int testNum = gspathDir.list().length + 1;
        File finishedFlag = new File(workingDir + "/state/.finished");
        File waitingFlag = new File(workingDir + "/state/.waiting");
        try {

        	Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                	checker.stopEnsemble();
                }
            });
        	
            for (; !finishedFlag.exists(); ++testNum) {
                waitingFlag.delete();
                checker.setTestId(testNum);
                Process reset = Runtime.getRuntime().exec("resettest " + numNode + 
                        " " + workingDir);
                reset.waitFor();
                zkController.resetTest();
                checker.runEnsemble();
                ensembleController.runWorkload();
                checker.waitOnSteadyStatesByTimeout(); // wait on first steady state timeout
                while (!waitingFlag.exists()) {
                    Thread.sleep(30);
                }
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
