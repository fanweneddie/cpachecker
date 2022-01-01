// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2022 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.modificationsprop;

import static org.sosy_lab.common.collect.Collections3.transformedImmutableSetCopy;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.CFACreator;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.CPABuilder;
import org.sosy_lab.cpachecker.core.algorithm.CPAAlgorithm;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.StopSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.AggregatedReachedSets;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.ParserException;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.CToFormulaConverterWithPointerAliasing;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.FormulaEncodingWithPointerAliasingOptions;
import org.sosy_lab.cpachecker.util.predicates.pathformula.pointeraliasing.TypeHandlerWithPointerAliasing;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;

/** CPA for difference verification using property information. */
@Options(prefix = "differential")
public class ModificationsPropCPA implements ConfigurableProgramAnalysis, AutoCloseable {

  @Option(
      secure = true,
      description = "Program to check against",
      name = "program",
      required = true)
  @FileOption(Type.REQUIRED_INPUT_FILE)
  private Path originalProgram = null;

  @Option(
      secure = true,
      // this description is not 100% accurate (no mod detection) but matches the other modification
      // CPAs leading to only one documentation entry
      description =
          "ignore declarations when detecting modifications, "
              + "be careful when variables are renamed (could be unsound)")
  private boolean ignoreDeclarations = false;

  @Option(secure = true, description = "perform assumption implication check")
  private boolean implicationCheck = true;

  @Option(
      secure = true,
      description =
          "perform preprocessing to detect states from which error locations are reachable")
  private boolean performPreprocessing = false;

  @Option(secure = true, description = "safely stop analysis on pointer accesses and similar")
  private boolean stopOnPointers = false;

  @Option(
      secure = true,
      description = "Switch on/off to form the union of variable sets at identical location pairs")
  private boolean variableSetMerge = true;

  @Option(
      secure = true,
      name = "badstateProperties",
      description =
          "comma-separated list of files with property specifications that should be considered "
              + "when determining the nodes that are in the reachability property.")
  @FileOption(FileOption.Type.OPTIONAL_INPUT_FILE)
  private List<Path> relevantProperties = ImmutableList.of();

  private final Configuration config;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final CFA cfaForComparison;
  private final TransferRelation transfer;
  private final DelegateAbstractDomain<ModificationsPropState> domain;
  private final Solver solver;
  private final CtoFormulaConverter converter;
  private final ModificationsPropHelper helper;

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ModificationsPropCPA.class);
  }

  // originalProgram != null checked through REQUIRED_INPUT_FILE annotation
  @SuppressFBWarnings("NP")
  public ModificationsPropCPA(
      final CFA pCfa,
      final Configuration pConfig,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier)
      throws InvalidConfigurationException {
    pConfig.inject(this);

    domain = DelegateAbstractDomain.getInstance();
    config = pConfig;
    logger = pLogger;
    shutdownNotifier = pShutdownNotifier;
    solver = Solver.create(pConfig, pLogger, pShutdownNotifier);
    converter =
        initializeCToFormulaConverter(
            solver.getFormulaManager(),
            pLogger,
            pConfig,
            pShutdownNotifier,
            pCfa.getMachineModel());

    // create CFA here to avoid handling of checked exceptions in #getInitialState
    CFACreator cfaCreator = new CFACreator(config, logger, shutdownNotifier);
    try {
      cfaForComparison =
          cfaCreator.parseFileAndCreateCFA(ImmutableList.of(originalProgram.toString()));
      final ImmutableSet<CFANode> elocs = propertyNodes(cfaForComparison);
      final ImmutableSet<CFANode> elocs_new = propertyNodes(pCfa);

      // Backward analysis adding all predecessor nodes to find all nodes that may reach an error
      // location.
      final Set<CFANode>
          elocs_reachable =
              performPreprocessing ? explore(elocs, this::getPredecessors) : ImmutableSet.of(),
          elocs_new_reachable =
              performPreprocessing ? explore(elocs_new, this::getPredecessors) : ImmutableSet.of();

      helper =
          new ModificationsPropHelper(
              new ImmutableSet.Builder<CFANode>().addAll(elocs_new).addAll(elocs).build(),
              ignoreDeclarations,
              implicationCheck,
              stopOnPointers,
              performPreprocessing,
              elocs_reachable,
              elocs_new_reachable,
              solver,
              converter,
              logger);
      transfer = new ModificationsPropTransferRelation(helper);

    } catch (ParserException pE) {
      throw new InvalidConfigurationException("Parser error for original program.", pE);
    } catch (InterruptedException | IOException pE) {
      throw new AssertionError(pE);
    }
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return domain;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transfer;
  }

  @Override
  public MergeOperator getMergeOperator() {
    // check equality of location tuple and merge by joining then
    return variableSetMerge ? new ModificationsPropMergeOperator() : new MergeSepOperator();
  }

  @Override
  public StopOperator getStopOperator() {
    // merge if more abstract state exists
    return new StopSepOperator(getAbstractDomain());
  }

  @Override
  public AbstractState getInitialState(CFANode node, StateSpacePartition partition)
      throws InterruptedException {
    return new ModificationsPropState(
        node,
        cfaForComparison.getMainFunction(),
        ImmutableSet.of(),
        new ArrayDeque<CFANode>(),
        helper);
  }

  @Override
  public void close() {
    solver.close();
  }

  // Can only be called after machineModel and formulaManager are set
  private CtoFormulaConverter initializeCToFormulaConverter(
      FormulaManagerView pFormulaManager,
      LogManager pLogger,
      Configuration pConfig,
      ShutdownNotifier pShutdownNotifier,
      MachineModel pMachineModel)
      throws InvalidConfigurationException {

    FormulaEncodingWithPointerAliasingOptions options =
        new FormulaEncodingWithPointerAliasingOptions(pConfig);
    TypeHandlerWithPointerAliasing typeHandler =
        new TypeHandlerWithPointerAliasing(logger, pMachineModel, options);

    return new CToFormulaConverterWithPointerAliasing(
        options,
        pFormulaManager,
        pMachineModel,
        Optional.empty(),
        pLogger,
        pShutdownNotifier,
        typeHandler,
        AnalysisDirection.FORWARD);
  }

  private ImmutableSet<CFANode> propertyNodes(CFA cfa) {
    if (!relevantProperties.isEmpty()) {
      try {
        Configuration reachPropConfig =
            Configuration.builder()
                .setOption("cpa", "cpa.arg.ARGCPA")
                .setOption("ARGCPA.cpa", "cpa.composite.CompositeCPA")
                .setOption(
                    "CompositeCPA.cpas", "cpa.location.LocationCPA,cpa.callstack.CallstackCPA")
                .setOption("cpa.composite.aggregateBasicBlocks", "false")
                .setOption("cpa.callstack.skipRecursion", "true")
                .setOption("cpa.automaton.breakOnTargetState", "-1")
                .setOption("output.disable", "true")
                .setOption("analysis.entryFunction", "main")
                .build();

        ReachedSetFactory rsFactory = new ReachedSetFactory(reachPropConfig, logger);
        ConfigurableProgramAnalysis cpa =
            new CPABuilder(reachPropConfig, logger, shutdownNotifier, rsFactory)
                .buildCPAs(
                    cfa,
                    Specification.fromFiles(
                        relevantProperties, cfa, reachPropConfig, logger, shutdownNotifier),
                    AggregatedReachedSets.empty());
        ReachedSet reached =
            rsFactory.createAndInitialize(
                cpa, cfa.getMainFunction(), StateSpacePartition.getDefaultPartition());

        CPAAlgorithm.create(cpa, logger, reachPropConfig, shutdownNotifier).run(reached);
        Preconditions.checkState(!reached.hasWaitingState());

        Deque<ARGState> propertyARGNodes =
            new ArrayDeque<>(
                FluentIterable.from(reached.asCollection())
                    .filter(state -> ((ARGState) state).isTarget())
                    .transform(state -> (ARGState) state)
                    .toList());
        ImmutableSet.Builder<CFANode> builder = new ImmutableSet.Builder<>();
        for (ARGState argState : propertyARGNodes) {
          if (argState.getParents().isEmpty()) {
            // Initial node might be in property and not have a parent.
            builder.add(cfa.getMainFunction());
          }
          builder.addAll(
              transformedImmutableSetCopy(
                  argState.getParents(), el -> el.getEdgeToChild(argState).getSuccessor()));
        }
        return builder.build();
      } catch (InvalidConfigurationException | CPAException | InterruptedException e) {
        logger.logException(Level.SEVERE, e, "Failed to determine relevant edges");
      }
    }
    return ImmutableSet.of();
  }

  /**
   * Get all previous nodes in CFA for a given CFANode. Includes summary edge successors by using
   * allEnteringEdges as opposed to enteringEdges.
   *
   * @param node the node to consider
   * @return the predecessor nodes in the CFA
   */
  private FluentIterable<CFANode> getPredecessors(final CFANode node) {
    return CFAUtils.allEnteringEdges(node).transform(edge -> edge.getPredecessor());
  }

  /**
   * Performs an exploration by a starting element and a function to a following set
   *
   * @param <A> the element type to explore on
   * @param startset the initial set of elements
   * @param explorer the function mapping an element to a set of successors
   * @return the set of reached states including the initial elements
   */
  private <A> Set<A> explore(final Set<A> startset, final Function<A, Iterable<A>> explorer) {
    final Set<A> waitlist = new HashSet<>(startset), result = new HashSet<>(startset);
    while (!waitlist.isEmpty()) {
      for (A el : new HashSet<>(waitlist)) {
        waitlist.remove(el);
        result.add(el);
        for (A pred : explorer.apply(el)) {
          if (!result.contains(pred)) {
            waitlist.add(pred);
          }
        }
      }
    }
    return new ImmutableSet.Builder<A>().addAll(result).build();
  }
}
