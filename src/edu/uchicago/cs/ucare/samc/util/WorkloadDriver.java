package edu.uchicago.cs.ucare.samc.util;

public abstract class WorkloadDriver {
    
    protected int numNode;
    protected String workingDir;
    
    public SpecVerifier verifier;

    public WorkloadDriver(int numNode, String workingDir) {
        this.numNode = numNode;
        this.workingDir = workingDir;
    }
    
    public WorkloadDriver(int numNode, String workingDir, SpecVerifier verifier) {
        this.numNode = numNode;
        this.workingDir = workingDir;
        this.verifier = verifier;
    }
    public abstract void startNode(int id);
    public abstract void stopNode(int id);
    public abstract void startEnsemble();
    public abstract void stopEnsemble();
    public abstract void resetTest();
    
    public abstract void runWorkload();
    
    public void setVerifier(SpecVerifier verifier) {
        this.verifier = verifier;
    }

}
