// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.predicates;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.Appenders.AbstractAppender;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.io.PathTemplate;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.core.counterexample.CFAPathWithAssumptions;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithAssumptions;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.arg.path.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.path.PathIterator;
import org.sosy_lab.cpachecker.cpa.overflow.OverflowState;
import org.sosy_lab.cpachecker.cpa.predicate.BAMBlockFormulaStrategy;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Model.ValueAssignment;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;

/**
 * This class can check feasibility of a simple path using an SMT solver.
 */
@Options(prefix="counterexample.export", deprecatedPrefix="cpa.predicate")
public class PathChecker {

  @Option(
      secure = true,
      name = "formula",
      deprecatedName = "dumpCounterexampleFormula",
      description =
          "where to dump the counterexample formula in case a specification violation is found")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate dumpCounterexampleFormula =
      PathTemplate.ofFormatString("Counterexample.%d.smt2");

  @Option(
      secure = true,
      name = "model",
      deprecatedName = "dumpCounterexampleModel",
      description =
          "where to dump the counterexample model in case a specification violation is found")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private PathTemplate dumpCounterexampleModel =
      PathTemplate.ofFormatString("Counterexample.%d.assignment.txt");

  @Option(
      secure = true,
      description =
          "An imprecise counterexample of the Predicate CPA is usually a bug,"
              + " but expected in some configurations. Should it be treated as a bug or accepted?")
  private boolean allowImpreciseCounterexamples = false;

  @Option(secure = true, description = "Always use imprecise counterexamples of the Predicate CPA")
  private boolean alwaysUseImpreciseCounterexamples = false;

  @Option(
      secure = true,
      description =
          "Strengthen a found counterexample with information from the SMT solver's model")
  private boolean useSmtInfosForCounterexample = true;

  private final LogManager logger;
  private final PathFormulaManager pmgr;
  private final Solver solver;
  private final AssignmentToPathAllocator assignmentToPathAllocator;

  public PathChecker(Configuration pConfig,
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      MachineModel pMachineModel,
      PathFormulaManager pPmgr,
      Solver pSolver) throws InvalidConfigurationException {
    this(pConfig, pLogger, pPmgr, pSolver, new AssignmentToPathAllocator(pConfig, pShutdownNotifier, pLogger, pMachineModel));
  }

  public PathChecker(
      Configuration pConfig,
      LogManager pLogger,
      PathFormulaManager pPmgr,
      Solver pSolver,
      AssignmentToPathAllocator pAssignmentToPathAllocator) throws InvalidConfigurationException {
    this.logger = pLogger;
    this.pmgr = pPmgr;
    this.solver = pSolver;
    this.assignmentToPathAllocator = pAssignmentToPathAllocator;
    pConfig.inject(this);
  }

  public CounterexampleInfo handleFeasibleCounterexample(final ARGPath allStatesTrace,
      CounterexampleTraceInfo counterexample, boolean branchingOccurred)
          throws InterruptedException {
    ARGPath targetPath;

    if (alwaysUseImpreciseCounterexamples) {
      return createImpreciseCounterexample(allStatesTrace, counterexample);
    }

    checkArgument(!counterexample.isSpurious());
    if (branchingOccurred) {
      Map<Integer, Boolean> preds = counterexample.getBranchingPredicates();
      if (preds.isEmpty()) {
        logger.log(Level.WARNING, "No information about ARG branches available!");
        return createImpreciseCounterexample(allStatesTrace, counterexample);
      }

      // find correct path
      try {
        ARGState root = allStatesTrace.getFirstState();
        ARGState target = allStatesTrace.getLastState();
        Set<ARGState> pathElements = ARGUtils.getAllStatesOnPathsTo(target);

        targetPath = ARGUtils.getPathFromBranchingInformation(root, target, pathElements, preds);

      } catch (IllegalArgumentException e) {
        logger.logUserException(Level.WARNING, e, null);
        logger.log(Level.WARNING, "The error path and the satisfying assignment may be imprecise!");

        return createImpreciseCounterexample(allStatesTrace, counterexample);
      }

    } else {
      targetPath = allStatesTrace;
    }

    return createCounterexample(targetPath, counterexample);
  }

  /**
   * Create a {@link CounterexampleInfo} object for a given counterexample.
   * The path will be checked again with an SMT solver to extract a model
   * that is as precise and simple as possible.
   * We assume that one additional SMT query will not cause too much overhead.
   * If the double-check fails, an imprecise result is returned.
   *
   * @param precisePath The precise ARGPath that represents the counterexample.
   * @param pInfo More information about the counterexample
   * @return a {@link CounterexampleInfo} instance
   */
  public CounterexampleInfo createCounterexample(
      final ARGPath precisePath, final CounterexampleTraceInfo pInfo) throws InterruptedException {

    if (!useSmtInfosForCounterexample) {
      return createImpreciseCounterexample(precisePath, pInfo);
    }

    CFAPathWithAssumptions pathWithAssignments;
    CounterexampleTraceInfo preciseInfo;
    try {
        Pair<CounterexampleTraceInfo, CFAPathWithAssumptions> replayedPathResult =
            checkPath(precisePath);

        if (replayedPathResult.getFirst().isSpurious()) {
          logger.log(Level.WARNING, "Inconsistent replayed error path!");
          logger.log(Level.WARNING, "The satisfying assignment may be imprecise!");
          return createImpreciseCounterexample(precisePath, pInfo);

        } else {
          preciseInfo = replayedPathResult.getFirst();
          pathWithAssignments = replayedPathResult.getSecond();
        }
    } catch (SolverException | CPATransferException e) {
      // path is now suddenly a problem
      logger.logUserException(
          Level.WARNING, e, "Could not replay error path to get a more precise model");
      logger.log(Level.WARNING, "The satisfying assignment may be imprecise!");
      return createImpreciseCounterexample(precisePath, pInfo);
    }

    CounterexampleInfo cex = CounterexampleInfo.feasiblePrecise(precisePath, pathWithAssignments);
    addCounterexampleFormula(preciseInfo, cex);
    addCounterexampleModel(preciseInfo, cex);
    return cex;
  }

  /**
   * Create a {@link CounterexampleInfo} object for a given counterexample.
   * Use this method if a precise {@link ARGPath} for the counterexample could not be constructed.
   *
   * @param imprecisePath Some ARGPath that is related to the counterexample.
   * @param pInfo More information about the counterexample
   * @return a {@link CounterexampleInfo} instance
   */
  private CounterexampleInfo createImpreciseCounterexample(
      final ARGPath imprecisePath, final CounterexampleTraceInfo pInfo) {
    if (!allowImpreciseCounterexamples) {
      throw new AssertionError(
          "Found imprecise counterexample in PredicateCPA. "
              + "If this is expected for this configuration "
              + "(e.g., because of UF-based heap encoding), "
              + "set counterexample.export.allowImpreciseCounterexamples=true. "
              + "Otherwise please report this as a bug.");
    }
    CounterexampleInfo cex =
        CounterexampleInfo.feasibleImprecise(imprecisePath);
    if (!alwaysUseImpreciseCounterexamples) {
      addCounterexampleFormula(pInfo, cex);
      addCounterexampleModel(pInfo, cex);
    }
    return cex;
  }

  private void addCounterexampleModel(
      CounterexampleTraceInfo cexInfo, CounterexampleInfo counterexample) {
    final ImmutableList<ValueAssignment> model = cexInfo.getModel();

    counterexample.addFurtherInformation(
        new AbstractAppender() {
          @Override
          public void appendTo(Appendable out) throws IOException {
            ImmutableList<String> lines =
                ImmutableList.sortedCopyOf(Lists.transform(model, Object::toString));
            Joiner.on('\n').appendTo(out, lines);
          }
        },
        dumpCounterexampleModel);
  }

  private void addCounterexampleFormula(
      CounterexampleTraceInfo cexInfo, CounterexampleInfo counterexample) {
    FormulaManagerView fmgr = solver.getFormulaManager();
    BooleanFormulaManagerView bfmgr = fmgr.getBooleanFormulaManager();

    BooleanFormula f = bfmgr.and(cexInfo.getCounterExampleFormulas());
    if (!bfmgr.isTrue(f)) {
      counterexample.addFurtherInformation(fmgr.dumpFormula(f), dumpCounterexampleFormula);
    }
  }

  private Pair<CounterexampleTraceInfo, CFAPathWithAssumptions> checkPath(ARGPath pPath)
      throws SolverException, CPATransferException, InterruptedException {

    Pair<PathFormula, List<SSAMap>> result = createPrecisePathFormula(pPath);

    List<SSAMap> ssaMaps = result.getSecond();

    PathFormula pathFormula = result.getFirstNotNull();

    BooleanFormula f = pathFormula.getFormula();

    try (ProverEnvironment thmProver = solver.newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
      thmProver.push(f);
      if (thmProver.isUnsat()) {
        return Pair.of(CounterexampleTraceInfo.infeasibleNoItp(), null);
      } else {
        List<ValueAssignment> model = getModel(thmProver);
        CFAPathWithAssumptions pathWithAssignments =
            assignmentToPathAllocator.allocateAssignmentsToPath(pPath, model, ssaMaps);

        return Pair.of(
            CounterexampleTraceInfo.feasible(ImmutableList.of(f), model, ImmutableMap.of()),
            pathWithAssignments);
      }
    }
  }

  /**
   * Calculate the precise PathFormula and SSAMaps for the given path. Multi-edges will be resolved.
   * The resulting list of SSAMaps need not be the same size as the given path.
   *
   * <p>If the path traverses recursive function calls, the path formula updates the SSAMaps.
   *
   * @param pPath calculate the precise list of SSAMaps for this path.
   * @return the PathFormula and the precise list of SSAMaps for the given path.
   */
  private Pair<PathFormula, List<SSAMap>> createPrecisePathFormula(ARGPath pPath)
      throws CPATransferException, InterruptedException {

    List<SSAMap> ssaMaps = new ArrayList<>(pPath.size());

    PathFormula pathFormula = pmgr.makeEmptyPathFormula();

    PathIterator pathIt = pPath.fullPathIterator();

    // for recursion we need to update SSA-indices after returning from a function call,
    // in non-recursive cases this should not change anything.
    Deque<PathFormula> callstack = new ArrayDeque<>();

    while (pathIt.hasNext()) {
      if (pathIt.isPositionWithState()) {
        pathFormula = addAssumptions(pathFormula, pathIt.getAbstractState());
      }
      CFAEdge edge = pathIt.getOutgoingEdge();
      pathIt.advance();

      if (!pathIt.hasNext() && pathIt.isPositionWithState()) {
        pathFormula = addAssumptions(pathFormula, pathIt.getAbstractState());
      }

      // for recursion
      if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
        callstack.push(pathFormula);
      }

      // for recursion
      if (!callstack.isEmpty() && edge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
        pathFormula =
            BAMBlockFormulaStrategy.rebuildStateAfterFunctionCall(
                pathFormula, callstack.pop(), ((FunctionReturnEdge) edge).getPredecessor());
      }

      pathFormula = pmgr.makeAnd(pathFormula, edge);
      ssaMaps.add(pathFormula.getSsa());
    }

    return Pair.of(pathFormula, ssaMaps);
  }

  private PathFormula addAssumptions(PathFormula pathFormula, ARGState nextState)
      throws CPATransferException, InterruptedException {
    if (nextState != null) {
      FluentIterable<AbstractStateWithAssumptions> assumptionStates =
          AbstractStates.projectToType(
              AbstractStates.asIterable(nextState), AbstractStateWithAssumptions.class);
      for (AbstractStateWithAssumptions assumptionState : assumptionStates) {
        if (assumptionState instanceof OverflowState
            && ((OverflowState) assumptionState).hasOverflow()) {
          assumptionState = ((OverflowState) assumptionState).getParent();
        }
        for (AExpression expr : assumptionState.getAssumptions()) {
          assert expr instanceof CExpression : "Expected a CExpression as assumption!";
          pathFormula = pmgr.makeAnd(pathFormula, (CExpression) expr);
        }
      }
    }
    return pathFormula;
  }

  private List<ValueAssignment> getModel(ProverEnvironment thmProver) {
    try {
      return thmProver.getModelAssignments();
    } catch (SolverException e) {
      logger.log(
          Level.WARNING,
          "Solver could not produce model, variable assignment of error path can not be dumped.");
      logger.logDebugException(e);
      return ImmutableList.of();
    }
  }
}