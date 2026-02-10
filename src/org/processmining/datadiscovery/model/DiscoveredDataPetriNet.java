package org.processmining.datadiscovery.model;

import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;

public interface DiscoveredDataPetriNet extends DataPetriNet {

	DecisionPointResult getDecisionPointResult(Place place);

}
