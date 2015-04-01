package edu.uchicago.cs.ucare.simc.transition;

import java.util.LinkedList;

import edu.uchicago.cs.ucare.simc.server.ModelCheckingServerAbstract;

public abstract class AbstractNodeOperationTransition extends NodeOperationTransition {

    protected ModelCheckingServerAbstract checker;
    
    public AbstractNodeOperationTransition(ModelCheckingServerAbstract checker) {
        id = -1;
        this.checker = checker;
    }

    public abstract NodeOperationTransition getRealNodeOperationTransition();
    public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus);

}
