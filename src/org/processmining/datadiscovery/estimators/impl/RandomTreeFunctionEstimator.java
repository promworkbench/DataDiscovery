package org.processmining.datadiscovery.estimators.impl;

import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.processmining.datadiscovery.estimators.Type;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomTree;

public class RandomTreeFunctionEstimator extends DecisionTreeFunctionEstimator {

	public RandomTreeFunctionEstimator(Map<String, Type> attributeType, Map<String, Set<String>> literalValues,
			Object[] outputClasses, String name, int capacity) {
		super(attributeType, literalValues, outputClasses, name, capacity);
	}

	protected AbstractClassifier createClassifier(Object option[], boolean saveData) throws Exception {
		RandomTree tree = new RandomTree();
		if (option != null && option instanceof String[])
			tree.setOptions((String[]) option);
		tree.setMinNum(minNumInstancePerLeaf);
		tree.setNumFolds(numFoldErrorPruning);

		if (crossValidate && instances.size() > (numFoldErrorPruning + 1)) {
			// k-fold cross validation 
			evaluation = new Evaluation(instances);
			evaluation.crossValidateModel(tree, instances, numFoldCrossValidation, new Random());
			tree.buildClassifier(instances);
		} else {
			tree.buildClassifier(instances);
			evaluation = new Evaluation(instances);
			evaluation.evaluateModel(tree, instances);
		}

		return tree;
	}

}