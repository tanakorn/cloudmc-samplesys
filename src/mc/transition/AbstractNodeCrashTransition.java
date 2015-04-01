package mc.transition;

import java.util.LinkedList;

import edu.uchicago.cs.ucare.simc.server.ModelChecker;

public class AbstractNodeCrashTransition extends AbstractNodeOperationTransition {
    
    public AbstractNodeCrashTransition(ModelChecker checker) {
        super(checker);
    }

    @Override
    public boolean apply() {
        NodeCrashTransition t = getRealNodeOperationTransition();
        if (t == null) {
            return false;
        }
        id = t.getId();
        return t.apply();
    }

    @Override
    public int getTransitionId() {
        return 101;
    }
    
    @Override
    public boolean equals(Object o) {
        return o instanceof AbstractNodeCrashTransition;
    }
    
    @Override 
    public int hashCode() {
        return 101;
    }
    
    public NodeCrashTransition getRealNodeOperationTransition() {
        for (int i = 0; i < checker.numNode; ++i) {
            if (checker.isNodeOnline(i)) {
                return new NodeCrashTransition(checker, i);
            }
        }
        return null;
    }
    
    @Override
    public LinkedList<NodeOperationTransition> getAllRealNodeOperationTransitions(boolean[] onlineStatus) {
        LinkedList<NodeOperationTransition> result = new LinkedList<NodeOperationTransition>();
        for (int i = 0; i < onlineStatus.length; ++i) {
            if (onlineStatus[i]) {
                result.add(new NodeCrashTransition(checker, i));
            }
        }
        return result;
    }

    public String toString() {
        return "abstract_node_crash";
    }
    
}
