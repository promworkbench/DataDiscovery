package org.processmining.datadiscovery.estimators.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.processmining.datadiscovery.estimators.AbstractFunctionEstimator;
import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datapetrinets.expression.GuardExpression;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.trees.J48;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;

public class OverlappingEstimatorPairwiseDecisionTrees extends AbstractFunctionEstimator implements DecisionTreeBasedFunctionEstimator {

	public interface PartitionEstimatorResult {
		
		Partition getPartition();

		double getFMeasure();

		Map<Object, FunctionEstimation> getPartitionEstimation();

		AbstractClassifier getTree();
		
	}

	public class Partition {

		private final Set<Object> otherClasses;
		private final Set<Object> combinedClasses;

		public Partition(Set<Object> combinedClass, Set<Object> otherClasses) {
			this.otherClasses = otherClasses;
			this.combinedClasses = ImmutableSet.copyOf(combinedClass);
		}

		public List<Object> getSubSets() {
			return ImmutableList.builder().add(new CombinedClass(combinedClasses)).addAll(otherClasses).build();
		}

		public Object get(Object outputClass) {
			if (combinedClasses.contains(outputClass)) {
				return new CombinedClass(combinedClasses);
			} else {
				// Unchanged
				return outputClass;
			}
		}

		public String toString() {
			return String.format("[%s],%s", combinedClasses, otherClasses);
		}

	}

	public class PartitionGenerator implements Iterator<Partition> {

		private final List<Object> classSet;
		private ListIterator<Object> firstClassIterator;
		private Iterator<Object> secondClassIterator;
		private Object currentClass;
		private int partitionIndex = 0;

		public PartitionGenerator(Set<Object> classSet) {
			this.classSet = Lists.newArrayList(classSet);
			this.firstClassIterator = this.classSet.listIterator();
		}

		public boolean hasNext() {
			return firstClassIterator.hasNext() && !(firstClassIterator.nextIndex() + 1 == classSet.size());
		}

		public Partition next() {
			if (currentClass != null && secondClassIterator.hasNext()) {
				final Object secondClass = secondClassIterator.next();
				Set<Object> combinedClass = ImmutableSet.of(currentClass, secondClass);
				partitionIndex++;
				Iterable<Object> filter = Iterables.filter(classSet,
						new Predicate<Object>() {

							public boolean apply(Object o) {
								return o != currentClass && o != secondClass;
							}
						});
				return new Partition(combinedClass, ImmutableSet.copyOf(filter));
			} else {
				currentClass = firstClassIterator.next();
				secondClassIterator = this.classSet.listIterator(firstClassIterator.nextIndex());
				if (secondClassIterator.hasNext()) {
					return next();
				} else {
					throw new IllegalStateException("Second Iterator should not have been called!");
				}
			}
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public int numPartitionsGenerated() {
			return partitionIndex;
		}

	}

	private static class CombinedClass implements Iterable<Object> {

		private Set<Object> classes;

		public CombinedClass(Set<Object> classes) {
			super();
			this.classes = classes;
		}

		public String toString() {
			return Arrays.toString(classes.toArray());
		}

		public Iterator<Object> iterator() {
			return classes.iterator();
		}

		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((classes == null) ? 0 : classes.hashCode());
			return result;
		}

		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CombinedClass other = (CombinedClass) obj;
			if (classes == null) {
				if (other.classes != null)
					return false;
			} else if (!classes.equals(other.classes))
				return false;
			return true;
		}

	}

	private final ThreadPoolExecutor pool;

	private final Map<Object, AbstractClassifier> usedClassifiers = new HashMap<>();
	private final Map<String, Type> attributeType;
	private final Map<String, Set<String>> literalValues;
	private final Object[] outputClasses;
	private final int capacity;
	private final String decisionPointName;

	private final DecisionTreeFunctionEstimator normalEstimator;

	private Map<Object, FunctionEstimation> estimation;

	private boolean mixEstimations = true;

	public OverlappingEstimatorPairwiseDecisionTrees(Map<String, Type> attributeType,
			Map<String, Set<String>> literalValues, Object[] outputClasses, int capacity, String name,
			ThreadPoolExecutor pool) {
		super();
		this.attributeType = attributeType;
		this.literalValues = literalValues;
		this.outputClasses = outputClasses;
		this.capacity = capacity;
		this.decisionPointName = name;
		this.pool = pool;
		this.normalEstimator = new DecisionTreeFunctionEstimator(attributeType, literalValues, outputClasses, name,
				capacity);
	}

	public void addInstance(Map<String, Object> variableAssignment, Object outputValue, float weight) throws Exception {
		normalEstimator.addInstance(variableAssignment, outputValue, weight);
	}
	
	public void saveInstances(File file) throws IOException {
		normalEstimator.saveInstances(file);
	}

	public Map<Object, FunctionEstimation> getFunctionEstimation(final Object[] option) throws Exception {
		estimation = normalEstimator.getFunctionEstimation(option);

		if (numOutputClasses() > 2) {

			final Set<Object> recomputeSet = new HashSet<>();
			for (Object o : outputClasses) {
				recomputeSet.add(o);
			}

			double currentFScore = normalEstimator.computeQualityMeasure();

			List<Future<PartitionEstimatorResult>> partitionedEstimators = new ArrayList<>();

			PartitionGenerator partitionView = new PartitionGenerator(recomputeSet);
			while (partitionView.hasNext()) {

				final Partition partition = partitionView.next();
				final Instances instances = normalEstimator.getInstances();

				partitionedEstimators.add(pool.submit(new Callable<PartitionEstimatorResult>() {

					public PartitionEstimatorResult call() throws Exception {

						final DecisionTreeFunctionEstimator partitionEstimator = new DecisionTreeFunctionEstimator(
								attributeType, literalValues, partition.getSubSets().toArray(), decisionPointName,
								capacity);
					
						partitionEstimator.setBinarySplit(normalEstimator.isBinarySplit());
						partitionEstimator.setNumFolds(normalEstimator.getNumFoldErrorPruning());
						partitionEstimator.setMinNumObj(normalEstimator.getMinNumInstancePerLeaf());
						partitionEstimator.setUnpruned(normalEstimator.isUnpruned());
						partitionEstimator.setCrossValidate(normalEstimator.isCrossValidate());

						for (Instance instance : instances) {

							Integer wekaClass = (int) instance.value(instances.classIndex());
							Object classObj = normalEstimator.classIndexMap.inverse().get(wekaClass);

							Object classPartition = partition.get(classObj);
							DenseInstance combinedClassInstance = new DenseInstance(instance);
							partitionEstimator.addWekaInstance(combinedClassInstance, classPartition, 1.0f);
						}

						final Map<Object, FunctionEstimation> partitionEstimation = partitionEstimator
								.getFunctionEstimation(option);

						final Map<Object, Double> updatedFScores = reEvaluateClasses(instances,
								partitionEstimator.getInstances(), partitionEstimator.getClassifier(),
								partitionEstimator.classIndexMap.inverse());

						final Map<Object, FunctionEstimation> fixedEstimation = new HashMap<>();

						for (Entry<Object, FunctionEstimation> entry : partitionEstimation.entrySet()) {

							Object classObj = entry.getKey();
							GuardExpression guard = entry.getValue().getExpression();

							if (classObj instanceof CombinedClass) {

								CombinedClass combinedClass = (CombinedClass) classObj;
								Iterator<Object> iterator = combinedClass.iterator();
								Object class1 = iterator.next();
								Object class2 = iterator.next();

								fixedEstimation.put(class1,
										FunctionEstimation.Factory.create(guard, updatedFScores.get(class1)));
								fixedEstimation.put(class2,
										FunctionEstimation.Factory.create(guard, updatedFScores.get(class2)));
							} else {
								fixedEstimation.put(classObj,
										FunctionEstimation.Factory.create(guard, updatedFScores.get(classObj)));
							}

						}

						return new PartitionEstimatorResult() {

							public Partition getPartition() {
								return partition;
							}

							public double getFMeasure() {
								return partitionEstimator.computeQualityMeasure();
							}

							public Map<Object, FunctionEstimation> getPartitionEstimation() {
								return fixedEstimation;
							}

							public AbstractClassifier getTree() {
								return partitionEstimator.getClassifier();
							}

						};
					}

				}));

			}

			for (Future<PartitionEstimatorResult> partitionEstimatorResultFuture : partitionedEstimators) {

				PartitionEstimatorResult partitionEstimatorResult = partitionEstimatorResultFuture.get();

				Partition partition = partitionEstimatorResult.getPartition();
				double newFScore = partitionEstimatorResult.getFMeasure();
				J48 partitionClassifier = (J48) partitionEstimatorResult.getTree();
				Map<Object, FunctionEstimation> partitionEstimation = partitionEstimatorResult
						.getPartitionEstimation();

				if (!mixEstimations) {

					//TODO this calculates wrong f-scores for the guards

					if (newFScore > currentFScore && partitionClassifier.measureNumLeaves() > 1) {

						System.out.println(String.format(
								"Improving f-score at decision point %s from %s to %s, using partition %s",
								decisionPointName, currentFScore, newFScore, partition));

						currentFScore = newFScore;
						estimation.clear();

						for (Entry<Object, FunctionEstimation> entry : partitionEstimation.entrySet()) {
							Object classObj = entry.getKey();
							estimation.put(classObj, entry.getValue());
						}

					}

				} else {

					for (Entry<Object, FunctionEstimation> newEstimation : partitionEstimation.entrySet()) {

						Object classObj = newEstimation.getKey();
						FunctionEstimation oldEstimation = estimation.get(classObj);
						boolean updated = updateIfBetter(oldEstimation, newEstimation.getValue(), classObj);
						if (updated) {
							logImprovement(partition, oldEstimation, newEstimation.getValue(), classObj);
							usedClassifiers.put(classObj, partitionClassifier);
						}

					}

				}

			}

			System.out.println(String.format("Checked %s partitions", partitionView.numPartitionsGenerated()));

		}
		return estimation;
	}

	private Map<Object, Double> reEvaluateClasses(Instances normalInstances, Instances pairWiseInstances,
			AbstractClassifier pairWiseClassifier, Map<Integer, Object> pairWiseClassToIndex) throws Exception {

		Map<Object, Double> fScoreMap = new HashMap<>();

		Multiset<Object> falsePositives = HashMultiset.create();
		Multiset<Object> falseNegatives = HashMultiset.create();
		Multiset<Object> truePositives = HashMultiset.create();

		for (int i = 0; i < normalInstances.size(); i++) {
			Instance instance = normalInstances.get(i);
			int priorWeka = (int) instance.value(normalInstances.classIndex());
			Object priorClass = normalEstimator.classIndexMap.inverse().get(priorWeka);

			Instance pairWiseInstance = pairWiseInstances.get(i);
			int classifiedWeka = (int) pairWiseClassifier.classifyInstance(pairWiseInstance);
			Object classifiedClass = pairWiseClassToIndex.get(classifiedWeka);

			if (priorClass != classifiedClass) {
				// Mis-Classification

				if (classifiedClass instanceof CombinedClass) {
					// It might now be combined
					CombinedClass combinedClass = (CombinedClass) classifiedClass;
					Iterator<Object> iterator = combinedClass.iterator();
					Object class1 = iterator.next();
					Object class2 = iterator.next();

					if (priorClass == class1) {
						truePositives.add(priorClass);
						// falsePositives.add(class2);
					} else if (priorClass == class2) {
						truePositives.add(priorClass);
						// falsePositives.add(class1);
					} else {
						falsePositives.add(class1);
						falsePositives.add(class2);
						falseNegatives.add(priorClass);
					}

				} else {
					falsePositives.add(classifiedClass);
					falseNegatives.add(priorClass);
				}

			} else {
				truePositives.add(priorClass);
			}
		}

		for (Object classObj : pairWiseClassToIndex.values()) {

			if (classObj instanceof CombinedClass) {
				CombinedClass combinedClass = (CombinedClass) classObj;

				Iterator<Object> iterator = combinedClass.iterator();
				Object class1 = iterator.next();
				Object class2 = iterator.next();

				fScoreMap.put(
						class1,
						calculateFScore(truePositives.count(class1), falsePositives.count(class1),
								falseNegatives.count(class1)));
				fScoreMap.put(
						class2,
						calculateFScore(truePositives.count(class2), falsePositives.count(class2),
								falseNegatives.count(class2)));
			} else {
				fScoreMap.put(
						classObj,
						calculateFScore(truePositives.count(classObj), falsePositives.count(classObj),
								falseNegatives.count(classObj)));
			}

		}

		return fScoreMap;

	}

	private static double calculateFScore(int tp, int fp, int fn) {
		double precision;
		double recall;
		
		if (fp == 0.0d) {
			precision = 1.0d;
		} else {
			precision = ((double) tp) / (tp + fp);
		}
			
		if (fn == 0.0d) {
			recall = 1.0d;
		} else {
			recall = ((double) tp) / (tp + fn);	
		}		
		if (precision == 0.0f && recall == 0.0f) {
			return 0.0f;
		} else {
			double fscore = (2 * precision * recall) / (precision + recall);
			return fscore;			
		}
	}

	private void logImprovement(Partition partition, FunctionEstimation oldEstimation,
			FunctionEstimation newEstimation, Object classObj) {
		System.out.println(String.format(
				"Improving f-score for transition %s at decision point %s from %s to %s, using partition %s", classObj,
				decisionPointName, (oldEstimation != null ? oldEstimation.getQualityMeasure() : "N/A"),
				newEstimation.getQualityMeasure(), partition));
	}

	private boolean updateIfBetter(FunctionEstimation oldEstimation, FunctionEstimation newEstimation,
			Object clazz) {
		if ((oldEstimation == null || Double.compare(oldEstimation.getQualityMeasure(), newEstimation.getQualityMeasure()) < 0)
				&& Double.compare(newEstimation.getQualityMeasure(), 0.0f) > 0) {
			estimation.put(clazz, newEstimation);
			return true;
		}
		return false;
	}

	private int numOutputClasses() {
		return outputClasses.length;
	}

	public String toString() {
		StringBuilder sBuilder = new StringBuilder();
		for (Entry<Object, FunctionEstimation> e : estimation.entrySet()) {
			AbstractClassifier transitionClassifier = usedClassifiers.get(e.getKey());
			if (transitionClassifier == null) {
				transitionClassifier = normalEstimator.getClassifier();
			}
			sBuilder.append(String.format("Transition %s was estimated using classifier:\n%s\n\n", e.getKey(),
					transitionClassifier));
		}
		return sBuilder.toString();
	}

	public double computeFMeasure() {
		return computeQualityMeasure();
	}
	
	public double computeQualityMeasure() {
		double sumFMeasures = 0;
		double toConsider = 0;
		for (Entry<Object, FunctionEstimation> e : estimation.entrySet()) {
			if (e.getValue().getQualityMeasure() > 0) {
				sumFMeasures += 1 / e.getValue().getQualityMeasure();
				toConsider++;
			}
		}
		if (toConsider != 0)
			return toConsider / sumFMeasures;
		else
			return 0;
	}

	public String getQualityMeasureName() {
		return "F-Score";
	}

	public int getNumInstances() {
		return normalEstimator.getNumInstances();
	}
	
	public double getSumOfWeights() {
		return normalEstimator.getInstances().sumOfWeights();
	}

	public void setMinNumObj(int i) {
		normalEstimator.setMinNumObj(i);
	}

	public void setUnpruned(boolean b) {
		normalEstimator.setUnpruned(b);
	}

	public void setBinarySplit(boolean selected) {
		normalEstimator.setBinarySplit(selected);
	}

	public void setCrossValidate(boolean validate) {
		normalEstimator.setCrossValidate(validate);
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