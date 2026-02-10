package org.processmining.datadiscovery.visualizers;

import javax.swing.JComponent;

import org.processmining.contexts.uitopia.annotations.Visualizer;
import org.processmining.datadiscovery.model.DiscoveredDataPetriNet;
import org.processmining.datapetrinets.utils.MarkingsHelper;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.semantics.petrinet.Marking;

@Plugin(name = "Discovered Data Petri net (Graphviz)", level = PluginLevel.PeerReviewed, returnLabels = {
		"Discovered Data Petri net (Graphviz)" }, returnTypes = {
				JComponent.class }, userAccessible = true, parameterLabels = { "Matching Instances" })
@Visualizer
public class DiscoveredDPNGraphvizVisualizerPlugin {

	@PluginVariant(requiredParameterLabels = { 0 })
	public JComponent visualise(PluginContext context, DiscoveredDataPetriNet dpn) {
		Marking initialMarking = MarkingsHelper.getInitialMarkingOrEmpty(context, dpn);
		Marking[] finalMarkings = MarkingsHelper.getFinalMarkingsOrEmpty(context, dpn);
		return new DiscoveredDPNGraphvizVisualizer(dpn, initialMarking, finalMarkings);
	}

}
