package org.processmining.datadiscovery.model;

import java.util.Map;

import org.processmining.datadiscovery.estimators.FunctionEstimation;

/**
 * The discovered rules (+quality measure) for a decision point.
 * 
 * @author F. Mannhardt
 *
 */
public interface DecisionPointResult {

	double getQualityMeasure();

	Map<Object, FunctionEstimation> getEstimatedGuards();

}