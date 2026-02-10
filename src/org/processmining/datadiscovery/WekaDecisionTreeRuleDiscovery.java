package org.processmining.datadiscovery;

import java.util.Set;

import org.processmining.datadiscovery.WekaDecisionTreeRuleDiscovery.TreeRule;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter.WekaNode;

import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.TreeTraverser;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.gui.treevisualizer.Node;

/**
 * Marker interface for all rule discovery classes, which return
 * {@link TreeRule}
 *
 */
public interface WekaDecisionTreeRuleDiscovery<R extends TreeRule> extends RuleDiscovery<R> {

	/**
	 * Rule derived by a Weka decision tree. This rule retains a reference to
	 * the Weka instances, classifier and evaluation. So keeping a reference to
	 * this object might lead to a high memory consumption.
	 */
	public interface TreeRule extends Rule {

		WekaNode getRootNode();

		TreeTraverser<WekaNode> treeTraverser();

		FluentIterable<WekaNode> preOrderTraversal();

		FluentIterable<WekaNode> postOrderTraversal();

		int treeDepth();

		int treeDepth(Node node);

		Instances getInstances();

		AbstractClassifier getClassifier();

		Evaluation getEvaluation();

		/**
		 * @return a bimap between the target class and the weka class index (used
		 *         in {@link Evaluation}
		 */
		BiMap<Object, Integer> getClassToIndex();

	}

	@Override
	public R discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException;

}
