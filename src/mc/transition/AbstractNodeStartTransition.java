package mc.transition;

import java.util.LinkedList;

import edu.uchicago.cs.ucare.simc.server.ModelChecker;

public class AbstractNodeStartTransition extends AbstractNodeOperationTransition {
    
    public AbstractNodeStartTransition(ModelChecker checker) {
        super(checker);
    }

    @Override
    public boolean apply() {
        NodeOperationTransition t = getRealNodeOperationTransition();
        if (t == null) {
            return false;
        }
        id = t.getId();
        return t.apply();
    }

    @Override
    public int getTransitionId() {
        return 112;
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractNodeStartTransition;
    }
    
    @Override 
    public int hashCode() {
        return 112;
    }
    
    @Override
    public NodeStartTransition getRealNodeOperationTransition() {
        for (int i = 0; i < checker.numNode; ++i) {
            if (!checker.isNodeOnline(i)) {
                return new NodeStartTransition(checker, i);
            }
        }
        return null;
    }
    
    @Override
    public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
        LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
        for (int i = 0; i < onlineStatus.length; ++i) {
            if (!onlineStatus[i]) {
                result.add(new NodeStartTransition(checker, i));
            }
        }
        return result;
    }

    public String toString() {
        return "abstract_node_start";
    }
    
}