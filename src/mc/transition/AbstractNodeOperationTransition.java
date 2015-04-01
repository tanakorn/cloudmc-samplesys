package mc.transition;

import java.util.LinkedList;

import edu.uchicago.cs.ucare.simc.server.ModelChecker;

public abstract class AbstractNodeOperationTransition extends NodeOperationTransition {

    protected ModelChecker checker;
    
    public AbstractNodeOperationTransition(ModelChecker checker) {
        id = -1;
        this.checker = checker;
    }

    public abstract NodeOperationTransition getRealNodeOperationTransition();
    public abstract LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus);

}
