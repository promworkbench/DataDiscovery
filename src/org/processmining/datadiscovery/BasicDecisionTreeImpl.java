package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.impl.DecisionTreeFunctionEstimator;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter.WekaNode;

import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.TreeTraverser;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;
import weka.gui.treevisualizer.Node;

public class BasicDecisionTreeImpl extends AbstractDecisionTreeRuleDiscovery<WekaDecisionTreeRuleDiscovery.TreeRule>
		implements WekaDecisionTreeRuleDiscovery<WekaDecisionTreeRuleDiscovery.TreeRule> {

	/**
	 * Rule derived by a Weka J48 decision tree. This rule retains a reference
	 * to the Weka instances. So keeping a reference to this object might lead
	 * to a high memory consumption.
	 *
	 */
	public static final class J48TreeRule extends AbstractDecisionRuleDiscovery.RuleImpl
			implements WekaDecisionTreeRuleDiscovery.TreeRule {

		private final WekaTreeClassificationAdapter treeClassificationAdapter;
		private final Instances instances;
		
		private final AbstractClassifier classifier;
		private final Evaluation evaluation;
		private BiMap<Object, Integer> classIndex;

		public J48TreeRule(Object decisionPoint, Map<Object, FunctionEstimation> estimation,
				DecisionTreeFunctionEstimator estimator) {
			super(decisionPoint, estimation, estimator);
			treeClassificationAdapter = estimator.getTreeClassificationAdapter();
			instances = estimator.getInstances();
			classifier = estimator.getClassifier();
			evaluation = estimator.getEvaluation();
			classIndex = estimator.getClassIndexMap();
		}

		public WekaNode getRootNode() {
			return treeClassificationAdapter.getRootNode();
		}

		public TreeTraverser<WekaNode> treeTraverser() {
			return treeClassificationAdapter.treeTraverser();
		}

		public int treeDepth() {
			return treeClassificationAdapter.treeDepth();
		}

		public int treeDepth(Node node) {
			return treeClassificationAdapter.treeDepth(node);
		}

		public FluentIterable<WekaNode> preOrderTraversal() {
			return treeClassificationAdapter.treeTraverser().preOrderTraversal(getRootNode());
		}

		public FluentIterable<WekaNode> postOrderTraversal() {
			return treeClassificationAdapter.treeTraverser().postOrderTraversal(getRootNode());
		}

		public Instances getInstances() {
			return instances;
		}

		public AbstractClassifier getClassifier() {
			return classifier;
		}

		public Evaluation getEvaluation() {
			return evaluation;
		}
		
		public BiMap<Object, Integer> getClassToIndex() {
			return classIndex;
		}

	}

	public enum NoLeafAction {
		TREAT_AS_TRUE, TREAT_AS_FALSE
	}

	private NoLeafAction noLeafAction = NoLeafAction.TREAT_AS_TRUE;

	public BasicDecisionTreeImpl(DecisionTreeConfig config,
			Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog, int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public BasicDecisionTreeImpl(DecisionTreeConfig config,
			Iterable<org.processmining.datadiscovery.ProjectedTrace> projectedLog, Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	public TreeRule discover(Object decisionPoint, Set<? extends Object> classes) throws RuleDiscoveryException {

		DecisionTreeFunctionEstimator estimator = new DecisionTreeFunctionEstimator(attributeType, literalValues,
				classes.toArray(), decisionPoint.toString(), getNumInstanceEstimate());
		
		estimator.setTreatNoLeafAsFalse(noLeafAction == NoLeafAction.TREAT_AS_FALSE);
		
		return doDiscoverDecisionTree(decisionPoint, classes, estimator);
	}

	public NoLeafAction getNoLeafAction() {
		return noLeafAction;
	}

	public void setNoLeafAction(NoLeafAction noLeafAction) {
		this.noLeafAction = noLeafAction;
	}

	protected TreeRule newRule(Object decisionPoint, FunctionEstimator estimator,
			Map<Object, FunctionEstimation> estimation) {
		return new J48TreeRule(decisionPoint, estimation, (DecisionTreeFunctionEstimator) estimator);
	}

}