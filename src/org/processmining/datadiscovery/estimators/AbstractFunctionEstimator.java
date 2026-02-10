package org.processmining.datadiscovery.estimators;

import java.util.HashMap;
import java.util.Map;

import org.processmining.datadiscovery.estimators.weka.WekaTreeClassificationAdapter.WekaLeafNode;
import org.processmining.datapetrinets.expression.GuardExpression;

import com.google.common.base.Function;

abstract public class AbstractFunctionEstimator implements FunctionEstimator {

	/**
	 * Converts the supplied {@link WekaLeafNode}s to a {@link Map} of {@link FunctionEstimation}s.
	 * 
	 * @param wekaLeafsNodes
	 * @param classMapping
	 * @param evaluation 
	 * @return
	 * @throws org.processmining.datapetrinets.expression.syntax.ParseException
	 */
	protected static Map<Object, FunctionEstimation> buildExpressionsFromLeafs(Iterable<WekaLeafNode> wekaLeafsNodes,
			Function<String, Object> classMapping) throws org.processmining.datapetrinets.expression.syntax.ParseException {
		Map<Object, FunctionEstimation> expressionEstimation = new HashMap<>();
		for (WekaLeafNode leaf : wekaLeafsNodes) {
			if (leaf.getInstanceCount() > 0.0) {
				Object leafClass = classMapping.apply(leaf.getClassName());
				FunctionEstimation existingCondition = expressionEstimation.get(leafClass);
				if (existingCondition != null) {
					GuardExpression newCondition = GuardExpression.Operation.or(existingCondition.getExpression(),
							leaf.getExpression());
					expressionEstimation.put(leafClass, FunctionEstimation.Factory.create(newCondition, -1d));
				} else {
					GuardExpression expression = leaf.getExpression();
					if (expression != null) {
						expressionEstimation.put(leafClass, FunctionEstimation.Factory.create(expression, -1d));
					}
				}				
			}
		}
		
		return expressionEstimation;
	}

}
