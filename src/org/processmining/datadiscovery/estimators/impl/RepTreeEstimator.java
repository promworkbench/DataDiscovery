package org.processmining.datadiscovery.estimators.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import org.processmining.datadiscovery.estimators.AbstractDecisionTreeFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter;

import com.google.common.base.Function;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.Instance;

/**
 * This class builds an regression tree and is very different from the other
 * {@link DecisionTreeFunctionEstimator} implementations.
 *
 */
public class RepTreeEstimator extends AbstractDecisionTreeFunctionEstimator {

	public RepTreeEstimator(Map<String, Type> attributeType, Map<String, Set<String>> literalValues,
			String classAttributeName, String name, int capacity) {
		super(name, attributeType, literalValues, new Object[] {}, capacity);
		// We do not have output classes, but we need to override the default class attribute
		// We assume that the super class already build the 'attributeList'
		Attribute classAttribute = getAttributeByName(classAttributeName);
		if (classAttribute == null) {
			throw (new IllegalArgumentException(
					"The chosen class attribute " + classAttributeName + " has not been defined."));
		} else {
			instances.setClass(classAttribute);
		}
	}

	protected ArrayList<Attribute> createAttributeList(Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses) {
		ArrayList<Attribute> attributeList = new ArrayList<Attribute>(attributeType.keySet().size());
		for (Entry<String, Type> entry : attributeType.entrySet()) {
			switch (entry.getValue()) {
				case LITERAL :
					attributeList.add(
							new Attribute(entry.getKey(), new LinkedList<String>(literalValues.get(entry.getKey()))));
					break;
				case TIMESTAMP :
					attributeList.add(new Attribute(entry.getKey(), "yyyy-MM-dd'T'HH:mm:ss"));
					break;
				case DISCRETE : // Do the same as case CONTINOUS
				case CONTINUOS :
					attributeList.add(new Attribute(entry.getKey()));
					break;
				case BOOLEAN :
					if (entry.getKey().equals(classAttributeName))
						throw (new IllegalArgumentException("The class attribute is BOOLEAN. Not admitted!"));
					else
						attributeList.add(new Attribute(entry.getKey(), booleanValues));
			}
		}
		return attributeList;
	}

	public double computeQualityMeasure() {
		return evaluation.weightedFMeasure();
	}

	public Map<Object, FunctionEstimation> getFunctionEstimation(Object[] option) throws Exception {

		tree = createClassifier(option, saveData);

		// Build guard expressions using the information from the tree
		WekaTreeClassificationAdapter wekaTreeAdapter = new WekaTreeClassificationAdapter((REPTree) tree, instances,
				variableType);
		Map<Object, FunctionEstimation> expressions = buildExpressionsFromLeafs(wekaTreeAdapter.traverseLeafNodes(),
				new Function<String, Object>() {

					public Object apply(String leafLabel) {
						return leafLabel; // just use leaflabel
					}
				});

		// Add evaluation
		Evaluation eval = getEvaluation();
		double fScore;
		for (int classIndex = 0; classIndex < instances.classAttribute().numValues(); classIndex++) {
			String classValue = instances.classAttribute().value(classIndex);
			Object objectValue;
			if (isOutputLiteral()) {
				objectValue = classValue;
			} else {
				objectValue = Double.parseDouble(classValue);
			}
			FunctionEstimation pair = expressions.get(objectValue);
			if (pair != null) {
				fScore = eval.fMeasure(classIndex);
				expressions.put(objectValue, FunctionEstimation.Factory.create(pair.getExpression(), fScore));
			}
		}

		return expressions;
	}

	@Override
	protected AbstractClassifier createClassifier(Object option[], boolean saveData) throws Exception {
		REPTree tree = new REPTree();
		if (option != null && option instanceof String[])
			tree.setOptions((String[]) option);
		tree.setNoPruning(unpruned);
		tree.setMinNum(minNumInstancePerLeaf);
		tree.setNumFolds(numFoldErrorPruning);

		if (crossValidate) {
			evaluation = new Evaluation(instances);
			evaluation.crossValidateModel(tree, instances, numFoldCrossValidation, new Random(1));
			tree.buildClassifier(instances);
		} else {
			tree.buildClassifier(instances);
			evaluation = new Evaluation(instances);
			evaluation.evaluateModel(tree, instances);
		}

		System.out.println(name);
		System.out.println(tree);
		return tree;
	}

	public synchronized void addInstance(Map<String, Object> variableAssignment, Object outputValue, float weight) {
		// Create an instance based on the variable value assignment
		Instance instance = createInstance(variableAssignment);
		// Set the weight to 1 (default, not doing weighted analysis)
		instance.setWeight(weight);

		if (outputValue == null) {
			System.out.println("NULL ");
			return;
		}
		if (outputValue instanceof Date)
			outputValue = ((Date) outputValue).getTime();
		if (isOutputLiteral())
			instance.setValue(instances.classAttribute(), outputValue.toString());
		else
			instance.setValue(instances.classAttribute(), ((Number) outputValue).doubleValue());
		instances.add(instance);
	}

	private boolean isOutputLiteral() {
		return variableType.get(instances.classAttribute().name()).equals(Type.LITERAL);
	}

	/*
	 * Outputs the classification result as Double
	 */
	public Object classifyInstance(Map<String, Object> attributes) throws Exception {
		return classify(attributes);
	}

}
