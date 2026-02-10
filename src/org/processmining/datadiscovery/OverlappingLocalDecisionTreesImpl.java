package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.impl.OverlappingEstimatorLocalDecisionTree;

public class OverlappingLocalDecisionTreesImpl extends AbstractDecisionTreeRuleDiscovery<RuleDiscovery.Rule> {

	private boolean isReduceMinLeafs = true;

	public OverlappingLocalDecisionTreesImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog, int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public OverlappingLocalDecisionTreesImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog,
			Map<String, Type> attributeType, Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	public Rule discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException {

		OverlappingEstimatorLocalDecisionTree estimator = new OverlappingEstimatorLocalDecisionTree(attributeType, literalValues,
				classes.toArray(), getNumInstanceEstimate(), decisionPoint.toString());
		estimator.setReduceMinLeafs(isReduceMinLeafs);
		
		return doDiscoverDecisionTree(decisionPoint, classes, estimator);
	}
	
	public boolean isReduceMinLeafs() {
		return isReduceMinLeafs;
	}

	public void setReduceMinLeafs(boolean isReduceMinLeafs) {
		this.isReduceMinLeafs = isReduceMinLeafs;
	}
	
	protected Rule newRule(Object decisionPoint, FunctionEstimator estimator,
			Map<Object, FunctionEstimation> estimation) {
		return new RuleImpl(decisionPoint, estimation, estimator);
	}

}
