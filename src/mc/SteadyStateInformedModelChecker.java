package mc;

import java.rmi.RemoteException;
import java.util.LinkedList;

import mc.transition.PacketSendTransition;
import mc.transition.Transition;

public abstract class SteadyStateInformedModelChecker extends ModelChecker implements SteadyStateListener {
    
    protected LinkedList<Transition> currentEnabledTransitions = new LinkedList<Transition>();
    protected boolean[] isNodeSteady;
    protected Boolean isStarted;
    protected Thread modelChecking;
    protected int[] numPacketSentToId;

    public SteadyStateInformedModelChecker(String interceptorName, String ackName, 
            int numNode, String globalStatePathDir, EnsembleController zkController, WorkloadFeeder feeder) {
        super(interceptorName, ackName, numNode, globalStatePathDir, zkController, feeder);
    }
    
    @Override
    public boolean waitPacket(int toId) throws RemoteException {
        while (isNodeOnline(toId)) {
            if (isSystemSteady() && !isThereOutstandingPacketTransition()) {
                return false;
            }
            synchronized (numPacketSentToId) {
                if (numPacketSentToId[toId] > 0) {
                    numPacketSentToId[toId]--;
                    return true;
                }
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                log.error("", e);
            }
        }
        return false;
    }
    
    public boolean isThereOutstandingPacketTransition() {
        boolean isThereProcessingEnabledPacket = false;
        for (Transition t : currentEnabledTransitions) {
            if (t instanceof PacketSendTransition && !((PacketSendTransition) t).getPacket().isObsolete()) {
                isThereProcessingEnabledPacket = true;
                break;
            }
        }
        return numPacketInSenderReceiverQueue() != 0 || isThereProcessingEnabledPacket;
    }
    
    @Override
    public boolean commit(InterceptPacket packet) {
        if (super.commit(packet)) {
            synchronized (numPacketSentToId) {
                numPacketSentToId[packet.getToId()]++;
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean isSystemSteady() {
        for (int i = 0; i < numNode; ++i) {
            if (!isNodeSteady(i)) {
//                log.info("korn node " + i + " is not steady");
                return false;
            }
        }
        return true;
    }

    @Override
    public void informSteadyState(int id, int runningState) throws RemoteException {
        setNodeSteady(id, true);
        if (log.isDebugEnabled()) {
            log.debug("Node " + id + " is in steady state");
        }
        synchronized (isStarted) {
            if (!isStarted && isSystemSteady()) {
                isStarted = true;
                initGlobalState();
                log.info("First system steady state, start model checker thread");
                modelChecking.start();
            }
        }
    }
    
    @Override
    public void informActiveState(int id) throws RemoteException {
        setNodeSteady(id, false);
    }
    
    protected int numPacketInSenderReceiverQueue() {
        int num = 0;
        for (int i = 0; i < numNode; ++i) {
            for (int j = 0; j < numNode; ++j) {
                num += senderReceiverQueues[i][j].size();
            }
        }
        return num;
    }
    
    protected void setNodeSteady(int id, boolean isSteady) {
        isNodeSteady[id] = isSteady;
        
    }

    protected boolean isNodeSteady(long id) {
        return isNodeSteady[(int) id] || !isNodeOnline[(int) id];
    }
    
    protected void waitNodeSteady(int id) throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Waiting node " + id + " to be in steady state");
        }
        int waitTick = 10;
        int i = 0;
        while (!isNodeSteady(id) && i++ < waitTick) {
//        while (!isNodeSteady(id)) {
            Thread.sleep(50);
        }
        if (i >= waitTick) {
            log.warn("Steady state for node " + id + " triggered by timeout");
        }
        setNodeSteady(id, true);
    }
    
    @Override
    public boolean commitAndWait(InterceptPacket packet) throws InterruptedException {
        setNodeSteady(packet.getToId(), false);
        if (super.commitAndWait(packet)) {
            waitNodeSteady(packet.getToId());
            return true;
        } else {
            setNodeSteady(packet.getToId(), true);
            return false;
        }
    }
    
    @Override
    public void resetTest() {
        super.resetTest();
        isNodeSteady = new boolean[numNode];
        isStarted = false;
        numPacketSentToId = new int[numNode];
    }

    @Override
    public boolean runNode(int id) {
        if (super.runNode(id)) {
            setNodeSteady(id, false);
            try {
//                waitNodeSteady(id);
                // I'm sorry for this, waitNodeSteady now means wait for timeout 200 ms
                if (log.isDebugEnabled()) {
                    log.debug("Waiting node " + id + " to be in real steady state");
                }
                int waitTick = 60;
                int i = 0;
                while (!isNodeSteady(id) && i++ < waitTick) {
//                while (!isNodeSteady(id)) {
                    Thread.sleep(50);
                }
                if (i >= waitTick) {
                    log.warn("Steady state for node " + id + " triggered by timeout");
                }
                setNodeSteady(id, true);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }
    
    abstract protected static class Explorer extends Thread {
        
        protected SteadyStateInformedModelChecker checker;
        
        public Explorer(SteadyStateInformedModelChecker checker) {
            this.checker = checker;
        }
        
    }
    
}
