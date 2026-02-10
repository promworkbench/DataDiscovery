package org.processmining.datadiscovery.plugins.alignment;

import org.deckfour.xes.model.XLog;
import org.processmining.framework.connections.impl.AbstractStrongReferencingConnection;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;

public class ControlFlowAlignmentConnection extends AbstractStrongReferencingConnection {
	public final static String PETRINETGRAPH = "PetrinetGraph";
	public final static String LOG = "Log";
	public final static String PNREPRESULT = "PetriNetResult";
	
	public ControlFlowAlignmentConnection(String label, PetrinetGraph net, XLog log, PNRepResult input) {		
		super(label);
		put(PETRINETGRAPH, net);
		put(LOG, log);
		put(PNREPRESULT, input);
	}
}
