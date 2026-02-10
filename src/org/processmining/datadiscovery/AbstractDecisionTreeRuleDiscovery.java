package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;

/**
 * Contract: Subclasses always create a
 * {@link DecisionTreeBasedFunctionEstimator}!
 *
 */
abstract class AbstractDecisionTreeRuleDiscovery<R extends RuleDiscovery.Rule>
		extends AbstractPetrinetDecisionRuleDiscovery<R, DecisionTreeConfig> {

	public AbstractDecisionTreeRuleDiscovery(DecisionTreeConfig config,
			Iterable<ProjectedTrace> projectedLog, int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public AbstractDecisionTreeRuleDiscovery(DecisionTreeConfig config,
			Iterable<ProjectedTrace> projectedLog, Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	protected void configureWithInstance(FunctionEstimator estimator) {
		if (!(estimator instanceof DecisionTreeBasedFunctionEstimator)) {
			throw new IllegalArgumentException(
					"Only to be used with a " + DecisionTreeBasedFunctionEstimator.class.getSimpleName());
		}
		DecisionTreeBasedFunctionEstimator decisionTreeEstimator = (DecisionTreeBasedFunctionEstimator) estimator;
		decisionTreeEstimator.setBinarySplit(config.isBinarySplit());
		decisionTreeEstimator.setCrossValidate(config.isCrossValidate());
		decisionTreeEstimator.setUnpruned(config.isUnpruned());
		decisionTreeEstimator.setConfidenceFactor(config.getConfidenceThreshold());

		// Each place gets its own minNumObj configuration
		int minNumObj = 2;
		if (config.isUseWeights()) {
			minNumObj = Math.max(minNumObj,
					(int) Math.floor((config.getMinPercentageObjectsOnLeaf() * decisionTreeEstimator.getSumOfWeights())));
		} else {
			minNumObj = Math.max(minNumObj,
					(int) Math.floor((config.getMinPercentageObjectsOnLeaf() * decisionTreeEstimator.getNumInstances())));
		}
		decisionTreeEstimator.setMinNumObj(minNumObj);
	}

}
