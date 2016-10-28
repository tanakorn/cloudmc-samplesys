package edu.uchicago.cs.ucare.samc.util;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public class LocalState implements Serializable {
	
	public static final String NODE_ID = "nodeId";
	
	protected Map<String, Serializable> keyValuePairs;
	
	public LocalState(){
		keyValuePairs = new HashMap<String, Serializable>();
	}
	
	public LocalState(int nodeId){
		keyValuePairs = new HashMap<String, Serializable>();
		keyValuePairs.put(NODE_ID, nodeId);
	}
	
	public void addKeyValue(String key, Serializable value) {
        keyValuePairs.put(key, value);
    }

    public Object getValue(String key) {
        return keyValuePairs.get(key);
    }
}
