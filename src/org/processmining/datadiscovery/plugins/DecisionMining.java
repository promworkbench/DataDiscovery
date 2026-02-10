package org.processmining.datadiscovery.plugins;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.extension.std.XOrganizationalExtension;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.model.XAttribute;
import org.deckfour.xes.model.XAttributeBoolean;
import org.deckfour.xes.model.XAttributeContinuous;
import org.deckfour.xes.model.XAttributeDiscrete;
import org.deckfour.xes.model.XAttributeLiteral;
import org.deckfour.xes.model.XAttributeTimestamp;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.datadiscovery.estimators.DecisionTreeBasedFunctionEstimator;
import org.processmining.datadiscovery.estimators.FunctionEstimation;
import org.processmining.datadiscovery.estimators.FunctionEstimator;
import org.processmining.datadiscovery.estimators.Type;
import org.processmining.datadiscovery.estimators.impl.DecisionTreeFunctionEstimator;
import org.processmining.datadiscovery.estimators.impl.DiscriminatingFunctionEstimator;
import org.processmining.datadiscovery.estimators.impl.OverlappingEstimatorLocalDecisionTree;
import org.processmining.datadiscovery.estimators.impl.OverlappingEstimatorPairwiseDecisionTrees;
import org.processmining.datadiscovery.model.DecisionPointResult;
import org.processmining.datadiscovery.model.DiscoveredDataPetriNet;
import org.processmining.datadiscovery.model.DiscoveredPetriNetWithData;
import org.processmining.datadiscovery.plugins.alignment.ControlFlowAlignmentConnection;
import org.processmining.datadiscovery.plugins.alignment.TraceProcessor;
import org.processmining.datapetrinets.DataPetriNet;
import org.processmining.datapetrinets.expression.GuardExpression;
import org.processmining.datapetrinets.ui.ConfigurationUIHelper;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginCategory;
import org.processmining.framework.plugin.annotations.PluginLevel;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.framework.util.ui.widgets.ProMComboBox;
import org.processmining.framework.util.ui.widgets.ProMComboCheckBox;
import org.processmining.framework.util.ui.widgets.helper.UserCancelledException;
import org.processmining.log.utils.XUtils;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.petrinet.InhibitorNet;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.PetrinetNode;
import org.processmining.models.graphbased.directed.petrinet.ResetInhibitorNet;
import org.processmining.models.graphbased.directed.petrinet.ResetNet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.DataElement;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PNWDTransition;
import org.processmining.models.graphbased.directed.petrinetwithdata.newImpl.PetriNetWithDataFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithILP;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.finalmarkingprovider.MarkingEditorPanel;
import org.processmining.plugins.petrinet.replayer.PNLogReplayer;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;
import org.processmining.plugins.utils.ProvidedObjectHelper;

import com.fluxicon.slickerbox.components.NiceDoubleSlider;
import com.fluxicon.slickerbox.components.NiceIntegerSlider;
import com.fluxicon.slickerbox.components.NiceSlider.Orientation;
import com.fluxicon.slickerbox.factory.SlickerFactory;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterators;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.AtomicLongMap;

@Plugin(name = "Discovery of the Process Data-Flow (Decision-Tree Miner)", level = PluginLevel.PeerReviewed, parameterLabels = {
		"Petri Net", "Log", "Control-flow Alignment" }, returnLabels = { "Petri Net with Data", "Initial Marking",
				"Final Marking" }, returnTypes = { DiscoveredDataPetriNet.class, Marking.class,
						Marking.class }, userAccessible = true, help = "Uses Decision Trees to Discover the Process Data-flow", categories = {
								PluginCategory.Discovery })
public class DecisionMining {

	private static final int NOMINAL_ATTRIBUTE_WARNING_LIMIT = 100;

	/*
	 * predefine UI elements
	 */
	private static ArrayList<String> booleanValues = new ArrayList<String>();
	private static JCheckBox pruneBox = SlickerFactory.instance().createCheckBox(null, true);
	private static JCheckBox binaryBox = SlickerFactory.instance().createCheckBox(null, true);
	private static JCheckBox crossValidateBox = SlickerFactory.instance().createCheckBox(null, false);
	private static NiceIntegerSlider instances4Leaf = SlickerFactory.instance().createNiceIntegerSlider("(In permil)",
			1, 500, 20, Orientation.HORIZONTAL);
	private final static String useStandardFE = "Basic Decision Tree ";
	private final static String DiscrFunctionEstimator = "True/False Decision Tree";
	private final static String PairwiseFunctionEstimator = "Pairwise Decision Tree";
	private final static String InclusiveEstimator = "Overlapping Decision Tree";

	private static NiceIntegerSlider percentageOfWrite = SlickerFactory.instance()
			.createNiceIntegerSlider("(In Percent)", 50, 100, 66, Orientation.HORIZONTAL);
	private static JCheckBox removeAttribInNoGuardBox = SlickerFactory.instance().createCheckBox(null, true);
	private static NiceDoubleSlider fitnessThresholdSlider = null; // Defined later, when the Fitness for the net and log is known

	private final static String[] algorithms = new String[] { useStandardFE, DiscrFunctionEstimator,
			PairwiseFunctionEstimator, InclusiveEstimator };
	private static ProMComboBox<String> algorithmCBox = new ProMComboBox<>(algorithms);
	private static JCheckBox mineWriteOpBox;
	private static ProMComboCheckBox attributeCCBox;
	private static ProMComboCheckBox placesCCBox;
	private static String[] attributes;
	private static Place[] consideredPlaces;

	static {
		booleanValues.add("T");
		booleanValues.add("F");
		mineWriteOpBox = SlickerFactory.instance().createCheckBox(null, true);
		mineWriteOpBox.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				percentageOfWrite.setEnabled(mineWriteOpBox.isSelected());
				if (!mineWriteOpBox.isSelected())
					JOptionPane.showMessageDialog(null,
							"You have just chosen not to mine the write operations. \nThis will lead to a more readable model "
									+ "where one can easily analyze the guards. \nBut the model will not be sound because variables never take on values.");
			}

		});
	}

	@PluginVariant(variantLabel = "Without Replay Result", requiredParameterLabels = { 0, 1 })
	@UITopiaVariant(affiliation = "TU/e", author = "Massimiliano de Leoni, F. Mannhardt,", email = "m.d.leoni@tue.nl, f.mannhardt@tue.nl")
	public static Object[] decisionMiner(UIPluginContext context, Petrinet net, XLog log) throws Exception {
		return plugin(context, net, log, true);
	}
	
	@PluginVariant(variantLabel = "Without Replay Result", requiredParameterLabels = { 0, 1 })
	@UITopiaVariant(affiliation = "TU/e", author = "Massimiliano de Leoni, F. Mannhardt,", email = "m.d.leoni@tue.nl, f.mannhardt@tue.nl")
	public static Object[] decisionMiner(UIPluginContext context, DataPetriNet net, XLog log) throws Exception {
		return plugin(context, net, log, true);
	}
	
	public static Object[] decisionMiner(UIPluginContext context, PetrinetGraph net, XLog log) throws Exception {
		return plugin(context, net, log, true);
	}
	
	@PluginVariant(variantLabel = "With Replay Result", requiredParameterLabels = { 0, 1, 2 })
	@UITopiaVariant(affiliation = "TU/e", author = "Massimiliano de Leoni, F. Mannhardt,", email = "m.d.leoni@tue.nl, f.mannhardt@tue.nl")
	public static Object[] decisionMiner(UIPluginContext context, Petrinet net, XLog log, PNRepResult input)
			throws Exception {
		context.getConnectionManager().addConnection(
				new ControlFlowAlignmentConnection("Control-Flow Alignment Connection", net, log, input));
		return plugin(context, net, log, true);
	}

	@PluginVariant(variantLabel = "With Replay Result", requiredParameterLabels = { 0, 1, 2 })
	@UITopiaVariant(affiliation = "TU/e", author = "Massimiliano de Leoni, F. Mannhardt,", email = "m.d.leoni@tue.nl, f.mannhardt@tue.nl")
	public static Object[] decisionMiner(UIPluginContext context, DataPetriNet net, XLog log, PNRepResult input)
			throws Exception {
		context.getConnectionManager().addConnection(
				new ControlFlowAlignmentConnection("Control-Flow Alignment Connection", net, log, input));
		return plugin(context, net, log, true);
	}
	
	public static Object[] decisionMiner(UIPluginContext context, PetrinetGraph net, XLog log, PNRepResult input)
			throws Exception {
		context.getConnectionManager().addConnection(
				new ControlFlowAlignmentConnection("Control-Flow Alignment Connection", net, log, input));
		return plugin(context, net, log, true);
	}
	
	public static Object[] daikonMiner(UIPluginContext context, PetrinetGraph net, XLog log) throws Exception {
		return decisionMiner(context, net, log);
	}

	/**
	 * The method that performs the actual mining to discover the process
	 * data-flow on decision points.
	 * 
	 * @param context
	 *            ProM context
	 * @param net
	 *            the PetrinetGraph to transform to a PetriNetWithData with
	 *            guards on decision points
	 * @param log
	 *            the event log
	 * @param setParameters
	 *            if False, apply default settings everywhere instead of asking
	 *            the user to set parameters. If True, give user the choice
	 *            through dialogs.
	 * @return Object[] array containing a PetriNetWithData, an initial Marking,
	 *         and a final Marking
	 * @throws Exception
	 */
	public static Object[] plugin(final UIPluginContext context, final PetrinetGraph net, final XLog log,
			boolean setParameters) throws Exception {

		/*
		 * Setup multi-threading for later use
		 */
		int maxConcurrentThreads = Runtime.getRuntime().availableProcessors();
		ThreadPoolExecutor pool = new ThreadPoolExecutor(maxConcurrentThreads, maxConcurrentThreads, 60,
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		pool.allowCoreThreadTimeOut(false);

		Future<Map<String, Type>> classTypesFuture = pool.submit(new Callable<Map<String, Type>>() {

			public Map<String, Type> call() throws Exception {
				/*
				 * Store the log's Attributes and their Data Types in
				 * Map<String, Type> classTypes
				 */
				return extractAttributeInformation(log);
			}
		});

		Future<Map<String, Set<String>>> literalValuesFuture = pool.submit(new Callable<Map<String, Set<String>>>() {

			public Map<String, Set<String>> call() throws Exception {

				/*
				 * Extract the literal values for the attributes of type
				 * XAttributeLiteral and store them in literalValues.
				 * literalValues is a mapping from attribute name to a set of
				 * literal string values for that attribute.
				 */
				return getLiteralValuesMap(log);
			}
		});

		/*
		 * Find the PNRepResult corresponding to the net and log, if there
		 * already is one. Else, exception [ConnectionCannotBeObtained]: Apply
		 * the PNLogReplayer to get a new PNRepResult for not PetrinetGraph
		 * types.
		 */
		ControlFlowAlignmentConnection connection;
		PNRepResult input = null;
		try {
			/*
			 * Check the connectionManager for an existing PNRepResult for the
			 * net and log.
			 */
			connection = context.getConnectionManager().getFirstConnection(ControlFlowAlignmentConnection.class,
					context, net, log);
			input = connection.getObjectWithRole(ControlFlowAlignmentConnection.PNREPRESULT);
		} catch (ConnectionCannotBeObtained e1) {
			/*
			 * There exists no connection yet of type
			 * ControlFlowAlignmentConnection, and it could not be automagically
			 * created. Calculate a new PNRepResult through
			 * PNLogReplayer().replayLog( context, net, log, mapping between
			 * transitions and event classes, the specific Replayer to use,
			 * plugin parameters)
			 */
			TransEvClassMapping mapping = createMapping(context, net, log, setParameters);
			CostBasedCompleteParam parameters = createParameters(log, mapping, net, context, setParameters);
			if (parameters == null) {
				context.getFutureResult(0).cancel(true); // Cancel the Activity in ProM and
				return null; // Return nothing							
			}
			// Enable multi-threaded alignments, but do not use too much threads to avoid memory issues
			parameters.setNumThreads((int) (maxConcurrentThreads * 0.75));
			if (net instanceof InhibitorNet)
				input = new PNLogReplayer().replayLog(context, (InhibitorNet) net, log, mapping,
						new PetrinetReplayerWithILP(), parameters);
			else if (net instanceof Petrinet)
				input = new PNLogReplayer().replayLog(context, (Petrinet) net, log, mapping,
						new PetrinetReplayerWithILP(), parameters);
			else if (net instanceof ResetInhibitorNet)
				input = new PNLogReplayer().replayLog(context, (ResetInhibitorNet) net, log, mapping,
						new PetrinetReplayerWithILP(), parameters);
			else if (net instanceof ResetNet)
				input = new PNLogReplayer().replayLog(context, (ResetNet) net, log, mapping,
						new PetrinetReplayerWithILP(), parameters);
			else
				throw (new IllegalArgumentException("The net is of a form that was not expected"));

			ProvidedObjectHelper.publish(context, "Control-flow Alignment of " + net.getLabel() + " and "
					+ XConceptExtension.instance().extractName(log), input, PNRepResult.class, false);

			context.getConnectionManager().addConnection(
					new ControlFlowAlignmentConnection("Control-Flow Alignment Connection", net, log, input));
		}

		if (input == null) {
			context.getFutureResult(0).cancel(true); // Cancel the Activity in ProM and
			return null; // Return nothing			
		}

		Map<String, Type> classTypes = classTypesFuture.get();
		Map<String, Set<String>> literalValues = literalValuesFuture.get();

		/*
		 * Initialize a HashMap<Place, FunctionEstimator>, linking a
		 * FunctionEstimator to each Place
		 */
		Map<Place, FunctionEstimator> estimators = new HashMap<>();

		/*
		 * Initialize a thread-safe AtomicLongMap<Transition> denoting the
		 * numberOfExecutions per Transition
		 */
		AtomicLongMap<Transition> numberOfExecutions = AtomicLongMap.create();

		/*
		 * Initialize a HashMap<Transition, Map<String, Integer>> denoting the
		 * number of writes per Transition FM: Does not need to be thread-safe!!
		 * Only get is executed!
		 */
		Map<Transition, AtomicLongMap<String>> numberOfWritesPerTransition = new HashMap<>();

		for (Transition trans : net.getTransitions()) {
			/*
			 * For each transition, set the numberOfExecutions to 0 and
			 * initialize the HashMap<String, Integer>
			 */
			numberOfExecutions.put(trans, 0);
			numberOfWritesPerTransition.put(trans, AtomicLongMap.<String>create());
		}

		/*
		 * Given the PNRepResult, find the Fitness to initialize the
		 * fitnessThreshold slider for the Configuration panel
		 */
		Double initialFitness = ((Double) input.getInfo().get(PNRepResult.TRACEFITNESS));
		if (initialFitness == null) {
			JOptionPane.showMessageDialog(null,
					"It is impossible to create the control-flow alignments. Make sure that the model and the log are as intended.");
			context.getFutureResult(0).cancel(true); // Cancel the Activity in ProM and
			return null; // Return nothing			
		}
		if (initialFitness < 0.5) {
			JOptionPane.showMessageDialog(null,
					"The average trace fitness is less than 0.5. It means that the model has some problems (e.g., not sound?) or "
							+ " is a bad representation of what observed in the event log. As a consequence, the guards are not fully reliable. "
							+ "Please try to generate the control-flow alignments and, when satisfied, give them to as the plug-in as input.");
			initialFitness = 0.5;
		}
		fitnessThresholdSlider = SlickerFactory.instance().createNiceDoubleSlider("", 0.5, 1, initialFitness,
				Orientation.HORIZONTAL);

		/*
		 * If setParameters == True, show the DecisionTreePanel allowing the
		 * user to configure plugin parameters. If the return-value of the
		 * configuration panel is True, stop the plugin and return null.
		 */
		if (setParameters && showDecisionTreePanel(estimators, context, classTypes, literalValues, net)) {
			// setParameters == True && showDecisionTreePanel returned True
			context.getFutureResult(0).cancel(true); // Cancel the Activity in ProM and
			return null; // Return nothing
		}

		/*
		 * For each place with at least 2 outgoing edges in the net ..
		 */
		for (Place place : net.getPlaces()) {
			// If 'place' does not represent an OR-split (outgoing edges < 2), goto next iteration of for-loop (skip the place, not interesting)
			if (net.getOutEdges(place).size() < 2) {
				continue;
			}

			if (!placesCCBox.getSelectedItems().contains(place)) {
				continue;
			}

			/*
			 * 'place' is part of an OR-split decision point with >= 2 outgoing
			 * edges. Prepare an array outputValues[] to collect the target
			 * Transitions of the outgoing edges of 'place'
			 */
			Transition outputValues[] = new Transition[net.getOutEdges(place).size()];
			int index = 0;
			/*
			 * For each outgoing edge 'arc' of Place 'place', store that edge's
			 * target Transition in the array.
			 */
			for (PetrinetEdge<? extends PetrinetNode, ? extends PetrinetNode> arc : net.getOutEdges(place)) {
				outputValues[index++] = (Transition) arc.getTarget();
			}

			/*
			 * Prepare variable f for a functionEstimator, and create one with
			 * the attribute types, values and resulting Transitions for Place
			 * place
			 */
			FunctionEstimator f;

			String algorithm = (String) algorithmCBox.getSelectedItem();
			if (algorithm == useStandardFE)
				f = new DecisionTreeFunctionEstimator(classTypes, literalValues, outputValues, place.getLabel(),
						log.size());
			else if (algorithm == DiscrFunctionEstimator)
				f = new DiscriminatingFunctionEstimator(classTypes, literalValues, outputValues, log.size(),
						place.getLabel());
			else if (algorithm == PairwiseFunctionEstimator)
				f = new OverlappingEstimatorPairwiseDecisionTrees(classTypes, literalValues, outputValues, log.size(),
						place.getLabel(), pool);
			else if (algorithm == InclusiveEstimator)
				f = new OverlappingEstimatorLocalDecisionTree(classTypes, literalValues, outputValues, log.size(),
						place.getLabel());
			else
				throw (new IllegalArgumentException("Algorithm Unforeseen!!"));

			/*
			 * Associate the created FunctionEstimator 'f' with Place 'place' by
			 * putting them in a Mapping from <Place> to <FunctionEstimator>
			 */
			estimators.put(place, f);

		} // END for (Place place : net.getPlaces())
			// POST: Each Place 'place' which is part of a decision part has an associated FunctionEstimator in Map<Place, FunctionEstimator> 'estimators'

		// Prepare context progress bar
		final Progress progress = context.getProgress();
		progress.setMaximum(log.size() + estimators.entrySet().size());
		progress.setValue(0);
		context.log("Processing the log traces...");

		List<Future<Integer>> traceFutures = new ArrayList<>();

		// Prepare trace counters
		int traceSkipped = 0;
		int totalNumTraces = 0;
		// For each alignment in the PNRepResult 'input'
		for (SyncReplayResult alignment : input) {
			// Count the number of traces encountered in total
			totalNumTraces += alignment.getTraceIndex().size();
			// If the alignment's fitness complies with the specified minimum fitness in fitnessThresholdSlider
			if (alignment.getInfo().get(PNRepResult.TRACEFITNESS).floatValue() >= fitnessThresholdSlider.getValue()) {
				// Then for each trace in the alignment
				for (Integer index : alignment.getTraceIndex()) {
					/*
					 * Have a thread in the pool process the trace in the
					 * alignment, adding instances to the estimator and keeping
					 * track of attribute values. For each alignment, For each
					 * step in the alignment, For each in-edge of the
					 * transition, If the source-Place of that in-edge has an
					 * estimator, Add an instance corresponding to the variable
					 * values in that place, before executing the transition
					 * corresponding to the step. An instance is a set of
					 * attribute values' pre-values and a transition to be
					 * executed
					 */
					traceFutures.add(pool.submit(new TraceProcessor(net, log.get(index), estimators, alignment,
							numberOfExecutions, numberOfWritesPerTransition, progress), index));
				}
			} else {
				// The alignment's fitness is lower than the fitness threshold; skip the trace and count the skipped traces
				traceSkipped += alignment.getTraceIndex().size();
			}
		}
		// Report # of skipped traces, shut down the thread pool
		context.log("Skipped " + traceSkipped + " low-fitting traces out of " + totalNumTraces);

		for (Future<Integer> traceFuture : traceFutures) {
			// This blocks until the trace processor is done
			traceFuture.get();
			if (progress.isCancelled()) {
				context.getFutureResult(0).cancel(true);
				return null;
			}

		}

		DiscoveredPetriNetWithData discoveredDPN = null;
		Map<Place, Future<DecisionPointResult>> results = new HashMap<>();

		// For each place
		for (Place place : net.getPlaces()) {
			// Configure the corresponding function estimator's decision tree parameters
			FunctionEstimator f = estimators.get(place);
			if (f != null) {
				if (f instanceof DecisionTreeBasedFunctionEstimator) {
					int numInstances = ((DecisionTreeBasedFunctionEstimator) f).getNumInstances();
					// Set the minimal number of instances per leaf, in per mille (relative to numInstances)
					((DecisionTreeBasedFunctionEstimator) f)
							.setMinNumObj((int) (numInstances * (instances4Leaf.getValue() / 1000F)));
					// Prune the tree?
					((DecisionTreeBasedFunctionEstimator) f).setUnpruned(!pruneBox.isSelected());
					// Binary split?
					((DecisionTreeBasedFunctionEstimator) f).setBinarySplit(binaryBox.isSelected());
					((DecisionTreeBasedFunctionEstimator) f).setCrossValidate(crossValidateBox.isSelected());
				}
			}
		}

		/*
		 * Collect result for each decision point
		 */
		for (Entry<Place, FunctionEstimator> estimatorPlacePair : estimators.entrySet()) {

			final Place place = estimatorPlacePair.getKey();
			final FunctionEstimator f = estimatorPlacePair.getValue();

			Future<DecisionPointResult> result = pool.submit(new Callable<DecisionPointResult>() {

				public DecisionPointResult call() throws Exception {

					// Calculate the conditions with likelihoods for each target transition of place entry2.getKey()
					final Map<Object, FunctionEstimation> estimationTransitionExpression = f
							.getFunctionEstimation(null);
					
					double sumFMeasures = 0;
					int numRules = 0;
					for (FunctionEstimation val : estimationTransitionExpression.values()) {
						sumFMeasures += val.getQualityMeasure();
						numRules++;
					}

					final double singleFScore = numRules > 0 ? sumFMeasures / numRules : 0;
					final String decisionPointClassifier = f.toString();

					DecisionPointResult result = new DecisionPointResult() {

						public String toString() {
							return decisionPointClassifier;
						}

						public double getQualityMeasure() {
							return singleFScore;
						}

						public Map<Object, FunctionEstimation> getEstimatedGuards() {

							return estimationTransitionExpression;
						}

					};

					if (result.getQualityMeasure() > 0) {
						context.log(String.format("Generated the conditions for decision point %s with f-score %s",
								place.getLabel(), result.getQualityMeasure()));
					}
					progress.inc();

					return result;
				}

			});
			results.put(place, result);
		}

		/*
		 * Prepare the mining algorithm's resulting PetriNetWithData.
		 */
		String dpnName = String.format("%s (%s, min instances per leaf: %d, pruning: %s, binary: %s)", net.getLabel(),
				algorithmCBox.getSelectedItem(), instances4Leaf.getValue(), pruneBox.getModel().isSelected(),
				binaryBox.getModel().isSelected());
		final PetriNetWithDataFactory factory = new PetriNetWithDataFactory(net,
				new DiscoveredPetriNetWithData(dpnName), false);
		discoveredDPN = (DiscoveredPetriNetWithData) factory.getRetValue(); // cast if safe

		/*
		 * For each entry in classTypes, <String, Type> representing (Attribute
		 * name, attribute type), depending on the type add a new Variable to
		 * the new PetriNetWithData without min or max values
		 */

		for (Entry<String, Type> entry : classTypes.entrySet()) {
			Class<?> classType = null;
			switch (entry.getValue()) {
				case BOOLEAN :
					classType = Boolean.class;
					break;
				case CONTINUOS :
					classType = Double.class;
					break;
				case DISCRETE :
					classType = Long.class;
					break;
				case LITERAL :
					classType = String.class;
					break;
				case TIMESTAMP :
					classType = Date.class;
					break;
				default :
					break;

			}
			String wekaUnescaped = wekaUnescape(entry.getKey());
			String saneVariableName = GuardExpression.Factory.transformToVariableIdentifier(wekaUnescaped);
			discoveredDPN.addVariable(saneVariableName, classType, null, null);
		}

		//TODO FM, we should not change the default Locale!! What about concurrent operations that rely on the correct Locale? Why is it done anyway?
		Locale defaultLocale = Locale.getDefault();
		Locale.setDefault(Locale.US);

		double sumFScores = 0;
		int numEstimators = 0;

		Map<Place, Place> placeMapping = factory.getPlaceMapping();

		for (Entry<Place, Future<DecisionPointResult>> futureEntry : results.entrySet()) {
			try {
				DecisionPointResult result = futureEntry.getValue().get();
				discoveredDPN.storeDecisionPointResult(placeMapping.get(futureEntry.getKey()), result);

				// If, for any Transition at this decision point, an expression is found..
				if (!result.getEstimatedGuards().isEmpty()) {
					// Then, for each such Transition, set the guard in the PetriNetWithData
					for (Entry<Object, FunctionEstimation> transitionEntry : result.getEstimatedGuards().entrySet()) {
						Transition transitionInPNWithoutData = (Transition) transitionEntry.getKey();
						PNWDTransition transitionInPNWithData = (PNWDTransition) factory.getTransMapping()
								.get(transitionInPNWithoutData);
						FunctionEstimation value = transitionEntry.getValue();
						if (transitionInPNWithData.getGuardExpression() != null) {
							//TODO find correct method to update f-score / what to do in the general case for more than two incoming arcs
							Double combinedFScore = (value.getQualityMeasure() + transitionInPNWithData.getQuality())
									/ 2;
							GuardExpression existingGuard = transitionInPNWithData.getGuardExpression();
							GuardExpression additionalGuard = value.getExpression();
							GuardExpression combinedGuard = GuardExpression.Operation.and(existingGuard,
									additionalGuard);
							context.log(
									String.format("Combining two guards for non-free choice construct: %s (%s) %s (%s)",
											existingGuard, value.getQualityMeasure(), additionalGuard,
											transitionInPNWithData.getQuality()));
							discoveredDPN.setGuard(transitionInPNWithData, combinedGuard, combinedFScore);
						} else {
							discoveredDPN.setGuard(transitionInPNWithData, value.getExpression(),
									value.getQualityMeasure());
						}
					}

					sumFScores += result.getQualityMeasure();
					numEstimators++;
				}
			} catch (ExecutionException e) {
				context.log(e);
			}
		}

		//TODO see above!
		Locale.setDefault(defaultLocale);

		if (sumFScores > 0) {
			context.log("Average F Score: " + sumFScores / numEstimators);
		}

		for (Transition transitionInPNWithoutData : net.getTransitions()) {
			PNWDTransition transitionInPNWithData = (PNWDTransition) factory.getTransMapping()
					.get(transitionInPNWithoutData);

			if (mineWriteOpBox.isSelected() && !transitionInPNWithData.isInvisible()) {

				//Set the read operations
				if (transitionInPNWithData.getGuardExpression() != null) {
					// Only use normal variables as those are read
					Set<String> normalVariables = transitionInPNWithData.getGuardExpression().getNormalVariables();
					for (String varName : normalVariables) {
						discoveredDPN.assignReadOperation(transitionInPNWithData, discoveredDPN.getVariable(varName));
					}
				}

				//Set the write operations
				long numberOfExecution = numberOfExecutions.get(transitionInPNWithoutData);
				for (Entry<String, Long> numWritesVariable : numberOfWritesPerTransition.get(transitionInPNWithoutData)
						.asMap().entrySet()) {
					if (numWritesVariable.getValue() > (numberOfExecution * percentageOfWrite.getValue()) / 100) {
						DataElement dataElem = discoveredDPN.getVariable(numWritesVariable.getKey());
						if (dataElem != null)
							discoveredDPN.assignWriteOperation(transitionInPNWithData, dataElem);
					}
				}
			}
		}

		if (removeAttribInNoGuardBox.isSelected())
			discoveredDPN.removeAllVariablesNotInGuard();

		Marking[] markings = factory.cloneInitialAndFinalConnection(context);

		// Set appropriate name for returned DPN
		context.getFutureResult(0).setLabel(dpnName);

		pool.shutdown();

		return new Object[] { discoveredDPN, markings[0], markings[1] };

	}

	private static TransEvClassMapping createMapping(UIPluginContext context, PetrinetGraph net, XLog log,
			boolean setParameters) throws UserCancelledException {

		TransEvClassMapping mapping = ConfigurationUIHelper.queryActivityEventClassMapping(context, net, log);

		Set<Transition> unmappedTrans = new HashSet<Transition>();
		for (Entry<Transition, XEventClass> entry : mapping.entrySet()) {
			if (entry.getValue().equals(mapping.getDummyEventClass())) {
				if (!entry.getKey().isInvisible()) {
					unmappedTrans.add(entry.getKey());
				}
			}
		}

		/*
		 * If there's at least one unmapped transition, offer the user the
		 * option to set them to invisible
		 */
		if (!unmappedTrans.isEmpty()) {
			JList<Transition> list = new JList<Transition>(unmappedTrans.toArray(new Transition[unmappedTrans.size()]));
			JPanel panel = new JPanel();

			BoxLayout layout = new BoxLayout(panel, BoxLayout.Y_AXIS);
			panel.setLayout(layout);
			panel.add(new JLabel("The following transitions are not mapped to any event class:"));

			JScrollPane sp = new JScrollPane(list);
			panel.add(sp);
			panel.add(new JLabel("Do you want to consider these transitions as invisible (unlogged activities)?"));

			Object[] options = { "Yes, set them to invisible", "No, keep them as they are" };

			/*
			 * If setParameters == false, automatically set the unmapped
			 * transitions to invisible
			 */
			if (!setParameters || 0 == JOptionPane.showOptionDialog(null, panel, "Configure transition visibility",
					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0])) {
				for (Transition t : unmappedTrans) {
					t.setInvisible(true);
				}
			}
		}

		return mapping;
	}

	/**
	 * Method defining the Configuration UI Panel, allowing the user to set the
	 * parameters for the plugin's execution.
	 * 
	 * @param estimators
	 * @param context
	 * @param classTypes
	 * @param literalValues
	 * @param net
	 * @return
	 */
	private static boolean showDecisionTreePanel(Map<Place, FunctionEstimator> estimators, UIPluginContext context,
			Map<String, Type> classTypes, Map<String, Set<String>> literalValues, PetrinetGraph net) {
		DecisionMiningPropertyPanel panel = new DecisionMiningPropertyPanel("");

		/*
		 * Put all the Attribute names in a String array and sort them. Then,
		 * define a CheckComboBox containing the sorted array of Attribute
		 * names.
		 */
		String[] attributes = classTypes.keySet().toArray(new String[classTypes.size()]); // String[0], s.t. toArray() allocates a new array with same runtype of correct size
		attributes = filterAttributesWithLargeLiteralSet(classTypes, literalValues, attributes);
		Arrays.sort(attributes);
		boolean equal = true;
		if (DecisionMining.attributes == null || attributes.length != DecisionMining.attributes.length) {
			equal = false;
		} else
			for (int i = 0; i < attributes.length; i++)
				if (!attributes[i].equals(DecisionMining.attributes[i]))
					equal = false;

		if (!equal) {
			DecisionMining.attributes = attributes;
			attributeCCBox = new ProMComboCheckBox(attributes, true);
		}
		panel.addProperty("Variables considered:", attributeCCBox);

		Place[] consideredPlaces = net.getPlaces().toArray(new Place[net.getPlaces().size()]);
		Arrays.sort(consideredPlaces);
		boolean placesEqual = true;
		if (DecisionMining.consideredPlaces == null
				|| consideredPlaces.length != DecisionMining.consideredPlaces.length) {
			placesEqual = false;
		} else
			for (int i = 0; i < consideredPlaces.length; i++)
				if (consideredPlaces[i] != (DecisionMining.consideredPlaces[i])) // has to be the same PN, not just the same label 
					placesEqual = false;

		if (!placesEqual) {
			DecisionMining.consideredPlaces = consideredPlaces;
			placesCCBox = new ProMComboCheckBox(consideredPlaces, true);
		}
		panel.addProperty("Places considered:", placesCCBox);

		panel.addProperty("Mine Write Operations", mineWriteOpBox);
		panel.addProperty("Percentage of occurrences of write operations to be mined", percentageOfWrite);
		panel.addProperty("Remove variables appearing in no guard", removeAttribInNoGuardBox);
		panel.addProperty("Prune decision trees", pruneBox);
		panel.addProperty("Enforce decision trees to be binary", binaryBox);
		panel.addProperty("Cross validate guards (5-fold)", crossValidateBox);
		panel.addProperty("Minimal fitness to consider a trace", fitnessThresholdSlider);
		panel.addProperty("Minimal numbers of instances per decision-tree leaf", instances4Leaf);
		panel.addProperty("Algorithm to mine guards", algorithmCBox);

		/*
		 * Show the configuration panel and read the user's InteractionResult
		 * from the context. If cancel: Return True, such that the plugin quits
		 */
		InteractionResult result = context.showConfiguration("Configuration", panel);
		if (result == InteractionResult.CANCEL) {
			return true;
		}

		/*
		 * If at least one attribute is selected in the CheckComboBox to
		 * consider for guard mining, i.e.
		 * attributeCCBox.getSelectedItems()!=null, Iterate over the attributes
		 * in classTypes, and remove the ones that are not selected in the
		 * Attribute CheckComboBox.
		 */
		if (attributeCCBox.getSelectedItems() != null) {
			Iterator<Entry<String, Type>> iter = classTypes.entrySet().iterator();
			while (iter.hasNext()) {
				if (!attributeCCBox.getSelectedItems().contains(iter.next().getKey()))
					iter.remove();
			}
		} else
			return true;
		return false;
	}

	private static String[] filterAttributesWithLargeLiteralSet(Map<String, Type> classTypes,
			Map<String, Set<String>> literalValues, String[] attributes) {
		HashSet<String> tempClassTypes = Sets.newHashSet(classTypes.keySet());
		for (String attrName : attributes) {
			Set<String> possibleValues = literalValues.get(attrName);
			if (possibleValues != null && possibleValues.size() > NOMINAL_ATTRIBUTE_WARNING_LIMIT) {
				int result = JOptionPane.showConfirmDialog(null,
						"The nominal/literal attribute: " + attrName + " has a large (>"
								+ NOMINAL_ATTRIBUTE_WARNING_LIMIT + ") set of possible values, for example:\n["
								+ Joiner.on(',').join(Iterators.limit(possibleValues.iterator(), 5))
								+ ", ...].\nWhen including this attribute, the discovery of the data-flow may take a long time and consume large amounts of memory. Do you still want to include the attribute?",
						"Warning: Large set of values for a nominal attribute detected!", JOptionPane.YES_NO_OPTION);
				if (result == JOptionPane.NO_OPTION) {
					tempClassTypes.remove(attrName);
				}
			}
		}
		if (tempClassTypes.size() != attributes.length) {
			attributes = tempClassTypes.toArray(new String[tempClassTypes.size()]);
			Arrays.sort(attributes);
		}
		return attributes;
	}

	private static CostBasedCompleteParam createParameters(XLog log, TransEvClassMapping mapping, PetrinetGraph net,
			PluginContext context, boolean setParameters) {
		XLogInfo logInfo = XLogInfoFactory.createLogInfo(log, mapping.getEventClassifier());
		CostBasedCompleteParam parameter = new CostBasedCompleteParam(logInfo.getEventClasses().getClasses(),
				mapping.getDummyEventClass(), net.getTransitions(), 1, 1);
		parameter.setGUIMode(false);
		parameter.setCreateConn(false);

		Marking initMarking = null;
		try {
			initMarking = context.getConnectionManager()
					.getFirstConnection(InitialMarkingConnection.class, context, net)
					.getObjectWithRole(FinalMarkingConnection.MARKING);
			if (initMarking.isEmpty()) {
				int yn = JOptionPane.showConfirmDialog(null, "The initial marking is empty. Is this intended?",
						"Initial Marking", JOptionPane.YES_NO_OPTION);
				if (yn == JOptionPane.NO_OPTION) {
					JOptionPane.showMessageDialog(null,
							"Please create the correct initial marking using the appropriate plug-in");
					return null;
				}
			}
		} catch (Exception e) {
			initMarking = createMarking((UIPluginContext) context, net, "Initial Marking");
			ProvidedObjectHelper.publish(context, "Initial Marking for " + net.getLabel(), initMarking, Marking.class,
					false);
			context.getConnectionManager().addConnection(new InitialMarkingConnection(net, initMarking));
		}
		parameter.setInitialMarking(initMarking);

		Marking finalMarking = null;
		try {
			finalMarking = context.getConnectionManager().getFirstConnection(FinalMarkingConnection.class, context, net)
					.getObjectWithRole(FinalMarkingConnection.MARKING);
			if (finalMarking.isEmpty()) {
				int yn = JOptionPane.showConfirmDialog(null, "The final marking is empty. Is this intended?",
						"Final Marking", JOptionPane.YES_NO_OPTION);
				if (yn == JOptionPane.NO_OPTION) {
					JOptionPane.showMessageDialog(null,
							"Please create the correct final marking using the appropriate plug-in");
					return null;
				}
			}

		} catch (Exception e) {
			finalMarking = createMarking((UIPluginContext) context, net, "Final Marking");
			ProvidedObjectHelper.publish(context, "Final Marking for " + net.getLabel(), finalMarking, Marking.class,
					false);
			context.getConnectionManager().addConnection(new FinalMarkingConnection(net, finalMarking));
		}
		parameter.setFinalMarkings(new Marking[] { finalMarking });
		parameter.setMaxNumOfStates(Integer.MAX_VALUE);
		return parameter;
	}

	/**
	 * Creates a marking of connection-type classType (i.e.
	 * FinalMarkingConnection.class)
	 * 
	 * @param context
	 * @param net
	 * @param title
	 * @return
	 */
	private static Marking createMarking(UIPluginContext context, PetrinetGraph net, String title) {
		MarkingEditorPanel editor = new MarkingEditorPanel(title);

		return editor.getMarking(context, net);
	}

	public static Map<String, Set<String>> getLiteralValuesMap(XLog log) {
		return getLiteralValuesMap(log, false);
	}

	public static Map<String, Set<String>> getLiteralValuesMap(XLog log, boolean forgetTraceAttribute) {

		Map<String, Set<String>> retValue = new HashMap<>();

		for (XTrace trace : log) {

			if (!forgetTraceAttribute) {

				for (XAttribute attributeEntry : trace.getAttributes().values()) {

					if (attributeEntry instanceof XAttributeLiteral) {

						String value = ((XAttributeLiteral) attributeEntry).getValue();
						String varName = fixVarName(attributeEntry.getKey());
						Set<String> literalValues = retValue.get(varName);

						if (literalValues == null) {
							literalValues = new TreeSet<>();
							retValue.put(varName, literalValues);
						}

						literalValues.add(value);
					}
				}
			}

			for (XEvent event : trace) {

				for (XAttribute attributeEntry : event.getAttributes().values()) {

					if (attributeEntry instanceof XAttributeLiteral) {

						String value = ((XAttributeLiteral) attributeEntry).getValue();
						String varName = fixVarName(attributeEntry.getKey());
						Set<String> literalValues = retValue.get(varName);

						if (literalValues == null) {
							literalValues = new TreeSet<>();
							retValue.put(varName, literalValues);
						}

						literalValues.add(value);
					}
				}
			}
		}

		return retValue;
	}

	public static Map<String, Type> extractAttributeInformation(XLog log) {
		return extractAttributeInformation(log, true);
	}

	//Cache for attribute keys as URLEncoding is quite expensive
	private static final ConcurrentMap<String, String> ESCAPE_CACHE = new ConcurrentHashMap<>(32, 0.75f, 4);
	private static final int ATTRIBUTE_NAME_CACHE_SIZE = 4096;

	public static String fixVarName(final String varName) {
		try {
			String fixedName = ESCAPE_CACHE.get(varName);
			if (fixedName == null) {
				// Quite naive eviction strategy, but this is fine for normal logs with not so many attribute names
				if (ESCAPE_CACHE.size() > ATTRIBUTE_NAME_CACHE_SIZE) {
					ESCAPE_CACHE.clear();
				}
				String preparedForUriEncoding = replaceNonUriEncodedChars(varName);
				String uriEncoded = URLEncoder.encode(preparedForUriEncoding, "utf-8");
				fixedName = uriEncoded.replace('%', '$');
				// Strings are immutable, so it does not matter which one is returned
				ESCAPE_CACHE.put(varName, fixedName);
			}
			return fixedName;
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Could not escape variable " + varName, e);
		}
	}

	public static String wekaUnescape(String varName) {
		try {
			return URLDecoder.decode(varName.replace('$', '%'), "utf-8");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("Could not unescape variable " + varName, e);
		}
	}

	public static String replaceNonUriEncodedChars(String varName) {
		char charArray[] = varName.toCharArray();
		for (int i = 0; i < charArray.length; i++) {
			switch (charArray[i]) {
				case '-' :
				case '.' :
				case '~' :
				case '*' :
					charArray[i] = '$';
					break;
				default :
			}
		}
		return new String(charArray);
	}

	public static Map<String, Type> extractAttributeInformation(XLog log, boolean forgetTraceAttribute) {
		HashMap<String, Type> retValue = new HashMap<>();
		for (XTrace trace : log) {

			if (!forgetTraceAttribute) {
				for (XAttribute attr : trace.getAttributes().values()) {
					if (!shouldIgnoreAttribute(attr)) {
						Type classType = generateDataElement(attr);
						if (classType != null)
							retValue.put(fixVarName(attr.getKey()), classType);
					}
				}
			}

			/*
			 * For each event in the trace
			 */
			for (XEvent event : trace) {
				/*
				 * Extract each attribute, store in the HashMap
				 */
				for (XAttribute attr : event.getAttributes().values()) {
					if (!shouldIgnoreAttribute(attr)) {
						Type classType = generateDataElement(attr);
						if (classType != null) {
							String fixedVarName = fixVarName(attr.getKey());
							//TODO what if attributes have inconsistent types
							if (!retValue.containsKey(fixedVarName)) {
								retValue.put(fixedVarName, classType);
							}
						}
					}
				}
			}

		}
		/*
		 * return: Mapping of Attribute name to the Attribute Data Type in a
		 * HashMap<String, Type>
		 */
		return retValue;
	}

	private static boolean shouldIgnoreAttribute(XAttribute attr) {
		if (XOrganizationalExtension.KEY_ROLE.equals(attr.getKey())
				|| XOrganizationalExtension.KEY_RESOURCE.equals(attr.getKey())
				|| XOrganizationalExtension.KEY_GROUP.equals(attr.getKey())) {
			// organisational attributes might be interesting
			return false;
		}
		return XUtils.isStandardExtensionAttribute(attr);
	}

	/**
	 * Return the specific Data Type corresponding to the given XAttribute
	 * 
	 * @param xAttrib
	 * @return the Type of the XAttribute, corresponding to the actual
	 *         XAttribute instance of xAttrib.
	 */
	private static Type generateDataElement(XAttribute xAttrib) {

		if (xAttrib instanceof XAttributeBoolean) {
			return Type.BOOLEAN;
		} else if (xAttrib instanceof XAttributeContinuous) {
			return Type.CONTINUOS;
		} else if (xAttrib instanceof XAttributeDiscrete) {
			return Type.DISCRETE;
		} else if (xAttrib instanceof XAttributeTimestamp) {
			return Type.TIMESTAMP;
		} else if (xAttrib instanceof XAttributeLiteral) {
			return Type.LITERAL;
		}

		return null;
	}

}
