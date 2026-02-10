package org.processmining.datadiscovery;

import java.util.Map;
import java.util.Set;

import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;

abstract public class AbstractPetrinetDecisionRuleDiscovery<R extends RuleDiscovery.Rule, C extends RuleDiscoveryConfig> extends AbstractDecisionRuleDiscovery<R, C>
		implements PetrinetDecisionRuleDiscovery {

	protected static final class DecisionRulesResultImpl implements PetrinetDecisionRules {

		private final Map<Place, PetrinetDecisionRule> ruleMapping;

		private DecisionRulesResultImpl(Map<Place, PetrinetDecisionRule> ruleMapping) {
			this.ruleMapping = ruleMapping;
		}

		public Map<Place, PetrinetDecisionRule> getRules() {
			return ruleMapping;
		}

		public String toString() {
			return Joiner.on(",\n").withKeyValueSeparator("=").join(ruleMapping);
		}

	}

	public AbstractPetrinetDecisionRuleDiscovery(C config, Iterable<ProjectedTrace> projectedLog,
			Map<String, Type> attributeType, Map<String, Set<String>> literalValues, int numInstancesEstimate) {
		super(config, projectedLog, attributeType, literalValues, numInstancesEstimate);
	}

	public AbstractPetrinetDecisionRuleDiscovery(C config, Iterable<ProjectedTrace> projectedLog,
			int numInstancesEstimate) {
		super(config, projectedLog, numInstancesEstimate);
	}

	public final PetrinetDecisionRules discoverRules(Petrinet net) throws RuleDiscoveryException {
		Builder<Place, PetrinetDecisionRule> builder = ImmutableMap.<Place, PetrinetDecisionRule>builder();
		for (Place place : net.getPlaces()) {
			if (net.getOutEdges(place).size() > 1) {
				builder.put(place, discoverRulesForPlace(net, place));
			}
		}
		return new DecisionRulesResultImpl(builder.build());
	}

	private PetrinetDecisionRule convertToPetrinetRule(final Rule rule) {
		return new PetrinetDecisionRule() {

			@SuppressWarnings({ "unchecked", "rawtypes" }) // we know they are transition
			public Map<Transition, FunctionEstimation> getRulesForTransition() {
				return ((Map) rule.getRules());
			}

			public Place getDecisionPoint() {
				return (Place) rule.getDecisionPoint();
			}

			public Rule getRule() {
				return rule;
			}
		};
	}

	public final PetrinetDecisionRule discoverRulesForPlace(PetrinetGraph net, Place place)
			throws RuleDiscoveryException {
		Set<Transition> transitions = getTransitionPostset(net, place);
		Rule rule = discover(place, transitions);
		return convertToPetrinetRule(rule);
	}

	private static Set<Transition> getTransitionPostset(PetrinetGraph net, Place place) {
		Set<Transition> postSet = Sets.newHashSetWithExpectedSize(net.getOutEdges(place).size());
		for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> arc : net.getOutEdges(place)) {
			postSet.add((Transition) arc.getTarget());
		}
		return postSet;
	}

}
