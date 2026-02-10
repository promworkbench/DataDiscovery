package org.processmining.datadiscovery.estimators;

import java.util.Date;

/**
 * Data type for discovery
 */
public enum Type {
	LITERAL(String.class), TIMESTAMP(Date.class), DISCRETE(Long.class), CONTINUOS(Double.class), BOOLEAN(Boolean.class);

	private final Class<?> javaType;

	private Type(Class<?> javaType) {
		this.javaType = javaType;
	}

	public Class<?> getJavaType() {
		return javaType;
	}

}