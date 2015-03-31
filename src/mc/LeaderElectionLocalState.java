package mc;

import java.io.Serializable;
import java.util.Map;

public class LeaderElectionLocalState extends LocalState {
	
	int leader;
	int role;
	Map<Integer, Integer> electionTable;

	public LeaderElectionLocalState() {
		super();
		leader = -1;
		role = -1;
		electionTable = null;
	}

	public LeaderElectionLocalState(int leader, int role, Map<Integer, Integer> electionTable) {
		super();
		this.leader = leader;
		this.role = role;
		this.electionTable = electionTable;
	}

	public int getLeader() {
		return leader;
	}

	public void setLeader(int leader) {
		this.leader = leader;
	}

	public int getRole() {
		return role;
	}

	public void setRole(int role) {
		this.role = role;
	}

	public Map<Integer, Integer> getElectionTable() {
		return electionTable;
	}

	public void setElectionTable(Map<Integer, Integer> electionTable) {
		this.electionTable = electionTable;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((electionTable == null) ? 0 : electionTable.hashCode());
		result = prime * result + leader;
		result = prime * result + role;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LeaderElectionLocalState other = (LeaderElectionLocalState) obj;
		if (electionTable == null) {
			if (other.electionTable != null)
				return false;
		} else if (!electionTable.equals(other.electionTable))
			return false;
		if (leader != other.leader)
			return false;
		if (role != other.role)
			return false;
		return true;
	}
	
}
