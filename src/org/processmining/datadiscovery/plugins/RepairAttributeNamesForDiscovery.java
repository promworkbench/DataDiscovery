package org.processmining.datadiscovery.plugins;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.log.utils.XUtils;

/**
 * Repairs the attribute names by cleaning them from disallowed characters for
 * discovery.
 * 
 * @author F. Mannhardt
 * 
 */
public final class RepairAttributeNamesForDiscovery {

	public RepairAttributeNamesForDiscovery() {
		super();
	}

	@Plugin(name = "Repair Attributes Names for Data Discovery", parameterLabels = {
			"Event Log" }, returnLabels = {}, returnTypes = {}, userAccessible = true, mostSignificantResult = -1, help = "Repairs the attribute names in the log such that it is suited for discovering data guards. Weka cannot deal with certain special characters (such as '-', '.', '~' and '*'). Those characters are removed. Throws an error if this conversion causes overwriting an existing attribute.")
	@UITopiaVariant(affiliation = UITopiaVariant.EHV, author = " F. Mannhardt", email = "f.mannhardt@tue.nl")
	public void repairEventAttributeDataTypes(final UIPluginContext context, XLog log) {

		for (XTrace trace : log) {
			repairAttributes(trace.getAttributes());
			for (XEvent event : trace) {
				repairAttributes(event.getAttributes());
			}
		}

	}

	private void repairAttributes(XAttributeMap attributes) {
		List<XAttribute> changedAttributes = new ArrayList<>();
		Iterator<XAttribute> iterator = attributes.values().iterator();
		while (iterator.hasNext()) {
			XAttribute a = iterator.next();
			if (containsForbiddenChars(a.getKey())) {
				iterator.remove();
				changedAttributes.add(XUtils.cloneAttributeWithChangedKey(a, removeForbiddenChars(a.getKey())));
			}
		}
		for (XAttribute a : changedAttributes) {
			if (attributes.containsKey(a.getKey())) {
				throw new IllegalArgumentException(
						"Cannot repair this event log. Name collision on attribute " + a.getKey());
			}
			attributes.put(a.getKey(), a);
		}
	}

	private String removeForbiddenChars(String s) {
		StringBuilder sb = new StringBuilder();
		int utfChar = s.codePointAt(0);
		for (int i = 0; i < s.length(); i += Character.charCount(utfChar)) {
			utfChar = s.codePointAt(i);
			if (utfChar != 65279) { //ignore BOM
				char[] chars = Character.toChars(utfChar);
				for (char c : chars) {
					if (!containsWekaForbiddenChar(c)) {
						sb.append(c);
					}
				}
			}
		}
		return sb.toString();
	}

	private boolean containsForbiddenChars(String s) {
		int utfChar = s.codePointAt(0);
		for (int i = 0; i < s.length(); i += Character.charCount(utfChar)) {
			utfChar = s.codePointAt(i);
			if (utfChar == 65279) { //ignore BOM
				return true;
			} else {
				char[] chars = Character.toChars(utfChar);
				for (char c : chars) {
					if (containsWekaForbiddenChar(c)) {
						return true;
					}
				}
			}
		}
		return true;
	}

	private boolean containsWekaForbiddenChar(char c) {
		switch (c) {
			case '-' :
			case '.' :
			case '~' :
			case '*' :
				return true;
			default :
				return false;
		}
	}
}
