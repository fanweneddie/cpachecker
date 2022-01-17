// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.components.worker;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.components.decomposition.BlockNode;
import org.sosy_lab.cpachecker.core.algorithm.components.exchange.Message;
import org.sosy_lab.cpachecker.core.algorithm.components.exchange.Message.MessageType;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.FormulaContext;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.FormulaEntryList;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.Selector.Factory;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.TraceFormula;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.TraceFormula.SelectorTraceWithKnownConditions;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.TraceFormula.TraceFormulaOptions;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.unsat.OriginalMaxSatAlgorithm;
import org.sosy_lab.cpachecker.core.specification.Specification;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.faultlocalization.Fault;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.Model.ValueAssignment;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverContext.ProverOptions;
import org.sosy_lab.java_smt.api.SolverException;

public class FaultLocalizationWorker extends AnalysisWorker {

  private final FormulaContext context;
  private final TraceFormulaOptions options;
  private final BooleanFormulaManagerView bmgr;

  private Fault minimalFault;

  private final List<CFAEdge> errorPath;

  FaultLocalizationWorker(
      String pId,
      BlockNode pBlock,
      LogManager pLogger,
      CFA pCFA,
      Specification pSpecification,
      Configuration pConfiguration,
      ShutdownManager pShutdownManager,
      SSAMap pTypeMap)
      throws CPAException, IOException, InterruptedException, InvalidConfigurationException {
    super(pId, pBlock, pLogger, pCFA, pSpecification, pConfiguration, pShutdownManager, pTypeMap);
    context = new FormulaContext(
        backwardAnalysis.getSolver(),
        backwardAnalysis.getPathFormulaManager(),
        pCFA,
        pLogger,
        pConfiguration,
        pShutdownManager.getNotifier()
    );
    options = new TraceFormulaOptions(pConfiguration);
    bmgr = backwardAnalysis.getFmgr().getBooleanFormulaManager();
    errorPath = new ArrayList<>();
    minimalFault = new Fault();

    CFANode currNode = pBlock.getStartNode();
    do {
      for (CFAEdge leavingEdge : CFAUtils.leavingEdges(currNode)) {
        if (block.getNodesInBlock().contains(leavingEdge.getSuccessor())) {
          errorPath.add(leavingEdge);
          currNode = leavingEdge.getSuccessor();
          break;
        }
      }
    } while (!currNode.equals(pBlock.getLastNode()));
  }

  public Collection<Message> processMessage(Message pMessage)
      throws InterruptedException, IOException,
             SolverException, CPAException {
    if (pMessage.getType() == MessageType.FOUND_RESULT
        && Result.valueOf(pMessage.getPayload()) == Result.FALSE) {
      if (!minimalFault.isEmpty()) {
        logger.log(Level.INFO, getBlockId() + ": " + minimalFault);
      }
    }
    return super.processMessage(pMessage);
  }

  @Override
  protected Collection<Message> backwardAnalysis(CFANode pStartNode, PathFormula pFormula)
      throws CPAException, SolverException, InterruptedException {
    Set<Message> responses = new HashSet<>(super.backwardAnalysis(pStartNode, pFormula));
    try {
      Set<Fault> faults = performFaultLocalization(pFormula);
      if (faults.isEmpty()) {
        return responses;
      }
      minimalFault = faults.stream().min(Comparator.comparingInt(f -> f.size())).orElseThrow();
    } catch (IOException pE) {
      throw new CPAException("IO Exception", pE);
    }
    return responses;
  }

  public Set<Fault> performFaultLocalization(PathFormula pPostCondition)
      throws CPATransferException, InterruptedException, SolverException, IOException {
    TraceFormula tf = createTraceFormula(pPostCondition);
    if (!tf.isCalculationPossible()) {
      return ImmutableSet.of();
    }
    return new OriginalMaxSatAlgorithm().run(context, tf);
  }

  public TraceFormula createTraceFormula(PathFormula pPostCondition)
      throws CPATransferException, InterruptedException, IOException, SolverException {
    PathFormulaManagerImpl pathFormulaManager = context.getManager();
    FormulaEntryList entries = new FormulaEntryList();
    PathFormula current = pPostCondition;
    BooleanFormula oldFormula = current.getFormula();
    Factory selectorFactory = new Factory();
    int id = 0;
    for (CFAEdge cfaEdge : errorPath) {
      current = pathFormulaManager.makeAnd(current, cfaEdge);
      if (current.getFormula().equals(oldFormula)) {
        continue;
      }
      BooleanFormula newFormula = current.getFormula();
      List<BooleanFormula> parts = new ArrayList<>(bmgr.toConjunctionArgs(newFormula, false));
      BooleanFormula correctPart = null;
      if (parts.size() == 1 && bmgr.isTrue(oldFormula)) {
        correctPart = parts.get(0);
      }
      if (parts.size() == 2) {
        if (parts.get(0).equals(oldFormula)) {
          correctPart = parts.get(1);
        } else {
          correctPart = parts.get(0);
        }
      }
      if (correctPart == null) {
        throw new AssertionError(
            "Splitting a BooleanFormula has to result in exactly two formulas: " + parts);
      }
      entries.addEntry(id, current.getSsa(),
          selectorFactory.makeSelector(context, correctPart, cfaEdge), correctPart);
      oldFormula = current.getFormula();
      id++;
    }
    Map<String, Integer> minimalIndices = new HashMap<>();
    Map<String, BooleanFormula> minimalFormulas = new HashMap<>();
    try (ProverEnvironment prover = backwardAnalysis.getSolver().newProverEnvironment(ProverOptions.GENERATE_MODELS)) {
      prover.push(current.getFormula());
      Preconditions.checkArgument(!prover.isUnsat(), "a model has to be existent");
      for (ValueAssignment modelAssignment : prover.getModelAssignments()) {
        Pair<String, OptionalInt> pair = FormulaManagerView.parseName(modelAssignment.getName());
        int newVal = minimalIndices.merge(pair.getFirst(), pair.getSecond().orElse(-2), Integer::max);
        if (newVal == pair.getSecond().orElse(-2)) {
          minimalFormulas.put(pair.getFirst(), modelAssignment.getAssignmentAsFormula());
        }
      }
    }
    BooleanFormula precondition = bmgr.and(minimalFormulas.values());
    return new SelectorTraceWithKnownConditions(context, options, entries, precondition,
        bmgr.isTrue(pPostCondition.getFormula()) ? pPostCondition.getFormula() : bmgr.not(pPostCondition.getFormula()), errorPath);
  }
}