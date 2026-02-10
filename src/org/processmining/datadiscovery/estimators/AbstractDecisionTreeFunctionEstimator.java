package org.processmining.datadiscovery.estimators;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter;
import org.processmining.datadiscovery.visualizers.PrefuseTreeVisualization;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Drawable;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffSaver;
import weka.gui.treevisualizer.PlaceNode2;
import weka.gui.treevisualizer.TreeDisplayListener;
import weka.gui.treevisualizer.TreeVisualizer;

abstract public class AbstractDecisionTreeFunctionEstimator extends AbstractFunctionEstimator
		implements DecisionTreeBasedFunctionEstimator {

	static class ColourCustomizableTreeVisualizer extends TreeVisualizer {

		private static final long serialVersionUID = -6536903757687046833L;

		public ColourCustomizableTreeVisualizer(TreeDisplayListener tdl, String dot, PlaceNode2 p, Color nodeColor) {
			super(tdl, dot, p);
			this.m_NodeColor = nodeColor;
		}

		public ColourCustomizableTreeVisualizer(TreeDisplayListener tdl, String dot, PlaceNode2 p) {
			super(tdl, dot, p);
			this.m_NodeColor = Color.LIGHT_GRAY;
		}
	}

	public static final String FALSE_VALUE = "F";
	public static final String TRUE_VALUE = "T";

	protected static final ImmutableList<String> booleanValues = ImmutableList.of(TRUE_VALUE, FALSE_VALUE);
	protected static final String nullValue = "NOT SET";
	protected static final String classAttributeName = "CLASS";

	protected String name; // The associated Place's label

	protected final Instances instances;
	protected AbstractClassifier tree;
	protected Evaluation evaluation;

	protected boolean crossValidate = false;
	protected int numFoldCrossValidation = 5;
	protected Random crossValidateRandom = new Random();

	protected boolean unpruned = false;
	protected float confidenceThreshold = 0.25f;
	protected int minNumInstancePerLeaf = 2;
	protected int numFoldErrorPruning = 3;
	protected boolean binarySplit = false;
	protected boolean saveData;
	

	protected final Map<String, Type> variableType; // Mapping from variable name to variable data type
	protected final ArrayList<Attribute> attributeList; // ArrayList of attributes and the format of their values (WEKA requires an ArrayList instead of a List)
	private final Map<String, Integer> attributeIndexMap; // Mapping from Attribute name to their index in attributeList	

	protected AbstractDecisionTreeFunctionEstimator(String name, Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses, int capacity) {
		this.name = name;
		this.variableType = attributeType;
		this.attributeIndexMap = Maps.newHashMapWithExpectedSize(attributeType.keySet().size() + 1);
		this.attributeList = createAttributeList(attributeType, literalValues, outputClasses);
		int attributeCounter = 0;
		for (Attribute attribute : attributeList) {
			attributeIndexMap.put(attribute.name(), attributeCounter++);
		}
		/*
		 * Create an empty set of instances with relation name 'name' (the
		 * Place's name), attribute information 'attributeList' and capacity
		 * 'capacity'.
		 */
		this.instances = new Instances(name, attributeList, capacity);
	}

	protected Attribute getAttributeByName(String attributeName) {
		Integer index = attributeIndexMap.get(attributeName);
		if (index < attributeList.size()) {
			return attributeList.get(index);
		}
		return null;
	}

	/**
	 * Create an {@link ArrayList} with all attributes used (including the class
	 * attribute)
	 * 
	 * @param attributeType
	 * @param literalValues
	 * @param outputClasses
	 * @return
	 */
	abstract protected ArrayList<Attribute> createAttributeList(Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses);

	/**
	 * Create the classifier
	 * 
	 * @param option
	 * @param saveData
	 * @return
	 * @throws Exception
	 */
	abstract protected AbstractClassifier createClassifier(Object[] option, boolean saveData) throws Exception;

	/**
	 * Creates an instance based on the variables and their values (as Objects).
	 * 
	 * @param variableAssignment
	 *            A mapping between variable names (String) and their values
	 *            (Object)
	 * @return An Instance containing the variables and their values
	 */
	protected Instance createInstance(Map<String, Object> variableAssignment) {
		Instance instance = new DenseInstance(instances.numAttributes());
		for (Entry<String, Object> entry : variableAssignment.entrySet()) {
			Attribute attr = null;
			String attributeKey = entry.getKey();
			//FM, optimized lookup avoid O(n) array traversal in weka				
			Integer attributeIndex = attributeIndexMap.get(attributeKey);
			if (attributeIndex == null) {
				continue;
			}
			attr = instances.attribute(attributeIndex);
			if (attr == null)
				continue;
			Object value = entry.getValue();
			if (value == null) {
				// NULL means there is a missing value for this attribute
				instance.setMissing(attr);
			} else if (value instanceof Number && (variableType.get(attributeKey) == Type.DISCRETE
					|| variableType.get(attributeKey) == Type.CONTINUOS))
				instance.setValue(attr, ((Number) value).doubleValue());
			else if (value instanceof Date && variableType.get(attributeKey) == Type.TIMESTAMP)
				instance.setValue(attr, ((Date) value).getTime());
			else if (value instanceof Boolean && variableType.get(attributeKey) == Type.BOOLEAN) {
				if (((Boolean) value).booleanValue())
					instance.setValue(attr, TRUE_VALUE);
				else
					instance.setValue(attr, FALSE_VALUE);
			} else if (value instanceof String && variableType.get(attributeKey) == Type.LITERAL) {
				instance.setValue(attr, (String) value);
			} else {
				System.out.println("Skipped variable " + attributeKey + " with value " + entry.getValue());
			}
		}
		return instance;
	}

	public void saveInstances(File file) throws IOException {
		ArffSaver saver = new ArffSaver();
		saver.setInstances(instances);
		saver.setFile(file);
		saver.writeBatch();
	}

	/**
	 * Creates a weka J48 tree based on String[] options and sets it as the tree
	 * to use.
	 * 
	 * @param options
	 *            String[] of weka J48 tree options.
	 * @throws Exception
	 *             if classifier can't be built correctly
	 */
	public void createAndSetTree(Object options[]) throws Exception {
		this.tree = createClassifier(options, saveData);
	}

	/**
	 * Classifies the variableAssignment according to the tree of the function
	 * estimator.
	 * 
	 * @param attributes
	 *            Mapping of variable name <String> to value <Object>.
	 * @return
	 * @throws Exception
	 */
	public double classify(Map<String, Object> attributes) throws Exception {
		Instance instance = createInstance(attributes);
		return tree.classifyInstance(instance);
	}

	/**
	 * Returns a performed evaluation of the classifier on the instances.
	 * 
	 * @return Evaluation of the tree on the instances.
	 */
	public Evaluation getEvaluation() {
		if (tree == null) {
			return null;
		} else {
			return evaluation;
		}
	}

	public WekaTreeClassificationAdapter getTreeClassificationAdapter() {
		if (tree != null) {
			return new WekaTreeClassificationAdapter((Drawable) tree, instances, variableType);
		} else {
			return null;
		}
	}

	/**
	 * Set the saveData boolean. True to enable saving instance data to nodes.
	 * 
	 * @param saveData
	 */
	public void setSaveData(boolean saveData) {
		this.saveData = saveData;
	}

	@Override
	public int getNumInstances() {
		return instances.numInstances();
	}
	
	@Override
	public double getSumOfWeights() {
		return instances.sumOfWeights();
	}

	@Override
	public void setUnpruned(boolean b) {
		unpruned = b;

	}

	@Override
	public void setCrossValidate(boolean doCrossValidate) {
		crossValidate = doCrossValidate;
	}

	@Override
	public void setConfidenceFactor(float confidenceThreshold) {
		this.confidenceThreshold = confidenceThreshold;
	}

	@Override
	public void setMinNumObj(int minNumInstancePerLeaf) {
		this.minNumInstancePerLeaf = minNumInstancePerLeaf;

	}

	@Override
	public void setNumFolds(int numFoldErrorPruning) {
		this.numFoldErrorPruning = numFoldErrorPruning;
	}

	@Override
	public void setBinarySplit(boolean binarySplit) {
		this.binarySplit = binarySplit;
	}

	/**
	 * Returns a JPanel containing a visualization of the weka tree.
	 * 
	 * @return <JPanel> containing a visualization of the decision tree.
	 */
	public JPanel getVisualization() {
		if (tree == null) {
			return null;
		} else {
			if (tree instanceof Drawable) {
				try {
					return new ColourCustomizableTreeVisualizer(null, ((Drawable) tree).graph(), new PlaceNode2());
				} catch (Exception e) {
					return null;
				}
			} else {
				throw new IllegalArgumentException("Cannot visualize non-drawable weka classifier");
			}
		}
	}

	/**
	 * Returns a JPanel containing a visualization of the weka J48 tree using
	 * prefusetrees
	 * 
	 * @return <JPanel> containing a visualization of the decision tree.
	 */
	public JComponent getPrefuseTreeVisualization() {
		if (tree == null)
			return null;
		else if (tree instanceof Drawable) {
			try {
				PrefuseTreeVisualization ptv = new PrefuseTreeVisualization();
				return ptv.display(((Drawable) tree).graph(), instances.classAttribute().name());
			} catch (Exception e) {
				return null;
			}
		} else {
			throw new IllegalArgumentException("Cannot visualize non-drawable weka classifier");
		}
	}

	@Override
	public String toString() {
		if (tree != null) {
			String evaluationResult = "N/A";
			if (crossValidate) {
				try {
					evaluationResult = evaluation != null ? " cross-validated \n" + getEvaluationSummary() : "N/A";
				} catch (Exception e) {
				}
			} else {
				try {
					evaluationResult = evaluation != null ? getEvaluationSummary() : "N/A";
				} catch (Exception e) {
				}
			}
			return String.format("Decision Tree\n%s\nEvaluation:\n%s", tree, evaluationResult);
		} else {
			return "[NO TREE]";
		}
	}

	private String getEvaluationSummary() throws Exception {
		return evaluation.toMatrixString() + "\n" + evaluation.toSummaryString(false) + "\n"
				+ evaluation.toClassDetailsString();
	}

	public int getNumFoldCrossValidation() {
		return numFoldCrossValidation;
	}

	public void setNumFoldCrossValidation(int numFoldCrossValidation) {
		this.numFoldCrossValidation = numFoldCrossValidation;
	}

	public String getName() {
		return name;
	}

	public boolean isCrossValidate() {
		return crossValidate;
	}

	public boolean isUnpruned() {
		return unpruned;
	}

	public float getConfidenceThreshold() {
		return confidenceThreshold;
	}

	public int getMinNumInstancePerLeaf() {
		return minNumInstancePerLeaf;
	}

	public int getNumFoldErrorPruning() {
		return numFoldErrorPruning;
	}

	public boolean isBinarySplit() {
		return binarySplit;
	}

	public Map<String, Integer> getAttributeIndexMap() {
		return attributeIndexMap;
	}
	
	public Random getCrossValidateRandom() {
		return crossValidateRandom;
	}

	public void setCrossValidateRandom(Random crossValidateRandom) {
		this.crossValidateRandom = crossValidateRandom;
	}	

}