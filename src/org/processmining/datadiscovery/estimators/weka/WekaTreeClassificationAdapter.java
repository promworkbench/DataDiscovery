package org.processmining.datadiscovery.estimators.weka;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.expression.syntax.ParseException;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.BoundType;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.TreeTraverser;

import weka.core.Attribute;
import weka.core.Drawable;
import weka.core.Instances;
import weka.gui.treevisualizer.Edge;
import weka.gui.treevisualizer.Node;
import weka.gui.treevisualizer.TreeBuild;

/**
 * Support variouos parsing tasks based on the {@link Drawable} tree
 * classification output of Weka.
 * 
 * @author F. Mannhardt
 *
 */
public class WekaTreeClassificationAdapter {

	private static final String DATE_FORMAT_PATTERN = "EEE MMM dd kk:mm:ss zzz yyyy";
	private static final String BOOLEAN_TRUE = "T";

	private final class WekaTreeTraverser extends TreeTraverser<Node> {
		public Iterable<Node> children(final Node n) {
			AbstractIterator<Node> iterator = new AbstractIterator<Node>() {

				private int i = 0;

				protected Node computeNext() {
					Edge child = n.getChild(i++);
					if (child != null) {
						return child.getTarget();
					} else {
						endOfData();
						return null;
					}
				}
			};
			return ImmutableList.copyOf(iterator);
		}
	}

	public final static class WekaCondition {

		private final String attributeName;
		private final String operator;
		private final String value;

		private final Attribute attribute;
		private final Type attributeType;

		/**
		 * Creates a new {@link WekaCondition} based on the parsing results.
		 * 
		 * @param attributeName
		 * @param restriction
		 *            assumes a format ' = VALUE' or '> VALUE'
		 * @param attribute
		 * @param attributeType
		 */
		public WekaCondition(String attributeName, String restriction, Attribute attribute, Type attributeType) {
			String wekaUnescape = WekaUtil.wekaUnescape(attributeName);
			if (!GuardExpression.Factory.isValidVariableIdentifier(wekaUnescape)) {
				this.attributeName = GuardExpression.Factory.transformToVariableIdentifier(wekaUnescape);
			} else {
				this.attributeName = wekaUnescape;
			}
			this.attribute = attribute;
			this.attributeType = attributeType;
			// Trim whitespace
			restriction = restriction.trim();
			int indexOfWhiteSpace = restriction.indexOf(' ');
			this.operator = restriction.substring(0, indexOfWhiteSpace);
			this.value = restriction.substring(indexOfWhiteSpace + 1);
		}

		public String getAttributeName() {
			return attributeName;
		}

		public String getValue() {
			return value;
		}

		public String toExpressionString() {
			String expressionValue;
			String expressionOperator;

			// Fix for Weka numeric omitting the 0 in for example '0.1'
			if (attribute.isNumeric() && value.charAt(0) == '.') {
				expressionValue = "0" + value;
			} else {
				expressionValue = value;
			}

			if (operator.equals("=")) {
				expressionOperator = "==";
			} else {
				expressionOperator = operator;
			}

			if (attribute.isString() || attribute.isNominal()) {
				if (attributeType == Type.BOOLEAN) {
					Boolean boolVal = BOOLEAN_TRUE.equals(expressionValue) ? true : false;
					return String.format("%s %s %s", getAttributeName(), expressionOperator, boolVal);
				} else {
					return String.format("%s %s \"%s\"", getAttributeName(), expressionOperator, expressionValue);
				}
			} else {
				if (attributeType == Type.TIMESTAMP) {
					SimpleDateFormat df = new SimpleDateFormat(DATE_FORMAT_PATTERN, Locale.US);
					return String.format("%s %s \"%s\"", getAttributeName(), expressionOperator,
							df.format(new Date((long) Double.parseDouble(expressionValue))));
				} else {
					return String.format("%s %s %s", getAttributeName(), expressionOperator, expressionValue);
				}
			}
		}

		public String toString() {
			return String.format("%s %s %s", getAttributeName(), operator, value);
		}

		public String getOperator() {
			return operator;
		}

	}

	public static class WekaNode {

		private static final Pattern WEKA_LABEL_PATTERN = Pattern.compile("([0-9]+:\\s)?(.+?)");

		private final Node node;
		private final WekaCondition condition;

		public WekaNode(Node node, Instances instances, Map<String, Type> attributeType) {
			this.node = node;
			if (node.getParent(0) != null) {
				String attributeName = extractAttributeName(node.getParent(0).getSource().getLabel());
				Attribute attribute = instances.attribute(attributeName);
				String operator = node.getParent(0).getLabel();
				condition = new WekaCondition(attributeName, operator, attribute, attributeType.get(attribute.name()));
			} else {
				condition = null;
			}
		}

		public WekaNode(Node node, WekaCondition condition) {
			this.node = node;
			this.condition = condition;
		}

		protected String extractAttributeName(String label) {
			Matcher matcher = WEKA_LABEL_PATTERN.matcher(label);
			if (matcher.matches()) {
				// Ignore first part which is added by some tree implementations
				return matcher.group(2);
			} else {
				throw new IllegalArgumentException("Missing information in WEKA node " + label
						+ ". Does not fulfil regular expression: " + WEKA_LABEL_PATTERN.pattern());
			}
		}

		public WekaCondition getCondition() {
			return condition;
		}

		public boolean hasCondition() {
			return condition != null;
		}

		public String toString() {
			if (hasCondition()) {
				return condition.toString();
			} else {
				return "ROOT";
			}
		}

		protected Node getOriginalNode() {
			return node;
		}

	}

	public final static class WekaLeafNode extends WekaNode {

		private static final Pattern WEKA_LEAF_PATTERN = Pattern.compile(
				"([0-9]+\\s:\\s)?(.*?)\\s\\(([0-9]*\\.?[0-9]+(E[0-9]+)?)/?([0-9]*\\.?[0-9]+(E[0-9]+)?)?\\)(.*)?");

		private final String className;
		private final double instanceCount;
		private final double wrongInstanceCount;

		private final Deque<WekaCondition> conditions;

		public WekaLeafNode(Node node, Instances instances, Map<String, Type> attributeType) {
			super(node, instances, attributeType);
			/*
			 * if (node.getParent(0) != null) { parent =
			 * node.getParent(0).getSource(); } else { parent = null; }
			 */
			Matcher matcher = WEKA_LEAF_PATTERN.matcher(node.getLabel());
			if (matcher.matches()) {
				className = matcher.group(2);
				instanceCount = Double.parseDouble(matcher.group(3));
				String group = matcher.group(5);
				if (group != null) {
					wrongInstanceCount = Double.parseDouble(group);
				} else {
					wrongInstanceCount = 0.0d;
				}
			} else {
				throw new IllegalArgumentException("Missing information in WEKA leaf node " + node.getLabel()
						+ ". Does not fulfil regular expression: " + WEKA_LEAF_PATTERN.pattern());
			}

			// Walk up the tree and collect all conditions associates with this leaf
			conditions = new ArrayDeque<>();
			Node currentNode = node;
			while (currentNode.getParent(0) != null) {
				String attributeName = extractAttributeName(currentNode.getParent(0).getSource().getLabel());
				Attribute attribute = instances.attribute(attributeName);
				String operator = currentNode.getParent(0).getLabel();
				WekaCondition wekaCondition = new WekaCondition(attributeName, operator, attribute,
						attributeType.get(attribute.name()));
				conditions.push(wekaCondition);
				currentNode = currentNode.getParent(0).getSource();
			}

			simplifyConditions(instances, attributeType);

		}

		private void simplifyConditions(Instances instances, Map<String, Type> attributeType) {
			Map<String, Range<Double>> numericAttributeRanges = new HashMap<>();
			Iterator<WekaCondition> iterator = conditions.iterator();
			while (iterator.hasNext()) {
				WekaCondition condition = iterator.next();
				if (attributeType.get(condition.getAttributeName()) == Type.CONTINUOS
						|| attributeType.get(condition.getAttributeName()) == Type.DISCRETE) {
					iterator.remove();
					Range<Double> range = numericAttributeRanges.get(condition.getAttributeName());
					switch (condition.getOperator()) {
						case ">" :
							if (range != null) {
								range = range.intersection(Range.greaterThan(Double.parseDouble(condition.getValue())));
							} else {
								range = Range.greaterThan(Double.parseDouble(condition.getValue()));
							}
							numericAttributeRanges.put(condition.getAttributeName(), range);
							break;
						case "<" :
							if (range != null) {
								range = range.intersection(Range.lessThan(Double.parseDouble(condition.getValue())));
							} else {
								range = Range.lessThan(Double.parseDouble(condition.getValue()));
							}
							numericAttributeRanges.put(condition.getAttributeName(), range);
							break;
						case "<=" :
							if (range != null) {
								range = range.intersection(Range.atMost(Double.parseDouble(condition.getValue())));
							} else {
								range = Range.atMost(Double.parseDouble(condition.getValue()));
							}
							numericAttributeRanges.put(condition.getAttributeName(), range);
							break;
						case ">=" :
							if (range != null) {
								range = range.intersection(Range.atLeast(Double.parseDouble(condition.getValue())));
							} else {
								range = Range.atLeast(Double.parseDouble(condition.getValue()));
							}
							numericAttributeRanges.put(condition.getAttributeName(), range);
							break;
						default :
							throw new RuntimeException("Unexpected operator " + condition);
					}
				}
			}

			for (Entry<String, Range<Double>> numericRange : numericAttributeRanges.entrySet()) {
				String attributeName = numericRange.getKey();
				Attribute attribute = instances.attribute(attributeName);
				Type type = attributeType.get(attribute.name());
				Range<Double> range = numericRange.getValue();
				if (range.hasLowerBound() && range.hasUpperBound()) {
					conditions.push(new WekaCondition(attributeName,
							convertUpperBoundOperator(range.upperBoundType()) + " " + range.upperEndpoint(), attribute,
							type));
					conditions.push(new WekaCondition(attributeName,
							convertLowerBoundOperator(range.lowerBoundType()) + " " + range.lowerEndpoint(), attribute,
							type));
				} else if (range.hasLowerBound()) {
					conditions.push(new WekaCondition(attributeName,
							convertLowerBoundOperator(range.lowerBoundType()) + " " + range.lowerEndpoint(), attribute,
							type));
				} else if (range.hasUpperBound()) {
					conditions.push(new WekaCondition(attributeName,
							convertUpperBoundOperator(range.upperBoundType()) + " " + range.upperEndpoint(), attribute,
							type));
				}
			}
		}

		private static String convertLowerBoundOperator(BoundType boundType) {
			switch (boundType) {
				case OPEN :
					return ">";
				case CLOSED :
					return ">=";
			}
			throw new IllegalArgumentException("Invalid bound type " + boundType);
		}

		private static String convertUpperBoundOperator(BoundType boundType) {
			switch (boundType) {
				case OPEN :
					return "<";
				case CLOSED :
					return "<=";
			}
			throw new IllegalArgumentException("Invalid bound type " + boundType);
		}

		public WekaLeafNode(Instances instances, String className, Deque<WekaCondition> conditions,
				double instanceCount, double wrongInstanceCount, Map<String, Type> attributeType) {
			super(null, !conditions.isEmpty() ? conditions.getLast() : null);
			this.className = className;
			this.conditions = conditions;
			this.instanceCount = instanceCount;
			this.wrongInstanceCount = wrongInstanceCount;
			simplifyConditions(instances, attributeType);
		}

		public String toString() {
			return String.format("%s (%s/%s) with conditions %s", className, instanceCount, wrongInstanceCount,
					getConditions());
		}

		public String getClassName() {
			return className;
		}

		public Deque<WekaCondition> getConditions() {
			return conditions;
		}

		public String getLeafCondition() {
			return conditions.peekLast().toString();
		}

		public double getInstanceCount() {
			return instanceCount;
		}

		public double getWrongInstanceCount() {
			return wrongInstanceCount;
		}

		public GuardExpression getExpression() throws ParseException {
			if (conditions.isEmpty()) {
				return null;
			}
			StringBuilder sBuilder = new StringBuilder();
			Iterator<WekaCondition> iterator = conditions.iterator();
			for (int i = 0; i < conditions.size() - 1; i++) {
				sBuilder.append("(");
			}

			boolean first = true;
			while (iterator.hasNext()) {
				WekaCondition condition = iterator.next();
				sBuilder.append("(");
				sBuilder.append(condition.toExpressionString());
				sBuilder.append(")");
				if (!first) {
					sBuilder.append(")");
				} else {
					first = false;
				}
				if (iterator.hasNext()) {
					sBuilder.append(" && ");
				}
			}

			return GuardExpression.Factory.newInstance(sBuilder.toString());
		}

	}

	private final Node rootNote;
	private final Drawable treeClassifier;
	private final Instances dataset;
	private final Map<String, Type> attributeType;

	public WekaTreeClassificationAdapter(Drawable treeClassifier, final Instances dataset,
			Map<String, Type> attributeType) {
		super();
		if (treeClassifier.graphType() != Drawable.TREE) {
			throw new IllegalArgumentException("Only supports tree-based classifiers!");
		}
		this.treeClassifier = treeClassifier;
		this.dataset = dataset;
		this.attributeType = attributeType;
		try {
			this.rootNote = new TreeBuild().create(new StringReader(treeClassifier.graph()));
		} catch (Exception e) {
			throw new RuntimeException("Could not parse WEKA result graph!", e);
		}
	}

	public FluentIterable<WekaNode> preOrderTraversal() {
		return treeTraverser().preOrderTraversal(getRootNode());
	}

	public FluentIterable<WekaNode> postOrderTraversal() {
		return treeTraverser().postOrderTraversal(getRootNode());
	}

	public TreeTraverser<WekaNode> treeTraverser() {
		final WekaTreeTraverser wekaTreeTraverser = new WekaTreeTraverser();
		return new TreeTraverser<WekaTreeClassificationAdapter.WekaNode>() {

			public Iterable<WekaNode> children(WekaNode node) {
				return Iterables.transform(wekaTreeTraverser.children(node.getOriginalNode()),
						new Function<Node, WekaNode>() {

							public WekaNode apply(Node node) {
								if (node.getChild(0) == null) {
									return new WekaLeafNode(node, dataset, attributeType);
								} else {
									return new WekaNode(node, dataset, attributeType);
								}
							}
						});
			}
		};
	}

	public FluentIterable<WekaLeafNode> traverseLeafNodes() {
		return treeTraverser().preOrderTraversal(getRootNode()).filter(new Predicate<WekaNode>() {

			public boolean apply(WekaNode n) {
				return n instanceof WekaLeafNode;
			}
		}).transform(new Function<WekaNode, WekaLeafNode>() {

			public WekaLeafNode apply(WekaNode n) {
				return (WekaLeafNode) n;
			}
		});
	}

	public WekaNode getRootNode() {
		if (rootNote.getChild(0) == null) {
			// single leaf root
			return new WekaLeafNode(rootNote, dataset, attributeType);
		}
		return new WekaNode(rootNote, dataset, attributeType);
	}

	public int treeDepth() {
		WekaTreeTraverser wekaTreeTraverser = new WekaTreeTraverser();
		return treeDepth(rootNote, wekaTreeTraverser);
	}

	public int treeDepth(Node node) {
		return treeDepth(node, new WekaTreeTraverser());
	}

	private int treeDepth(Node node, WekaTreeTraverser traverser) {
		if (node == null) {
			return 0;
		} else {
			int childDepth = -1;
			Iterable<Node> iterable = traverser.children(node);
			for (Node n : iterable) {
				childDepth = Math.max(childDepth, treeDepth(n, traverser));
			}
			return 1 + childDepth;
		}
	}

	public String toString() {
		return treeClassifier.toString();
	}

}
