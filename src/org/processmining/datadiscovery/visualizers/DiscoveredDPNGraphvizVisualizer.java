package org.processmining.datadiscovery.visualizers;

import java.text.NumberFormat;
import java.util.List;

import javax.swing.table.DefaultTableModel;

import org.processmining.datadiscovery.model.DecisionPointResult;
import org.processmining.datadiscovery.model.DiscoveredDataPetriNet;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.visualization.graphviz.DPNGraphvizVisualizer;
import org.processmining.framework.util.ui.widgets.ProMTextArea;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.models.semantics.petrinet.Marking;

public class DiscoveredDPNGraphvizVisualizer extends DPNGraphvizVisualizer {

	private static final long serialVersionUID = 135930844732659379L;

	public DiscoveredDPNGraphvizVisualizer(final DiscoveredDataPetriNet dpn, final Marking initialMarking,
			final Marking[] finalMarkings) {
		super(dpn, initialMarking, finalMarkings);
	}

	protected void showPlaceDetails(DirectedGraphNode node, ProMTextArea textArea) {
		DecisionPointResult result = getDiscoveredDPN().getDecisionPointResult(((Place) node));
		if (result != null) {
			textArea.setText(result.toString() + "\n F-Score: "
					+ NumberFormat.getNumberInstance().format(result.getQualityMeasure()));
		} else {
			super.showPlaceDetails(node, textArea);
		}
	}

	protected DefaultTableModel createTableModel(DataPetriNet dpn) {
		final List<Transition> transitions = getSortedTransitions(dpn);
		String qualityMeasureName = "F-Score";

		DefaultTableModel tableModel = new DefaultTableModel(new String[] { "Transition", qualityMeasureName, "Guard" },
				0);
		for (Transition t : transitions) {
			String guard = ((PNWDTransition) t).getGuardExpression().toPrettyString(1);
			double confidence = ((PNWDTransition) t).getQuality();
			tableModel
					.addRow(new String[] { t.getLabel(), NumberFormat.getNumberInstance().format(confidence), guard });
		}
		tableModel.addRow(new String[] { "Average",
				NumberFormat.getNumberInstance().format(caluclateAverageQualityMeasure(dpn.getTransitions())), "" });
		return tableModel;
	}

	private DiscoveredDataPetriNet getDiscoveredDPN() {
		return (DiscoveredDataPetriNet) getDpn();
	}

}
