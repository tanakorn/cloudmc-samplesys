package edu.uchicago.cs.ucare.samc.transition;

import java.util.LinkedList;

import edu.uchicago.cs.ucare.samc.server.ModelCheckingServerAbstract;

public abstract class AbstractNodeOperationTransition extends NodeOperationTransition {

    protected ModelCheckingServerAbstract checker;
    
    public AbstractNodeOperationTransition(ModelCheckingServerAbstract checker) {
        id = -1;
        this.checker = checker;
    }

    public abstract NodeOperationTransition getRealNodeOperationTransition();
    public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus);

}
