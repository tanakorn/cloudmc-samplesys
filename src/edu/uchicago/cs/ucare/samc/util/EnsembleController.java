package edu.uchicago.cs.ucare.samc.util;

public abstract class EnsembleController {
    
    protected int numNode;
    protected String workingDir;

    public EnsembleController(int numNode, String workingDir) {
        this.numNode = numNode;
        this.workingDir = workingDir;
    }
    
    public abstract void startNode(int id);
    public abstract void stopNode(int id);
    public abstract void startEnsemble();
    public abstract void stopEnsemble();
    public abstract void resetTest();

}
