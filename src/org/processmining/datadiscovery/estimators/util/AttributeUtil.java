package org.processmining.datadiscovery.estimators.util;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.deckfour.xes.model.XAttributable;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.weka.WekaUtil;
import org.processmining.log.utils.XUtils;

public final class AttributeUtil {

	private AttributeUtil() {
	}

	public static Map<String, Type> calcAttributeTypes(Iterable<XTrace> traces, Set<String> attributes) {
		Map<String, Type> attributeTypes = new HashMap<>();
		for (XTrace trace : traces) {
			updateAttributeType(attributeTypes, trace, attributes);
			for (XEvent event : trace) {
				updateAttributeType(attributeTypes, event, attributes);
			}

		}
		return attributeTypes;
	}

	private static void updateAttributeType(Map<String, Type> attributeTypes, XAttributable attributable,
			Set<String> consideredAttributes) {
		XAttributeMap attributes = attributable.getAttributes();
		for (String key : consideredAttributes) {
			String fixedVarName = WekaUtil.fixVarName(key);
			// Uses the first occurrence and assumes data types to be consistent!
			if (!attributeTypes.containsKey(fixedVarName)) {
				XAttribute attribute = attributes.get(key);
				if (attribute != null) {
					Object value = XUtils.getAttributeValue(attribute);
					Type classType = generateDataElement(value);
					if (classType != null) {
						attributeTypes.put(fixedVarName, classType);
					}
				}
			}
		}
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

	public static Map<String, Set<String>> calcLiteralValues(Iterable<XTrace> traces, Set<String> attributes) {
		Map<String, Set<String>> literalValues = new HashMap<>();
		for (XTrace trace : traces) {
			updateLiteralValues(literalValues, trace, attributes);
			for (XEvent event : trace) {
				updateLiteralValues(literalValues, event, attributes);
			}
		}
		return literalValues;
	}

	private static void updateLiteralValues(Map<String, Set<String>> literalValueMap, XAttributable attributable,
			Set<String> consideredAttributes) {
		XAttributeMap attributes = attributable.getAttributes();
		for (String key : consideredAttributes) {
			String varName = WekaUtil.fixVarName(key);
			XAttribute attribute = attributes.get(key);
			if (attribute != null) {
				Object value = XUtils.getAttributeValue(attribute);
				if (value instanceof String) {
					Set<String> literalValues = literalValueMap.get(varName);
					if (literalValues == null) {
						literalValues = new TreeSet<>();
						literalValueMap.put(varName, literalValues);
					}
					literalValues.add((String) value);
				}
			}
		}
	}

}