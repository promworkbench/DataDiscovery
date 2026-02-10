package org.processmining.datadiscovery.plugins.alignment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeBoolean;
import org.deckfour.xes.model.XAttributeContinuous;
import org.deckfour.xes.model.XAttributeDiscrete;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XAttributeTimestamp;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.weka.WekaUtil;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.framework.plugin.Progress;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.plugins.petrinet.replayresult.StepTypes;
import org.processmining.plugins.petrinet.replayresult.SwappedMove;
import org.processmining.plugins.petrinet.replayresult.ViolatingSyncMove;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * 
 * @author Mirko Polato
 *
 */
abstract public class AbstractTraceProcessor implements Runnable {

	protected PetrinetGraph net;
	protected XTrace xTrace;
	protected Map<Place, FunctionEstimator> estimators;
	protected Progress progress;
	protected Map<String,Object> variableValues = new HashMap<String,Object>();
	protected AtomicLongMap<Transition> numberOfExecutions;
	protected Map<Transition, AtomicLongMap<String>> numberOfWritesPerTransition;
	protected float weight;
	protected float fitness;

	public AbstractTraceProcessor(PetrinetGraph net, 
						  XTrace xTrace, 
						  Map<Place, FunctionEstimator> estimators,
						  AtomicLongMap<Transition> numberOfExecutions, 
						  Map<Transition, AtomicLongMap<String>> numberOfWritesPerTransition, 
						  Progress progress) {
		this.net = net;
		this.xTrace = xTrace;
		this.estimators = estimators;
		this.progress = progress;
		this.numberOfExecutions = numberOfExecutions;
		this.numberOfWritesPerTransition = numberOfWritesPerTransition;
		this.weight = 1;
		this.fitness = 1;
	}


	protected void updateInstance(Place place, Transition transition, XEvent nextEvent) {	
		
		FunctionEstimator estimator = estimators.get(place);
		
		if (estimator != null) {
		
			try {
				estimator.addInstance(variableValues, transition, this.weight);
			} catch (Exception e) {
				e.printStackTrace();
			}
		
		}
	}

	protected void updateAttributes(XAttributeMap xAttributeMap) {
		
		for (XAttribute xAttrib : xAttributeMap.values())
		{
			String attributeKey = xAttrib.getKey();
			String varName=WekaUtil.fixVarName(attributeKey);
			if (xAttrib instanceof XAttributeBoolean) {
				variableValues.put(varName, ((XAttributeBoolean)xAttrib).getValue());
			} else if (xAttrib instanceof XAttributeContinuous) {
				variableValues.put(varName, ((XAttributeContinuous)xAttrib).getValue());
			} else if (xAttrib instanceof XAttributeDiscrete) {
				variableValues.put(varName, ((XAttributeDiscrete)xAttrib).getValue());
			} else if (xAttrib instanceof XAttributeTimestamp) {
				variableValues.put(varName, ((XAttributeTimestamp)xAttrib).getValue());
			} else if (xAttrib instanceof XAttributeLiteral) {
				variableValues.put(varName,((XAttributeLiteral)xAttrib).getValue());
			}
			
		}
	}
	
	protected void processAlignment(List<StepTypes> steps, List<Object> nodeInstanceList) {
		
		Iterator<XEvent> eventIter=xTrace.iterator();
		Transition transition = null;
		XEvent nextEvent = null;
		updateAttributes(xTrace.getAttributes());
		
		Iterator<Object> transIter = nodeInstanceList.iterator();
		for(StepTypes step : steps)
		{
			switch(step)
			{
				case LMGOOD:
					nextEvent = eventIter.next();
					transition = (Transition) transIter.next();
					
					numberOfExecutions.incrementAndGet(transition);
					
					//FM: using AtomicLongMap it is easier and really thread-safe
					AtomicLongMap<String> numberOfWritePerVariable = numberOfWritesPerTransition.get(transition);
					for(String varName : nextEvent.getAttributes().keySet()) {
						varName = GuardExpression.Factory.transformToVariableIdentifier(WekaUtil.fixVarName(varName));
						numberOfWritePerVariable.incrementAndGet(varName);
					}
					break;
					
				case L :
				case LMNOGOOD :
					eventIter.next();
					transIter.next();
					continue;
					
				case MINVI :
				case MREAL :
					nextEvent = null;
					transition = (Transition) transIter.next();
					break;
					
				case LMREPLACED :
					nextEvent = null;
					transition = ((ViolatingSyncMove) transIter.next()).getTransition();
					break;
					
				case LMSWAPPED :
					nextEvent = null;
					transition = ((SwappedMove) transIter.next()).getInsteadOf();
					break;			
			}
			
			for(PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> edge : net.getInEdges(transition)) {
			
				try {
					if (edge.getSource() instanceof Place) {
						updateInstance((Place) edge.getSource(), transition, nextEvent);	
					}
				}
				catch(Exception err) {
					//FM, why not report error to user??
					err.printStackTrace();
				}
			}
			
			if (nextEvent != null) {
				updateAttributes(nextEvent.getAttributes());
			}
			
		}
		
		//FM, Progress is thread-safe no need to synchronize
		progress.inc();
	}

}
