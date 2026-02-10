package org.processmining.datadiscovery.estimators;

import java.util.Map;

/**
 * General interfaces for a classification/regression algorithm
 *
 */
public interface FunctionEstimator {

	/**
	 * Adds a new instance to the estimator.
	 * 
	 * @param attributes
	 * @param classObject
	 * @param weight
	 * @throws Exception
	 */
	public void addInstance(Map<String, Object> attributes, Object classObject, float weight) throws Exception; //TODO weight should be double as in WEKA

	/**
	 * Gets the estimation in terms of a {@link FunctionEstimation} for each
	 * class.
	 * 
	 * @param option
	 *            (may be NULL) for the underlying algorithm
	 * @return
	 * @throws Exception
	 */
	public Map<Object, FunctionEstimation> getFunctionEstimation(Object[] option) throws Exception;

	/**
	 * @param attributes
	 *            the instances attribute values
	 * @return the classification
	 * @throws Exception
	 */
	public Object classifyInstance(Map<String, Object> attributes) throws Exception;

	/**
	 * Evaluates the classification.
	 * 
	 * @return
	 */
	public double computeQualityMeasure();

	/**
	 * Gets the number of instances in the instances set.
	 * 
	 * @return <Integer> the number of instances.
	 */
	int getNumInstances();

	/**
	 * @return the sum of all weights of all instances
	 */
	double getSumOfWeights();

}