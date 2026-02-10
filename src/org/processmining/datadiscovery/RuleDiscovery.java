package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.FunctionEstimation;

public interface RuleDiscovery<R extends RuleDiscovery.Rule> {

	/**
	 * Discovered rule on the occurrence of classes.
	 */
	public interface Rule {
		Object getDecisionPoint();

		Map<Object, FunctionEstimation> getRules();

		/**
		 * @param attributes
		 *            of the instance
		 * @return the output class or an array of output classes
		 */
		Object classify(Map<String, Object> attributes);
	}

	/**
	 * Discovers rules on the occurrence of the supplied classes on a so-called
	 * decision point.
	 * 
	 * @param decisionPoint
	 * @param classes
	 * @return
	 * @throws RuleDiscoveryException
	 */
	public R discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException;

}
