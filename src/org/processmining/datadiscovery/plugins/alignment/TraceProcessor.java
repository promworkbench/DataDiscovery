package org.processmining.datadiscovery.plugins.alignment;

import java.util.Map;

import org.deckfour.xes.model.XTrace;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.framework.plugin.Progress;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

import com.google.common.util.concurrent.AtomicLongMap;

public class TraceProcessor extends AbstractTraceProcessor {

	private SyncReplayResult alignment;

	public TraceProcessor(PetrinetGraph net, 
						  XTrace xTrace, 
						  Map<Place, FunctionEstimator> estimators,
						  SyncReplayResult alignment, 
						  AtomicLongMap<Transition> numberOfExecutions, 
						  Map<Transition, AtomicLongMap<String>> numberOfWritesPerTransition, 
						  Progress progress) {
		
		super(net, xTrace, estimators, numberOfExecutions, numberOfWritesPerTransition, progress);
		
		this.alignment = alignment;
		this.fitness = alignment.getInfo().get(PNRepResult.TRACEFITNESS).floatValue();
		//this.weight = this.fitness;
	}


	public void run() {
		
		processAlignment(alignment.getStepTypes(), alignment.getNodeInstance());
		
	}

}
