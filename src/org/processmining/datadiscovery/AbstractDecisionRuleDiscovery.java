package org.processmining.datadiscovery;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.weka.WekaUtil;

import com.google.common.base.Function;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;

public abstract class AbstractDecisionRuleDiscovery<R extends RuleDiscovery.Rule, C extends RuleDiscoveryConfig>
		implements RuleDiscovery<R> {

	static class RuleImpl implements Rule {

		private final Map<Object, FunctionEstimation> estimation;
		private final Object decisionPoint;

		private String estimatorInfo;

		public RuleImpl(Object decisionPoint, Map<Object, FunctionEstimation> estimation, FunctionEstimator estimator) {
			this.decisionPoint = decisionPoint;
			this.estimation = estimation;
			this.estimatorInfo = estimator.toString();
		}

		public Map<Object, FunctionEstimation> getRules() {
			return estimation;
		}

		public String toString() {
			return estimatorInfo;
		}

		public Object getDecisionPoint() {
			return decisionPoint;
		}

		public Object classify(Map<String, Object> attributes) {
			// assume mutually-exclusive rules
			for (Entry<Object, FunctionEstimation> entry : estimation.entrySet()) {
				if (entry.getValue().getExpression().isTrue(attributes)) {
					return entry.getKey();
				}
			}
			return null;
		}

	}

	private static final Object NULL = new Object();

	protected final ProjectedLog projectedLog;
	protected final Map<String, Type> attributeType;
	protected final Map<String, Set<String>> literalValues;
	protected final C config;
	private final int numInstancesEstimate;

	public AbstractDecisionRuleDiscovery(C config, Iterable<ProjectedTrace> projectedLog, int numInstancesEstimate) {
		this(config, projectedLog, extractAttributeInformation(projectedLog), getLiteralValuesMap(projectedLog),
				numInstancesEstimate); //TODO join both literal values map and attribute type info in one data structure
	}

	private static Map<String, Set<String>> getLiteralValuesMap(Iterable<ProjectedTrace> projectedLog) {
		Map<String, Set<String>> retValue = new HashMap<>();
		for (ProjectedTrace trace : projectedLog) {
			//TODO add trace attributes
			for (ProjectedEvent event : trace) {
				for (String attributeKey : event.getAttributes()) {
					Object value = event.getAttributeValue(attributeKey);
					if (value instanceof String) {
						String varName = escapeAttributeName(attributeKey);
						Set<String> literalValues = retValue.get(varName);
						if (literalValues == null) {
							literalValues = new TreeSet<>();
							retValue.put(varName, literalValues);
						}
						literalValues.add((String) value);
					}
				}
			}
		}
		return retValue;
	}

	private static Map<String, Type> extractAttributeInformation(Iterable<ProjectedTrace> projectedLog) {
		HashMap<String, Type> retValue = new HashMap<>();
		for (ProjectedTrace trace : projectedLog) {
			//TODO add trace attributes
			for (ProjectedEvent event : trace) {
				for (String attributeKey : event.getAttributes()) {
					String fixedVarName = escapeAttributeName(attributeKey);
					// Uses the first occurrence and assumes data types to be consistent!
					if (!retValue.containsKey(fixedVarName)) {
						Object value = event.getAttributeValue(attributeKey);
						Type classType = generateDataElement(value);
						if (classType != null) {
							retValue.put(fixedVarName, classType);
						}
					}
				}
			}

		}
		return retValue;
	}

	private static Type generateDataElement(Object value) {
		if (value instanceof Boolean) {
			return Type.BOOLEAN;
		} else if (value instanceof Double || value instanceof Float) {
			return Type.CONTINUOS;
		} else if (value instanceof Long || value instanceof Integer) {
			return Type.DISCRETE;
		} else if (value instanceof Date) {
			return Type.TIMESTAMP;
		} else if (value instanceof String) {
			return Type.LITERAL;
		} else {
			// Non "primitive" types like list are not supported
			return null;
		}
	}

	public AbstractDecisionRuleDiscovery(C config, final Iterable<ProjectedTrace> projectedLog,
			Map<String, Type> attributeType, Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		this.config = config;
		if (projectedLog instanceof ProjectedLog) {
			this.projectedLog = (ProjectedLog) projectedLog; //TODO change after all callers have been updated to avoid Hudson issues	
		} else {
			this.projectedLog = new ProjectedLog() {

				public Iterator<ProjectedTrace> iterator() {
					return projectedLog.iterator();
				}

				public Object getInitialValue(String attributeName) {
					return null;
				}

				public Set<String> getAttributes() {
					return ImmutableSet.of();
				}
			};
		}
		this.attributeType = attributeType;
		this.literalValues = literalValues;
		this.numInstancesEstimate = numInstancesEstimate;
	}

	protected abstract void configureWithInstance(FunctionEstimator estimator);

	protected abstract R newRule(Object decisionPoint, FunctionEstimator estimator,
			Map<Object, FunctionEstimation> estimation);

	protected int getNumInstanceEstimate() {
		return numInstancesEstimate;
	}

	protected final R doDiscoverDecisionTree(Object decisionPoint, Set<? extends Object> transitions,
			FunctionEstimator estimator) throws RuleDiscoveryException {
		
		//TODO check how to fix this in weka
		// Workaround for bug in Weka, weighted instance seem to fail with one attribute
		if (projectedLog.getAttributes().size() == 1) {
			config.setUseWeights(false);
		}

		// Order: 1st add instances, then configure estimator

		final Map<Object, Multiset<Map<String, Object>>> instances = new HashMap<>();
		for (Object clazz : transitions) {
			instances.put(clazz, HashMultiset.<Map<String, Object>>create());
		}
		final Map<String, Object> escapedInitialAttributes = getEscapedInitialAttributes(projectedLog);
		final Map<String, Object> currentAttributeValues = new HashMap<>();
		for (ProjectedTrace trace : projectedLog) {
			currentAttributeValues.putAll(escapedInitialAttributes);
			Object lastActivity = null;
			for (Iterator<ProjectedEvent> iterator = trace.iterator(); iterator.hasNext();) {
				ProjectedEvent e = iterator.next();
				if (config.isMinePrimeGuards()) {
					for (String attributeKey : e.getAttributes()) {
						// NULL is used as marker for missing values
						Object value = e.getAttributeValue(attributeKey);
						String escapedKey = escapeAttributeName(attributeKey).concat("'");
						if (value == null) {
							currentAttributeValues.put(escapedKey, NULL);
						} else {
							currentAttributeValues.put(escapedKey, value);
						}
					}
				}
				final Object activity = e.getActivity();
				// only add instance if 
				// decision point is NULL 
				// or the class does not directly follow the decision point 
				// or when the last activity equals the decision point 
				if (!config.isMineDirectlyFollowingClasses() || decisionPoint == null
						|| decisionPoint.equals(lastActivity)) {
					if (activity != null && transitions.contains(activity)) {
						instances.get(activity).add(ImmutableMap.copyOf(currentAttributeValues));
					}
				}
				// Update current values					
				for (String attributeKey : e.getAttributes()) {
					// NULL is used as marker for missing values
					Object value = e.getAttributeValue(attributeKey);
					String escapedKey = escapeAttributeName(attributeKey);
					if (value == null) {
						currentAttributeValues.put(escapedKey, NULL);
					} else {
						currentAttributeValues.put(escapedKey, value);
					}
				}
				lastActivity = activity;
			}
			currentAttributeValues.clear();
		}

		try {
			for (Entry<Object, Multiset<Map<String, Object>>> classEntry : instances.entrySet()) {
				for (com.google.common.collect.Multiset.Entry<Map<String, Object>> instanceEntry : classEntry.getValue()
						.entrySet()) {
					Map<String, Object> attributesWithNull = Maps.transformValues(instanceEntry.getElement(),
							new Function<Object, Object>() {

								public Object apply(Object val) {
									if (val == NULL) {
										return null;
									} else {
										return val;
									}
								}
							});
					if (config.isUseWeights()) {
						estimator.addInstance(attributesWithNull, classEntry.getKey(), instanceEntry.getCount());
					} else {
						for (int i = 0; i < instanceEntry.getCount(); i++) {
							estimator.addInstance(attributesWithNull, classEntry.getKey(), 1.0f);
						}
					}
				}
			}
		} catch (Exception ex) {
			throw new RuleDiscoveryException(ex);
		}

		// Configuring the estimator might need to know about the number of instances !!!
		configureWithInstance(estimator);

		try {
			Map<Object, FunctionEstimation> estimation = estimator.getFunctionEstimation(null);
			return newRule(decisionPoint, estimator, estimation);
		} catch (Exception ex) {
			throw new RuleDiscoveryException("An error occured while discovering a rule for place " + decisionPoint,
					ex);
		} catch (StackOverflowError error) {
			throw new RuleDiscoveryException(
					"One of the rules discovered at place '" + decisionPoint
							+ "' has too many levels of nesting! A stack overflow occured while processing this rule. Probably the used classifier returned a tree with too many leafs.",
					error);
		}
	}

	private Map<String, Object> getEscapedInitialAttributes(ProjectedLog projectedLog) {
		Builder<String, Object> valueBuilder = ImmutableMap.builder();
		for (String attribute : projectedLog.getAttributes()) {
			Object value = projectedLog.getInitialValue(attribute);
			if (value != null) {
				valueBuilder.put(escapeAttributeName(attribute), value);
			} else {
				valueBuilder.put(escapeAttributeName(attribute), NULL);
			}
		}
		return valueBuilder.build();
	}

	public static String escapeAttributeName(String attribute) {
		//TODO find something better than this, unfortunately WEKA is rather strict on attribute names
		return WekaUtil.fixVarName(attribute);
	}

}