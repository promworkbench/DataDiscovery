package org.processmining.datadiscovery;

import java.util.Set;

public interface ProjectedTrace extends Iterable<ProjectedEvent> {
	Object getAttributeValue(String attributeName);

	Set<String> getAttributes();
}