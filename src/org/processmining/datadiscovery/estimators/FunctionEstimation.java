package org.processmining.datadiscovery.estimators;

import org.processmining.datapetrinets.expression.GuardExpression;

public interface FunctionEstimation {

	public static class Factory {

		private Factory() {
			super();
		}

		public static FunctionEstimation create(final GuardExpression expression, final Double qualityMeasure) {
			return new FunctionEstimation() {

				public Double getQualityMeasure() {
					return qualityMeasure;
				}

				public GuardExpression getExpression() {
					return expression;
				}
			};
		}
	}

	GuardExpression getExpression();

	Double getQualityMeasure();
}