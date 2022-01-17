// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.components.block_analysis;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.AnalysisDirection;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm.AlgorithmStatus;
import org.sosy_lab.cpachecker.core.algorithm.components.decomposition.BlockNode;
import org.sosy_lab.cpachecker.core.algorithm.components.exchange.Message;
import org.sosy_lab.cpachecker.core.algorithm.components.state_transformer.AnyStateTransformer;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.block.BlockCPA;
import org.sosy_lab.cpachecker.cpa.block.BlockCPABackward;
import org.sosy_lab.cpachecker.cpa.block.BlockStartReachedTargetInformation;
import org.sosy_lab.cpachecker.cpa.block.BlockState;
import org.sosy_lab.cpachecker.cpa.block.BlockTransferRelation;
import org.sosy_lab.cpachecker.cpa.composite.CompositeCPA;
import org.sosy_lab.cpachecker.cpa.composite.CompositeState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateAbstractState;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Triple;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverException;

public abstract class BlockAnalysis {

  protected final Solver solver;
  protected final FormulaManagerView fmgr;
  protected final BooleanFormulaManagerView bmgr;
  protected final PathFormulaManagerImpl pathFormulaManager;

  protected final Algorithm algorithm;
  protected final ReachedSet reachedSet;
  protected final ConfigurableProgramAnalysis cpa;
  protected final AnyStateTransformer transformer;

  protected final Precision emptyPrecision;

  protected final BlockNode block;
  protected final LogManager logger;

  protected AlgorithmStatus status;

  public BlockAnalysis(
      String pId,
      LogManager pLogger,
      BlockNode pBlock,
      CFA pCFA,
      AnalysisDirection pDirection,
      Specification pSpecification,
      Configuration pConfiguration,
      ShutdownManager pShutdownManager)
      throws CPAException, InterruptedException, InvalidConfigurationException, IOException {
    Triple<Algorithm, ConfigurableProgramAnalysis, ReachedSet> parts =
        AlgorithmFactory.createAlgorithm(pLogger, pSpecification, pCFA, pConfiguration,
            pShutdownManager,
            ImmutableSet.of(
                "analysis.algorithm.configurableComponents",
                "analysis.useLoopStructure",
                "cpa.predicate.blk.alwaysAtJoin",
                "cpa.predicate.blk.alwaysAtBranch",
                "cpa.predicate.blk.alwaysAtProgramExit"), pBlock);
    algorithm = parts.getFirst();
    cpa = parts.getSecond();
    reachedSet = parts.getThird();

    solver = extractAnalysis(cpa, PredicateCPA.class).getSolver();
    emptyPrecision = reachedSet.getPrecision(reachedSet.getFirstState());
    status = AlgorithmStatus.NO_PROPERTY_CHECKED;

    fmgr = solver.getFormulaManager();
    bmgr = fmgr.getBooleanFormulaManager();
    pathFormulaManager = new PathFormulaManagerImpl(
        solver.getFormulaManager(),
        pConfiguration,
        pLogger,
        pShutdownManager.getNotifier(),
        pCFA,
        pDirection);
    block = pBlock;
    logger = pLogger;

    transformer = new AnyStateTransformer(pId);
  }

  public Optional<CFANode> abstractStateToLocation(AbstractState state) {
    if (state instanceof LocationState) {
      return Optional.of(((LocationState) state).getLocationNode());
    }
    if (state instanceof BlockState) {
      return Optional.of(((BlockState) state).getLocationNode());
    }
    if (state instanceof CompositeState) {
      for (AbstractState wrappedState : ((CompositeState) state).getWrappedStates()) {
        Optional<CFANode> maybeNode = abstractStateToLocation(wrappedState);
        if (maybeNode.isPresent()) {
          return maybeNode;
        }
      }
    }
    if (state.getClass().equals(ARGState.class)) {
      return abstractStateToLocation(((ARGState) state).getWrappedState());
    }
    return Optional.empty();
  }

  public Map<AbstractState, BooleanFormula> transformReachedSet(
      ReachedSet pReachedSet,
      CFANode targetNode,
      AnalysisDirection direction) {
    Map<AbstractState, BooleanFormula> formulas = new HashMap<>();
    for (AbstractState abstractState : pReachedSet) {
      Optional<CFANode> optionalLocation = abstractStateToLocation(abstractState);
      if (optionalLocation.isPresent() && optionalLocation.orElseThrow().equals(targetNode)) {
        BooleanFormula formula =
            transformer.safeTransform(abstractState, fmgr, direction, block.getId());
        formulas.put(abstractState, formula);
      }
    }
    return formulas;
  }

  public <T extends AbstractState> ImmutableSet<T> extractStateFromCompositeState(
      Class<T> pTarget,
      CompositeState pCompositeState) {
    Set<T> result = new HashSet<>();
    for (AbstractState wrappedState : pCompositeState.getWrappedStates()) {
      if (pTarget.isAssignableFrom(wrappedState.getClass())) {
        result.add(pTarget.cast(wrappedState));
      } else if (wrappedState instanceof CompositeState) {
        result.addAll(extractStateFromCompositeState(pTarget, (CompositeState) wrappedState));
      }
    }
    return ImmutableSet.copyOf(result);
  }

  public CompositeState extractCompositeStateFromAbstractState(AbstractState state) {
    checkNotNull(state, "state cannot be null");
    checkState(state instanceof ARGState, "State has to be an ARGState");
    ARGState argState = (ARGState) state;
    checkState(argState.getWrappedState() instanceof CompositeState,
        "First state must contain a CompositeState");
    return (CompositeState) argState.getWrappedState();
  }

  public <T extends ConfigurableProgramAnalysis> T extractAnalysis(
      ConfigurableProgramAnalysis pCPA,
      Class<T> pTarget) {
    ARGCPA argCpa = (ARGCPA) pCPA;
    if (argCpa.getWrappedCPAs().size() > 1) {
      throw new AssertionError("Wrapper expected but got " + pCPA + "instead");
    }
    if (!(argCpa.getWrappedCPAs().get(0) instanceof CompositeCPA)) {
      throw new AssertionError(
          "Expected " + CompositeCPA.class + " but got " + argCpa.getWrappedCPAs().get(0)
              .getClass());
    }
    CompositeCPA compositeCPA = (CompositeCPA) argCpa.getWrappedCPAs().get(0);
    for (ConfigurableProgramAnalysis wrappedCPA : compositeCPA.getWrappedCPAs()) {
      if (wrappedCPA.getClass().equals(pTarget)) {
        return pTarget.cast(wrappedCPA);
      }
    }
    throw new AssertionError(
        "Expected analysis " + pTarget + " is not part of the composite cpa " + pCPA);
  }

  protected ARGState getStartState(PathFormula condition, CFANode node)
      throws InterruptedException {
    CompositeState compositeState =
        extractCompositeStateFromAbstractState(getInitialStateFor(node));
    PredicateAbstractState predicateState =
        extractStateFromCompositeState(PredicateAbstractState.class,
            compositeState)
            .stream().findFirst()
            .orElseThrow(() -> new AssertionError("Analysis has to contain a PredicateState"));
    predicateState = PredicateAbstractState.mkNonAbstractionStateWithNewPathFormula(
        condition,
        predicateState);
    List<AbstractState> states = new ArrayList<>();
    for (AbstractState wrappedState : compositeState.getWrappedStates()) {
      if (wrappedState instanceof PredicateAbstractState) {
        states.add(predicateState);
      } else {
        states.add(wrappedState);
      }
    }
    CompositeState actualStartState = new CompositeState(states);
    return new ARGState(actualStartState, null);
  }

  private AbstractState getInitialStateFor(CFANode pNode) throws InterruptedException {
    return cpa.getInitialState(pNode, StateSpacePartition.getDefaultPartition());
  }

  public Solver getSolver() {
    return solver;
  }

  public FormulaManagerView getFmgr() {
    return fmgr;
  }

  public Algorithm getAlgorithm() {
    return algorithm;
  }

  public BooleanFormulaManagerView getBmgr() {
    return bmgr;
  }

  public PathFormulaManagerImpl getPathFormulaManager() {
    return pathFormulaManager;
  }

  public Precision getEmptyPrecision() {
    return emptyPrecision;
  }

  public AlgorithmStatus getStatus() {
    return status;
  }

  public abstract Collection<Message> analyze(PathFormula condition, CFANode node)
      throws CPAException, InterruptedException, SolverException;

  public boolean cantContinue(String currentPreCondition, String receivedPostCondition)
      throws SolverException, InterruptedException {
    return solver.isUnsat(
        bmgr.and(fmgr.parse(currentPreCondition), fmgr.parse(receivedPostCondition)));
  }

  public static class ForwardAnalysis extends BlockAnalysis {

    private final BlockTransferRelation relation;
    private boolean reportedOriginalViolation;

    public ForwardAnalysis(
        String pId,
        LogManager pLogger,
        BlockNode pBlock,
        CFA pCFA,
        Specification pSpecification,
        Configuration pConfiguration,
        ShutdownManager pShutdownManager)
        throws CPAException, InterruptedException, InvalidConfigurationException, IOException {
      super(pId, pLogger, pBlock, pCFA, AnalysisDirection.FORWARD, pSpecification, pConfiguration,
          pShutdownManager);
      relation = (BlockTransferRelation) CPAs.retrieveCPA(cpa, BlockCPA.class).getTransferRelation();
    }

    @Override
    public Collection<Message> analyze(PathFormula condition, CFANode node)
        throws CPAException, InterruptedException {
      relation.init(block);
      reachedSet.clear();
      reachedSet.add(getStartState(condition, node), emptyPrecision);
      status = algorithm.run(reachedSet);
      Set<ARGState> targetStates = from(reachedSet).filter(AbstractStates::isTargetState)
          .filter(ARGState.class).copyInto(new HashSet<>());
      /*if (targetStates.isEmpty()) {
        throw new AssertionError("At least one target state has to exist (block start)");
      }*/

      // find violations for potential backward analysis
      Set<Message> answers = new HashSet<>();
      if (!reportedOriginalViolation) {
        for (ARGState targetState : targetStates) {
          int startInfos = from(targetState.getTargetInformation()).filter(
              BlockStartReachedTargetInformation.class).size();
          if (targetState.getTargetInformation().size() > startInfos) {
            Optional<CFANode> targetNode =
                abstractStateToLocation(targetState);
            if (targetNode.isEmpty()) {
              throw new AssertionError(
                  "States need to have a location but this one does not:" + targetState);
            }
            reportedOriginalViolation = true;
            answers.add(Message.newErrorConditionMessage(block.getId(),
                targetNode.orElseThrow().getNodeNumber(), bmgr.makeTrue(),
                fmgr, true));
            break;
          }
        }
      }

      // find all states with location at the end, make formula
      Map<AbstractState, BooleanFormula>
          formulas = transformReachedSet(reachedSet, block.getLastNode(),
          AnalysisDirection.FORWARD);
      BooleanFormula result = formulas.isEmpty() ? bmgr.makeTrue() : bmgr.or(formulas.values());
      if (!formulas.isEmpty() || block.getPredecessors().isEmpty()) {
        Message response = Message.newBlockPostCondition(block.getId(), block.getLastNode().getNodeNumber(),
            result,
            fmgr,
            block.getPredecessors().isEmpty());
        answers.add(response);
      }
      return answers;
    }
  }

  public static class BackwardAnalysis extends BlockAnalysis {

    private final BlockTransferRelation relation;

    public BackwardAnalysis(
        String pId,
        LogManager pLogger,
        BlockNode pBlock,
        CFA pCFA,
        Specification pSpecification,
        Configuration pConfiguration,
        ShutdownManager pShutdownManager)
        throws CPAException, InterruptedException, InvalidConfigurationException, IOException {
      super(pId, pLogger, pBlock, pCFA, AnalysisDirection.BACKWARD, pSpecification, pConfiguration,
          pShutdownManager);
      relation = (BlockTransferRelation) CPAs.retrieveCPA(cpa, BlockCPABackward.class).getTransferRelation();
    }

    @Override
    public Collection<Message> analyze(PathFormula condition, CFANode node)
        throws CPAException, InterruptedException, SolverException {
      relation.init(block);
      reachedSet.clear();
      reachedSet.add(getStartState(condition, node), emptyPrecision);
      status = algorithm.run(reachedSet);
      Map<AbstractState, BooleanFormula>
          formulas = transformReachedSet(reachedSet, block.getStartNode(),
          AnalysisDirection.BACKWARD);
      BooleanFormula result = bmgr.or(formulas.values()); // returns false if formulas is empty
      if (bmgr.isFalse(result) && !block.isEmpty()) {
        return ImmutableSet.of(Message.newErrorConditionUnreachableMessage(block.getId()));
      }
      // by definition: if the post-condition reaches the root element, the specification is violated
      // (as far as this block can tell)
      return ImmutableSet.of(Message.newErrorConditionMessage(block.getId(), block.getStartNode().getNodeNumber(),
          result, fmgr, false));
    }
  }

  public static class NoopAnalysis extends BlockAnalysis {

    public NoopAnalysis(
        String pId,
        LogManager pLogger,
        BlockNode pBlock,
        CFA pCFA,
        AnalysisDirection pDirection,
        Specification pSpecification,
        Configuration pConfiguration,
        ShutdownManager pShutdownManager)
        throws CPAException, InterruptedException, InvalidConfigurationException, IOException {
      super(pId, pLogger, pBlock, pCFA, pDirection, pSpecification, pConfiguration,
          pShutdownManager);
    }

    @Override
    public Collection<Message> analyze(
        PathFormula condition, CFANode node)
        throws CPAException, InterruptedException, SolverException {
      return ImmutableSet.of();
    }
  }
}