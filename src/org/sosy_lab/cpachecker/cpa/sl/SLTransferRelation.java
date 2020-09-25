// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.sl;

import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.pathformula.ctoformula.CToFormulaConverterWithSL;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.ProverEnvironment;
import org.sosy_lab.java_smt.api.SolverException;

public class SLTransferRelation extends SingleEdgeTransferRelation {

  private final LogManager logger;
  private final Solver solver;
  private final CToFormulaConverterWithSL converter;
  private final SLStatistics stats;

  public SLTransferRelation(
      LogManager pLogger,
      Solver pSolver,
      CToFormulaConverterWithSL pConverter,
      SLStatistics pStats) {
    logger = pLogger;
    solver = pSolver;
    converter = pConverter;
    stats = pStats;
  }

  @Override
  public Collection<? extends AbstractState>
      getAbstractSuccessorsForEdge(AbstractState pState, Precision pPrecision, CFAEdge pCfaEdge)
          throws CPATransferException, InterruptedException {
    SLState state = SLState.copyWithoutErrors((SLState) pState);
    state = converter.makeAnd(state, pCfaEdge);
    if (pCfaEdge instanceof AssumeEdge) {
      // Feasibility check
      return handleAssumption(state);
    }
    return ImmutableList.of(state);
  }

  private List<SLState> handleAssumption(SLState state)
      throws CPATransferException, InterruptedException {
    BooleanFormula constraints =
        solver.getFormulaManager().getBooleanFormulaManager().and(state.getConstraints());
    boolean unsat = false;
    try (ProverEnvironment prover = solver.newProverEnvironment()) {
      prover.addConstraint(constraints);
      stats.startSolverTime();
      unsat = prover.isUnsat();
    } catch (SolverException e) {
      throw new CPATransferException("Feasibility check failed.", e);
    } finally {
      stats.stopSolverTime();
    }
    if (unsat) {
      return ImmutableList.of();
    }
    return ImmutableList.of(state);
  }
}