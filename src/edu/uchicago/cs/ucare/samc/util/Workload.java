package edu.uchicago.cs.ucare.samc.util;

public abstract class Workload {
    
    protected boolean isFinished;
    protected WorkloadFeeder feeder;
    
    public Workload() {
        isFinished = false;
        this.feeder = null;
    }
    
    public Workload(WorkloadFeeder feeder) {
        isFinished = false;
        this.feeder = feeder;
    }
    
    public void finish() {
        if (!isFinished) {
            if (feeder != null) {
                feeder.notifyFinished();
                isFinished = true;
            }
        }
    }
    
    public abstract void reset();
    
    public abstract void run();
    
    public abstract void stop();
        
}
