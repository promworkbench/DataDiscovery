package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.impl.OverlappingEstimatorPairwiseDecisionTrees;

public class OverlappingPairwiseDecisionTreeImpl extends AbstractDecisionTreeRuleDiscovery<RuleDiscovery.Rule> {

	public OverlappingPairwiseDecisionTreeImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog,
			int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public OverlappingPairwiseDecisionTreeImpl(DecisionTreeConfig config, Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog,
			Map<String, Type> attributeType, Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	public Rule discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException {
		ThreadPoolExecutor pool = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() / 2,
				Runtime.getRuntime().availableProcessors() / 2, 60, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>());

		try {
			DecisionTreeBasedFunctionEstimator estimator = new OverlappingEstimatorPairwiseDecisionTrees(attributeType,
					literalValues, classes.toArray(), getNumInstanceEstimate(), decisionPoint.toString(), pool);
			return doDiscoverDecisionTree(decisionPoint, classes, estimator);
		} finally {
			pool.shutdown();
		}
	}
	
	protected Rule newRule(Object decisionPoint, FunctionEstimator estimator,
			Map<Object, FunctionEstimation> estimation) {
		return new RuleImpl(decisionPoint, estimation, estimator);
	}

}