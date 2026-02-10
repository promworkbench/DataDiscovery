package org.processmining.datadiscovery.estimators.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.processmining.datadiscovery.estimators.AbstractFunctionEstimator;
import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter.WekaCondition;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter.WekaLeafNode;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.expression.VariableProvider;
import org.processmining.datapetrinets.expression.syntax.ParseException;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class OverlappingEstimatorLocalDecisionTree extends AbstractFunctionEstimator
		implements DecisionTreeBasedFunctionEstimator {

	// Weka does not work with a setting less than 2
	private static final int MIN_INSTANCES_AT_LEAF = 2;

	public static class LeafProcessor {

		private final Object predictedClassObject;
		private final Integer predictedClassIndex;
		private final List<Instance> instancesAtLeaf;

		public LeafProcessor(WekaLeafNode node, DecisionTreeFunctionEstimator normalEstimator) {
			this.predictedClassObject = normalEstimator.classMapping.get(node.getClassName());
			this.predictedClassIndex = normalEstimator.classIndexMap.get(predictedClassObject);
			this.instancesAtLeaf = new ArrayList<>((int) node.getInstanceCount());

			for (Instance instance : normalEstimator.getInstances()) {
				boolean fulfills = true;
				for (WekaCondition c : node.getConditions()) {
					if (!fulfillsCondition(instance, c)) {
						fulfills = false;
						break;
					}
				}
				if (fulfills) {
					instancesAtLeaf.add(instance);
				}
			}
		}

		private static boolean fulfillsCondition(Instance instance, WekaCondition condition) {
			Instances dataset = instance.dataset();
			Attribute attribute = dataset.attribute(condition.getAttributeName());
			double value = instance.value(attribute.index());
			double instanceValue;
			if (!instance.isMissing(attribute)) {
				if (attribute.isNominal()) {
					instanceValue = attribute.indexOfValue(condition.getValue());
				} else {
					instanceValue = Double.valueOf(condition.getValue());
				}

				switch (condition.getOperator()) {
					case "=" :
						return Double.compare(value, instanceValue) == 0;
					case "!=" :
						return Double.compare(value, instanceValue) != 0;
					case "<" :
						return Double.compare(value, instanceValue) < 0;
					case ">" :
						return Double.compare(value, instanceValue) > 0;
					case "<=" :
						return Double.compare(value, instanceValue) <= 0;
					case ">=" :
						return Double.compare(value, instanceValue) >= 0;
				}

				throw new IllegalArgumentException("Operator unkown in: " + condition);

			} else {
				return false;
			}
		}

		public List<Instance> getInstancesAtLeaf() {
			return instancesAtLeaf;
		}

		public Integer getPredictedClassIndex() {
			return predictedClassIndex;
		}

	}

	private final Object[] outputClasses;
	private final String decisionPointName;
	private final DecisionTreeFunctionEstimator normalEstimator;
	private final Map<WekaLeafNode, AbstractClassifier> additionalClassifiers = new HashMap<>();

	private final Map<String, Type> attributeType;
	private final Map<String, Set<String>> literalValues;
	private List<WekaLeafNode> manipulatedLeafs;

	private boolean isReduceMinLeafs = true;
	private boolean alwaysMergeExistingLeafs = false;
	private double mergeWrongInstanceRatio = 0.05d;

	public OverlappingEstimatorLocalDecisionTree(Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses, int capacity, String name) {
		super();
		this.attributeType = attributeType;
		this.outputClasses = outputClasses;
		this.literalValues = literalValues;
		this.decisionPointName = name;
		this.normalEstimator = new DecisionTreeFunctionEstimator(attributeType, literalValues, outputClasses, name,
				capacity);
		this.manipulatedLeafs = new ArrayList<WekaTreeClassificationAdapter.WekaLeafNode>();
	}

	public void addInstance(Map<String, Object> variableAssignment, Object outputValue, float weight) throws Exception {
		normalEstimator.addInstance(variableAssignment, outputValue, weight);
	}

	public void saveInstances(File file) throws IOException {
		normalEstimator.saveInstances(file);
	}

	public Map<Object, FunctionEstimation> getFunctionEstimation(Object[] option) throws Exception {

		manipulatedLeafs.clear();

		normalEstimator.getFunctionEstimation(option);

		if (outputClasses.length > 1) {

			J48 tree = (J48) normalEstimator.getClassifier();

			WekaTreeClassificationAdapter wekaJ48Adapter = new WekaTreeClassificationAdapter(tree,
					normalEstimator.getInstances(), attributeType);

			ImmutableList<WekaLeafNode> leavesSortedByWrongInstances = wekaJ48Adapter.traverseLeafNodes()
					.toSortedList(new Comparator<WekaLeafNode>() {

						public int compare(WekaLeafNode o1, WekaLeafNode o2) {
							return Double.compare(o2.getWrongInstanceCount(), o1.getWrongInstanceCount());
						}

					});

			for (WekaLeafNode leafNode : leavesSortedByWrongInstances) {

				if (isCandidateLeaf(leafNode)) {

					// Add original leaf node
					manipulatedLeafs.add(leafNode);

					final LeafProcessor leafProcessor = new LeafProcessor(leafNode, normalEstimator);
					final DecisionTreeFunctionEstimator overlappingClassesEstimator = new DecisionTreeFunctionEstimator(
							attributeType, literalValues, outputClasses, decisionPointName,
							leafProcessor.getInstancesAtLeaf().size());

					overlappingClassesEstimator.setBinarySplit(normalEstimator.isBinarySplit());
					overlappingClassesEstimator.setNumFolds(normalEstimator.getNumFoldErrorPruning());
					overlappingClassesEstimator.setUnpruned(normalEstimator.isUnpruned());
					overlappingClassesEstimator.setCrossValidate(normalEstimator.isCrossValidate());

					boolean hasWeights = false;
					// Build new data set only retaining the wrongly classified instances
					for (Instance instance : leafProcessor.getInstancesAtLeaf()) {
						Integer realClassIndex = (int) instance.value(instance.classIndex());
						Object realClass = normalEstimator.classIndexMap.inverse().get(realClassIndex);
						if (realClassIndex != leafProcessor.getPredictedClassIndex()) {
							if (instance.weight() > 1) {
								hasWeights = true;
							}
							overlappingClassesEstimator.addWekaInstance(instance, realClass, (float) instance.weight());
						}
					}

					int originalMinNumObjs = normalEstimator.getMinNumInstancePerLeaf();
					int newMinNumObjs = originalMinNumObjs;
					if (isReduceMinLeafs()) {
						if (hasWeights) {
							double originalMinPercentageLeafs = (originalMinNumObjs
									/ normalEstimator.getInstances().sumOfWeights());
							newMinNumObjs = Math.max(MIN_INSTANCES_AT_LEAF, (int) (Math
									.ceil(originalMinPercentageLeafs * overlappingClassesEstimator.getInstances().sumOfWeights())));
						} else {
							double originalMinPercentageLeafs = (originalMinNumObjs
									/ (double) normalEstimator.getNumInstances());
							newMinNumObjs = Math.max(MIN_INSTANCES_AT_LEAF, (int) (Math
									.ceil(originalMinPercentageLeafs * overlappingClassesEstimator.getNumInstances())));
						}
					}
					overlappingClassesEstimator.setMinNumObj(newMinNumObjs);
					if (overlappingClassesEstimator.isCrossValidate()
							&& newMinNumObjs < overlappingClassesEstimator.getNumFoldCrossValidation()) {
						continue; // Don't try to find a decision tree as cross validation would not work
					}

					final AbstractClassifier overlappingTree = overlappingClassesEstimator.createClassifier(null,
							false);
					final WekaTreeClassificationAdapter overlappingTreeAdapter = new WekaTreeClassificationAdapter(
							(J48) overlappingTree, overlappingClassesEstimator.getInstances(), attributeType);

					additionalClassifiers.put(leafNode, overlappingTree);

					if (overlappingTreeAdapter.treeDepth() > 0) {

						// Add newly found overlapping conditions
						for (WekaLeafNode newLeaf : overlappingTreeAdapter.traverseLeafNodes()) {
							ArrayDeque<WekaCondition> mergedConditions = new ArrayDeque<WekaTreeClassificationAdapter.WekaCondition>();
							for (WekaCondition oldCondition : leafNode.getConditions()) {
								mergedConditions.addLast(oldCondition);
							}
							for (WekaCondition newCondition : newLeaf.getConditions()) {
								mergedConditions.addLast(newCondition);
							}
							WekaLeafNode newLeafNode = new WekaLeafNode(overlappingClassesEstimator.getInstances(),
									newLeaf.getClassName(), mergedConditions, newLeaf.getInstanceCount(),
									newLeaf.getWrongInstanceCount(), attributeType);

							if (newLeafNode.getInstanceCount() > 0.0) {
								// only add leaf if we actually saw instances
								manipulatedLeafs.add(newLeafNode);
							}
						}

					} else {

						// No decision tree found
						WekaLeafNode root = (WekaLeafNode) overlappingTreeAdapter.getRootNode();
						// Here we check with the original parameter to avoid just merging most leafs
						// only leafs with many wrong instances should be considered to be merged to avoid over-fitting
						if (root.getInstanceCount() > originalMinNumObjs) {
							// Enough instances available, check merge condition 
							if (fulfillsMergeCondition(root, leavesSortedByWrongInstances)) {
								ArrayDeque<WekaCondition> mergedConditions = new ArrayDeque<WekaTreeClassificationAdapter.WekaCondition>();
								for (WekaCondition oldCondition : leafNode.getConditions()) {
									mergedConditions.addLast(oldCondition);
								}

								WekaLeafNode newLeafNode = new WekaLeafNode(overlappingClassesEstimator.getInstances(),
										root.getClassName(), mergedConditions, root.getInstanceCount(),
										root.getWrongInstanceCount(), attributeType);

								if (newLeafNode.getInstanceCount() > 0.0) {
									// only add leaf if we actually saw instances
									manipulatedLeafs.add(newLeafNode);
								}
							}
						}

					}
				} else {
					// Add leaf as-is
					manipulatedLeafs.add(leafNode);
				}

			}
		}

		Map<Object, FunctionEstimation> estimation = buildExpressionsFromLeafs(manipulatedLeafs,
				new Function<String, Object>() {

					public Object apply(String classLabel) {
						return normalEstimator.classMapping.get(classLabel);
					}
				});

		Set<Object> tautologies = new HashSet<Object>();
		for (Iterator<Entry<Object, FunctionEstimation>> iterator = estimation.entrySet().iterator(); iterator
				.hasNext();) {
			Entry<Object, FunctionEstimation> entry = iterator.next();
			GuardExpression expression = entry.getValue().getExpression();
			if (checkForTautology(expression)) {
				tautologies.add(entry.getKey());
			}
		}
		for (Object obj : tautologies) {
			estimation.put(obj, FunctionEstimation.Factory.create(GuardExpression.Factory.newInstance("true"), -1.0d));
		}

		return ImmutableMap.copyOf(estimation);
	}

	private boolean fulfillsMergeCondition(WekaLeafNode root,
			ImmutableList<WekaLeafNode> leavesSortedByWrongInstances) {
		if ((root.getWrongInstanceCount() / root.getInstanceCount()) < mergeWrongInstanceRatio) {
			return true;
		} else {
			return alwaysMergeExistingLeafs && hasLeaf(leavesSortedByWrongInstances, root.getClassName());
		}
	}

	private boolean hasLeaf(ImmutableList<WekaLeafNode> leavesSortedByWrongInstances, String className) {
		for (WekaLeafNode node : leavesSortedByWrongInstances) {
			if (node.getClassName().equals(className)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Basic tautology check using all available instances
	 * 
	 * @param expression
	 *            to be checked
	 * @return whether the expression is always true for all available instances
	 */
	private final boolean checkForTautology(GuardExpression expression) {
		boolean allSatisfied = true;
		Set<String> normalVariables = expression.getNormalVariables();
		final Map<String, Object> valueMap = new HashMap<String, Object>();
		instanceFor: for (Instance instance : normalEstimator.getInstances()) {
			for (String variable : normalVariables) {
				Type type = attributeType.get(variable);
				Integer wekaIndex = normalEstimator.getAttributeIndexMap().get(variable);
				if (!instance.isMissing(wekaIndex)) {
					Object value = convertFromWekaData(type, instance, wekaIndex);
					valueMap.put(variable, value);
				} else {
					// Do not check this instances due to a missing value
					continue instanceFor;
				}
			}
			allSatisfied &= expression.isTrue(new VariableProvider.DefaultVariableProvider(valueMap));
			valueMap.clear();
		}
		return allSatisfied;
	}

	private static Object convertFromWekaData(Type type, Instance instance, Integer wekaIndex) {
		Object value = null;
		switch (type) {
			case BOOLEAN :
				String bool = instance.stringValue(wekaIndex);
				value = DecisionTreeFunctionEstimator.TRUE_VALUE.equals(bool);
				break;
			case CONTINUOS :
				value = Double.valueOf(instance.value(wekaIndex));
				break;
			case DISCRETE :
				value = Double.valueOf(instance.value(wekaIndex)).longValue();
				break;
			case LITERAL :
				value = instance.stringValue(wekaIndex);
				break;
			case TIMESTAMP :
				value = new Date(Double.valueOf(instance.value(wekaIndex)).longValue());
				break;
			default :
				break;
		}
		return value;
	}

	private boolean isCandidateLeaf(WekaLeafNode node) {
		return node.getWrongInstanceCount() > (normalEstimator.getNumFoldErrorPruning() + 1);
	}

	public String toString() {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("Overlapping Decision Discovery\n\n");
			sb.append("Initial Decision Tree\n");
			sb.append(normalEstimator.getClassifier().toString());
			sb.append("\n\n--------------------\n");
			sb.append("Additional Decision Trees");
			sb.append("\n--------------------\n");
			for (Entry<WekaLeafNode, AbstractClassifier> entry : additionalClassifiers.entrySet()) {
				WekaLeafNode leaf = entry.getKey();
				AbstractClassifier estimator = entry.getValue();
				if (leaf.getExpression() != null) {
					sb.append("Additional tree for leaf '" + leaf.getClassName() + "' under condition "
							+ leaf.getExpression().toPrettyString(1) + "\n");
					sb.append(estimator.toString() + "\n\n");
				} else {
					sb.append("Additional tree for leaf '" + leaf.getClassName() + "' under condition TRUE\n");
					sb.append(estimator.toString() + "\n\n");
				}
			}
			return sb.toString();
		} catch (ParseException e) {
			throw new RuntimeException("Invalid expression generated in overlapping rule discovery", e);
		}
	}

	public double computeFMeasure() {
		return computeQualityMeasure();
	}

	public double computeQualityMeasure() {
		return -1;
	}

	public String getQualityMeasureName() {
		return "No Quality Measure Defined";
	}

	public int getNumInstances() {
		return normalEstimator.getNumInstances();
	}

	public double getSumOfWeights() {
		return normalEstimator.getInstances().sumOfWeights();
	}

	public void setMinNumObj(int minNumObj) {
		normalEstimator.setMinNumObj(minNumObj);
	}

	public void setUnpruned(boolean unpruned) {
		normalEstimator.setUnpruned(unpruned);
	}

	public void setBinarySplit(boolean selected) {
		normalEstimator.setBinarySplit(selected);
	}

	public void setCrossValidate(boolean validate) {
		normalEstimator.setCrossValidate(validate);
	}

	public boolean isReduceMinLeafs() {
		return isReduceMinLeafs;
	}

	public void setReduceMinLeafs(boolean isReduceMinLeafs) {
		this.isReduceMinLeafs = isReduceMinLeafs;
	}

	public void setConfidenceFactor(float confidenceThreshold) {
		normalEstimator.setConfidenceFactor(confidenceThreshold);
	}

	public void setNumFolds(int numFoldErrorPruning) {
		normalEstimator.setNumFolds(numFoldErrorPruning);
	}

	public Object classifyInstance(Map<String, Object> attributes) throws Exception {
		throw new UnsupportedOperationException();
	}

}