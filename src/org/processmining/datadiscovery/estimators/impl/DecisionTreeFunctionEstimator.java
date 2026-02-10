package org.processmining.datadiscovery.estimators.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.deckfour.xes.model.XLog;
import org.processmining.datadiscovery.estimators.AbstractDecisionTreeFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.util.AttributeUtil;
import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter;
import org.processmining.datapetrinets.expression.GuardExpression;

import com.google.common.base.Function;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.Drawable;
import weka.core.Instance;
import weka.core.Instances;

public class DecisionTreeFunctionEstimator extends AbstractDecisionTreeFunctionEstimator {

	protected final Object[] outputClasses;
	protected List<String> classValues; // ArrayList of unique identifiers for target transitions
	protected BiMap<Object, Integer> classIndexMap; // Mapping from target classes their class index in weka
	protected BiMap<String, Object> classMapping; // Bidirectional mapping from unique identifiers of target transitions

	private boolean treatNoLeafAsFalse = false;

	/**
	 * Constructs a new DecisionTreeFunctionEstimator with the place's target
	 * transitions as CLASS value.
	 * 
	 * @param attributeType
	 *            A Mapping from Attribute name String to Attribute Type (use
	 *            {@link AttributeUtil} to create one from a {@link XLog}
	 * @param literalValues
	 *            A Mapping from XAttributeLiteral attribute name String to a
	 *            Set of literal values of type String (use
	 *            {@link AttributeUtil} to create one from a {@link XLog}
	 * @param outputClasses
	 *            An Object[] array of target CLASS objects.
	 * @param name
	 *            The label of the place associated with this function estimator
	 * @param capacity
	 *            parameter for weka 'instances'.
	 */
	public DecisionTreeFunctionEstimator(Map<String, Type> attributeType, Map<String, Set<String>> literalValues,
			Object[] outputClasses, String name, int capacity) {
		super(name, attributeType, literalValues, outputClasses, capacity);
		this.outputClasses = outputClasses;
		Attribute classAttribute = getAttributeByName(classAttributeName);
		instances.setClass(classAttribute);
	}

	protected ArrayList<Attribute> createAttributeList(Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses) {
		ArrayList<Attribute> attributeList = new ArrayList<>(attributeType.keySet().size() + 1);
		/*
		 * For each entry <String, Type> in map, depending on the Type of the
		 * attribute, add a new Attribute (attribute name, format for values of
		 * said attribute) to ArrayList<Attribute> attributeList
		 */
		for (Entry<String, Type> entry : attributeType.entrySet()) {
			switch (entry.getValue()) {
				case LITERAL :
					attributeList
							.add(new Attribute(entry.getKey(), new ArrayList<>(literalValues.get(entry.getKey()))));
					break;
				case TIMESTAMP :
					//TODO FM, why do we need this timeformat here?
					attributeList.add(new Attribute(entry.getKey(), "yyyy-MM-dd'T'HH:mm:ss"));
					break;
				case DISCRETE : // Do the same as case CONTINOUS
				case CONTINUOS :
					attributeList.add(new Attribute(entry.getKey()));
					break;
				case BOOLEAN :
					attributeList.add(new Attribute(entry.getKey(), booleanValues));
			}
		} // END for(Entry<String, Type> entry : map.entrySet())

		// Initialize the classValues ArrayList to an ArrayList<String> of size outputValuesAsObjects.length
		classValues = new ArrayList<String>(outputClasses.length);
		// Add the predefined nullValue to classValues
		classValues.add(nullValue);
		classIndexMap = HashBiMap.create();
		classIndexMap.put(nullValue, 0);
		/*
		 * Populate the classValues array with unique identifiers for class
		 * values and populate 'mapping' with mappings from the unique String
		 * identifier of a class value to the actual class value Object
		 */
		createArray(outputClasses);
		// Define a new attribute ("CLASS",classValues) containing the unique textual representations of target Transitions
		Attribute classAttribute = new Attribute(classAttributeName, classValues);

		/*
		 * Add the ("CLASS",classValues) attribute to to the end of
		 * attributeList. --> We now have an attribute to uniquely identify the
		 * target transitions <-- Use as class for classification!
		 */
		attributeList.add(classAttribute);

		return attributeList;
	}

	/**
	 * Converts all class values (from Object[] outputValues) to unique String
	 * identifiers in classValues, and maps them to the actual Objects.
	 * 
	 * @param outputValues
	 *            An Object[] of the target Transitions of associated Place
	 * @param classValues
	 *            An ArrayList<String> containing the unique String
	 *            representations of a transition
	 * @param classMapping
	 *            a Map<String,Object> from the unique String representation of
	 *            a transition's Object representation to the Object itself
	 */
	private void createArray(Object[] outputValues) {
		classMapping = HashBiMap.create();
		for (int i = 0; i < outputValues.length; i++) {
			/*
			 * For each Object (Transition) in outputValues, convert the
			 * Transition to a string 'value', replacing newlines with spaces
			 * and put it in classValues. If ArrayList<String> classValues
			 * already contains that value, concatenate an integer to it until
			 * it's not in classValues. Finally, add the mapping (String value,
			 * Object outputValues[i]) to Map<String,Object> 'mapping'
			 */
			String value = outputValues[i].toString().replace('\n', ' ');
			int n = 0;
			while (classValues.contains(value)) {
				n++;
				value = value + n;
			}
			classValues.add(value);
			classIndexMap.put(outputValues[i], i + 1);
			classMapping.put(value, outputValues[i]);
		}
	}

	/**
	 * Adds a new instance to the estimator's 'instances'.
	 * 
	 * @param variableAssignment
	 *            A Map<String,Object> of variable identifier (key) and its
	 *            value (object).
	 * @param outputValue
	 *            The Transition which is to be executed with the variable
	 *            values of variableAssignment.
	 * @param weight
	 *            Parameter for weighted decision trees. Keep 1 for default.
	 */
	public void addInstance(Map<String, Object> variableAssignment, Object outputValue, float weight) {
		// Create an instance based on the variable value assignment
		Instance instance = createInstance(variableAssignment);
		addWekaInstance(instance, outputValue, weight);
	}

	public void addWekaInstance(Instance instance, Object outputValue, float weight) {
		// Set the weight 
		instance.setWeight(weight);

		// Get the target transition
		String classValue = getClassValue(outputValue);
		// Set the class attribute value and add the instance to the set of instances
		// FM, instances.classAttribute() is thread-safe!
		instance.setValue(instances.classAttribute(), classValue);

		//FM, It is safe to just synchronize here on the instances, 
		//    as we are not using the list of instances anywhere else!
		synchronized (instances) {
			instances.add(instance);
		}
	}

	/**
	 * Retrieve the classValue from 'mapping' for a given target class (Object)
	 * 
	 * @param outputValue
	 *            A target class (Object)
	 * @return
	 */
	protected String getClassValue(Object outputValue) {
		if (outputValue == null) {
			return nullValue;
		} else {
			return classMapping.inverse().get(outputValue);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.processmining.models.FunctionEstimator.FunctionEstimator#
	 * computeQualityMeasure()
	 */
	public double computeQualityMeasure() { //TODO shouldn't that be the weighted fscore or better RSME?
		Evaluation eval = this.getEvaluation();
		double sumFMeasures = 0;
		double toConsider = 0;
		for (int i = 0; i < classValues.size(); i++) {
			if (eval.fMeasure(i) > 0) {
				sumFMeasures += 1 / eval.fMeasure(i);
				toConsider++;
			}
		}
		if (toConsider != 0) {
			return toConsider / sumFMeasures;
		} else {
			return 0;
		}
	}

	/**
	 * Creates the RepTree using the earlier supplied options.
	 * 
	 * @param option
	 *            Array of Strings containing J48 tree options.
	 * @param saveData
	 *            Boolean. True to enable saving instance data to the tree.
	 * @return a J48 tree defined by options, associated with 'instances'.
	 * @throws Exception
	 *             if classifier can't be built correctly
	 */
	protected AbstractClassifier createClassifier(Object option[], boolean saveData) throws Exception {
		J48 tree = new J48();
		if (option != null && option instanceof String[])
			tree.setOptions((String[]) option);
		tree.setUnpruned(unpruned);
		tree.setConfidenceFactor(confidenceThreshold);
		tree.setMinNumObj(minNumInstancePerLeaf);
		tree.setNumFolds(numFoldErrorPruning);
		tree.setBinarySplits(binarySplit);
		tree.setSaveInstanceData(saveData);

		if (crossValidate && instances.size() > (numFoldCrossValidation + 1)) {
			// k-fold cross validation 
			evaluation = new Evaluation(instances);
			evaluation.crossValidateModel(tree, instances, numFoldCrossValidation, crossValidateRandom);	
			tree.buildClassifier(instances);
		} else {
			tree.buildClassifier(instances);
			evaluation = new Evaluation(instances);
			evaluation.evaluateModel(tree, instances);
		}

		return tree;
	}

	/**
	 * Returns a mapping from a Transition <Object> to a
	 * {@link FunctionEstimation} (i.e, a pair of (condition
	 * {@link GuardExpression}, and the likelihood of correct classification).
	 * <p>
	 * The all leaf nodes of the J48 tree are traversed to build the
	 * {@link GuardExpression}. Each leaf corresponds to a resulting CLASS,
	 * representing a target transition.
	 * 
	 * @param option
	 *            A String[] array of weka J48 tree options. Set to null to
	 *            ignore.
	 * @return A mapping <Object, FunctionEstimation> between a Transition
	 *         (Object) and a {@link FunctionEstimation} (i.e., a pair of
	 *         condition (GuardExpression) and likelihood (Double).
	 */
	public Map<Object, FunctionEstimation> getFunctionEstimation(Object[] option) throws Exception {

		// Create a J48 tree with options String 'option' and boolean saveData to save instance data in the tree
		tree = createClassifier(option, saveData);

		// Build guard expressions using the information from the tree
		WekaTreeClassificationAdapter wekaJ48Adapter = new WekaTreeClassificationAdapter((Drawable) tree, instances,
				variableType);

		Map<Object, FunctionEstimation> expressions = buildExpressionsFromLeafs(wekaJ48Adapter.traverseLeafNodes(),
				new Function<String, Object>() {

					public Object apply(String leafLabel) {
						return classMapping.get(leafLabel); // lookup class object
					}
				});

		if (treatNoLeafAsFalse) {
			for (Object classObj : classIndexMap.keySet()) {
				if (classObj != nullValue) {
					FunctionEstimation estimation = expressions.get(classObj);
					if (estimation == null) {
						expressions.put(classObj,
								FunctionEstimation.Factory.create(GuardExpression.Factory.falseInstance(), 0.0));
					}
				}
			}
		} else {
			for (Object classObj : classIndexMap.keySet()) {
				if (classObj != nullValue) {
					FunctionEstimation estimation = expressions.get(classObj);
					if (estimation == null) {
						expressions.put(classObj,
								FunctionEstimation.Factory.create(GuardExpression.Factory.trueInstance(), 0.0));
					}
				}
			}
		}

		// Add evaluation
		Evaluation eval = getEvaluation();
		double fScore;
		for (int classIndex = 0; classIndex < instances.classAttribute().numValues(); classIndex++) {
			String classValue = instances.classAttribute().value(classIndex);
			Object objectValue = classMapping.get(classValue);
			FunctionEstimation pair = expressions.get(objectValue);
			if (pair != null) {
				fScore = eval.fMeasure(classIndex);
				expressions.put(objectValue, FunctionEstimation.Factory.create(pair.getExpression(), fScore));
			}
		}

		return expressions;

	}

	@Override
	public Object classifyInstance(Map<String, Object> attributes) throws Exception {
		double index = classify(attributes);
		return classIndexMap.inverse().get((int) index);
	}

	public AbstractClassifier getClassifier() {
		return tree;
	}

	public boolean isTreatNoLeafAsFalse() {
		return treatNoLeafAsFalse;
	}

	public void setTreatNoLeafAsFalse(boolean treatNoLeafAsFalse) {
		this.treatNoLeafAsFalse = treatNoLeafAsFalse;
	}

	public Instances getInstances() {
		return instances;
	}

	/**
	 * @return mapping between classes as objects and weka class index
	 */
	public BiMap<Object, Integer> getClassIndexMap() {
		return classIndexMap;
	}

}
