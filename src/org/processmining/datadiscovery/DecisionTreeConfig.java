package org.processmining.datadiscovery;

public class DecisionTreeConfig extends RuleDiscoveryConfig {

	private boolean isBinarySplit = false;
	private float confidenceThreshold = 0.25f;
	private boolean isCrossValidate = false;
	private double minPercentageObjOnLeaf;
	private boolean isUnpruned = false;
	
	
	public boolean isBinarySplit() {
		return isBinarySplit;
	}

	public float getConfidenceThreshold() {
		return confidenceThreshold;
	}

	public boolean isCrossValidate() {
		return isCrossValidate;
	}

	public double getMinPercentageObjectsOnLeaf() {
		return minPercentageObjOnLeaf;
	}

	public boolean isUnpruned() {
		return isUnpruned;
	}

	public void setBinarySplit(boolean isBinarySplit) {
		this.isBinarySplit = isBinarySplit;
	}

	public void setConfidenceTreshold(float confidenceTreshold) {
		this.confidenceThreshold = confidenceTreshold;
	}

	public void setCrossValidate(boolean isCrossValidate) {
		this.isCrossValidate = isCrossValidate;
	}

	public void setMinPercentageObjectsOnLeaf(double minPercentageObjOnLeaf) {
		this.minPercentageObjOnLeaf = minPercentageObjOnLeaf;
	}

	public void setUnpruned(boolean isUnpruned) {
		this.isUnpruned = isUnpruned;
	}

}