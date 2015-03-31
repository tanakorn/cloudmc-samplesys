package mc;

import mc.transition.NodeCrashTransition;

public class FirstNodeCrashModelChecker extends SteadyStateInformedModelChecker {

    public FirstNodeCrashModelChecker(String interceptorName, String ackName, int maxId,
            String globalStatePathDir, EnsembleController zkController, WorkloadFeeder feeder) {
        super(interceptorName, ackName, maxId, globalStatePathDir, zkController, feeder);
    }
    
    @Override
    public void resetTest() {
        super.resetTest();
        modelChecking = new Worker(this);
    }
    
    class Worker extends Thread {
        
        ModelChecker mc;
        
        public Worker(ModelChecker mc) {
            this.mc = mc;
        }
        
        public void run() {
            log.info("Making first node crash");
            NodeCrashTransition t = new NodeCrashTransition(mc, 0);
            t.apply();
        }

    }

}
