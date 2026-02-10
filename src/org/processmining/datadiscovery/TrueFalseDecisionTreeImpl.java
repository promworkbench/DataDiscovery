package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.impl.DiscriminatingFunctionEstimator;

public class TrueFalseDecisionTreeImpl extends AbstractDecisionTreeRuleDiscovery<RuleDiscovery.Rule> {

	public TrueFalseDecisionTreeImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog,
			int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public TrueFalseDecisionTreeImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog,
			Map<String, Type> attributeType, Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	public Rule discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException {
		DiscriminatingFunctionEstimator estimator = new DiscriminatingFunctionEstimator(attributeType, literalValues,
				classes.toArray(), getNumInstanceEstimate(), decisionPoint.toString());
		return doDiscoverDecisionTree(decisionPoint, classes, estimator);
	}

	protected Rule newRule(Object decisionPoint, FunctionEstimator estimator,
			Map<Object, FunctionEstimation> estimation) {
		return new RuleImpl(decisionPoint, estimation, estimator);
	}
	
}