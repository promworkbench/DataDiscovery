package org.processmining.datadiscovery.model;

import java.util.HashMap;
import java.util.Map;

import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithData;

public class DiscoveredPetriNetWithData extends PetriNetWithData implements DiscoveredDataPetriNet
{
	private final Map<Place, DecisionPointResult> map= new HashMap<>();

	public DiscoveredPetriNetWithData(String netName) {
		super(netName);
	}

	public void storeDecisionPointResult(Place place, DecisionPointResult f)
	{
		if (this.getPlaces().contains(place))
			map.put(place, f);
		else
			throw(new IllegalArgumentException("Place "+place+" is not part of the net"));
	}
	
	public DecisionPointResult getDecisionPointResult(Place p)
	{
		return map.get(p);
	}

}
