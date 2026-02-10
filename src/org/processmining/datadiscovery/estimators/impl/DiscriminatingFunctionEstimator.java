package org.processmining.datadiscovery.estimators.impl;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.AbstractFunctionEstimator;
import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;


/**
 * Function Estimator that utilizes separate Decision Trees for each involved
 * Transition. Resulting expressions describe the features that discriminate one
 * Transition's occurrences from all other Transitions.
 * 
 * @author SCandel (original class)
 * @author F. Mannhardt
 *
 */
public class DiscriminatingFunctionEstimator extends AbstractFunctionEstimator implements
		DecisionTreeBasedFunctionEstimator {

	private static final Boolean[] CLASS_OBJECTS = new Boolean[] { true, false };

	private final DecisionTreeFunctionEstimator[] estimators;
	private final Object[] outputValuesAsObjects;

	public DiscriminatingFunctionEstimator(Map<String, Type> map, Map<String, Set<String>> literalValues,
			Object[] outputValuesAsObjects, int capacity, String name) {
		estimators = new DecisionTreeFunctionEstimator[outputValuesAsObjects.length];
		for (int i = 0; i < outputValuesAsObjects.length; i++) {
			estimators[i] = new DecisionTreeFunctionEstimator(map, literalValues, CLASS_OBJECTS, name + " "
					+ outputValuesAsObjects[i].toString(), capacity);
		}
		this.outputValuesAsObjects = outputValuesAsObjects;
	}

	@Override
	public void addInstance(Map<String, Object> variableAssignment, Object outputValue, float weight) throws Exception {
		for (int i = 0; i < estimators.length; i++) {
			if (outputValuesAsObjects[i].equals(outputValue)) {
				estimators[i].addInstance(variableAssignment, true, weight);
			} else {
				estimators[i].addInstance(variableAssignment, false, weight);
			}
		}
	}

	public void saveInstances(File file) throws IOException {
		for (int i = 0; i < estimators.length; i++) {
			estimators[i].saveInstances(new File(outputValuesAsObjects[i]+file.getName()));
		}
	}

	@Override
	public Map<Object, FunctionEstimation> getFunctionEstimation(Object[] option) throws Exception {

		Map<Object, FunctionEstimation> retValue = new HashMap<>();

		for (int i = 0; i < estimators.length; i++) {
			DecisionTreeFunctionEstimator currentEstimator = estimators[i];
			FunctionEstimation pair = currentEstimator.getFunctionEstimation(option).get(true);
			if (pair != null) {
				retValue.put(outputValuesAsObjects[i], pair);
			}
		}

		return retValue;
	}
	
	@Override
	public Object classifyInstance(Map<String, Object> attributes) throws Exception {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public int getNumInstances() {
		return estimators[0].getNumInstances();
	}
	
	@Override
	public double getSumOfWeights() {
		return estimators[0].getSumOfWeights();
	}

	@Override
	public void setMinNumObj(int i) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setMinNumObj(i);
		}
	}

	@Override
	public void setUnpruned(boolean b) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setUnpruned(b);
		}
	}

	@Override
	public void setBinarySplit(boolean selected) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setBinarySplit(selected);
		}
	}
	
	@Override
	public void setCrossValidate(boolean validate) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setCrossValidate(validate);
		}
	}
	
	@Override
	public void setConfidenceFactor(float confidenceThreshold) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setConfidenceFactor(confidenceThreshold);
		}
	}

	@Override
	public void setNumFolds(int numFoldErrorPruning) {
		for (DecisionTreeFunctionEstimator estimator : estimators) {
			estimator.setNumFolds(numFoldErrorPruning);
		}
	}

	public double computeQualityMeasure() {
		return -1;
	}

	public String getQualityMeasureName() {
		return "F-Score";
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (int i = 0; i < estimators.length; i++) {
			sb.append(outputValuesAsObjects[i]);
			sb.append('\n');
			for (int j = 0; j < outputValuesAsObjects[i].toString().length(); j++)
				sb.append('=');
			sb.append('\n');
			sb.append(estimators[i]);
			sb.append('\n');
			sb.append('\n');
		}
		return sb.toString();
	}

}