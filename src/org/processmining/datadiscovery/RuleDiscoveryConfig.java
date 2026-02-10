package org.processmining.datadiscovery;

public class RuleDiscoveryConfig {
	
	private boolean isMinePrimeGuards = false;
	private boolean isMineDirectlyFollowingClasses = false;
	private boolean isUseWeights = true;
	
	public boolean isMinePrimeGuards() {
		return isMinePrimeGuards;
	}

	public void setMinePrimeGuards(boolean isMinePrimeGuards) {
		this.isMinePrimeGuards = isMinePrimeGuards;
	}

	public boolean isMineDirectlyFollowingClasses() {
		return isMineDirectlyFollowingClasses;
	}

	public void setMineDirectlyFollowingClasses(boolean isMineDirectlyFollowingClasses) {
		this.isMineDirectlyFollowingClasses = isMineDirectlyFollowingClasses;
	}

	public boolean isUseWeights() {
		return isUseWeights;
	}

	public void setUseWeights(boolean isUseWeights) {
		this.isUseWeights = isUseWeights;
	}

}