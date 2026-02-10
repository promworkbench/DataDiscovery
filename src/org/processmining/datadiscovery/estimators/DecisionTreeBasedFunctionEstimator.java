package org.processmining.datadiscovery.estimators;

public interface DecisionTreeBasedFunctionEstimator extends FunctionEstimator {

	/**
	 * Sets the minimum number of instances per leaf option for the weka tree.
	 * 
	 * @param minNumInstancePerLeaf
	 */
	void setMinNumObj(int minNumObj);

	/**
	 * Sets the unpruned option for the weka tree.
	 * 
	 * @param isUnpruned
	 *            , true to enable unpruned.
	 */
	void setUnpruned(boolean isUnpruned);

	/**
	 * Sets the binary split option for the weka J48 tree.
	 * 
	 * @param binarySplit
	 *            Boolean. True to enable binary split.
	 */
	void setBinarySplit(boolean isBinary);

	/**
	 * @param validate
	 *            whether to do cross-validation
	 */
	void setCrossValidate(boolean validate);

	/**
	 * Sets the confidence threshold option for the weka tree.
	 * 
	 * @param confidenceThreshold
	 */
	void setConfidenceFactor(float confidenceThreshold);

	/**
	 * Sets the number of folds for error pruning option for the weka tree.
	 * 
	 * @param numFoldErrorPruning
	 */
	void setNumFolds(int numFoldErrorPruning);

}
