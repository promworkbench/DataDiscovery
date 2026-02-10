package org.processmining.datadiscovery;

import java.util.Map;

import org.processmining.datadiscovery.RuleDiscovery.Rule;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

public interface PetrinetDecisionRuleDiscovery {

	public interface PetrinetDecisionRule {
		Rule getRule();
		Place getDecisionPoint();
		Map<Transition, FunctionEstimation> getRulesForTransition();
	}

	public interface PetrinetDecisionRules {
		Map<Place, PetrinetDecisionRule> getRules();
	}

	public PetrinetDecisionRule discoverRulesForPlace(PetrinetGraph net, Place place) throws RuleDiscoveryException;

	public PetrinetDecisionRules discoverRules(Petrinet net) throws RuleDiscoveryException;

}