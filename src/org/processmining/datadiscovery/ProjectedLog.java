package org.processmining.datadiscovery;

import java.util.Set;

public interface ProjectedLog extends Iterable<ProjectedTrace> {
	Set<String> getAttributes();

	Object getInitialValue(String attributeName);
}