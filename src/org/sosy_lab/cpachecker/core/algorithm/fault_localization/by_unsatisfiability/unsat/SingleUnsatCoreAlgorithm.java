// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.unsat;

import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.FaultLocalizerWithTraceFormula;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.FormulaContext;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.Selector;
import org.sosy_lab.cpachecker.core.algorithm.fault_localization.by_unsatisfiability.trace_formula.TraceFormula;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.faultlocalization.Fault;
import org.sosy_lab.cpachecker.util.predicates.smt.Solver;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.BooleanFormulaManager;
import org.sosy_lab.java_smt.api.SolverException;

public class SingleUnsatCoreAlgorithm
    implements FaultLocalizerWithTraceFormula, StatisticsProvider {

  private final MaxSatStatistics stats = new MaxSatStatistics();

  /**
   * Calculate a single unsat-core (not necessarily minimal) and return the set.
   *
   * @param context the formula context
   * @param tf the traceformula for the program
   * @return one UNSAT-core
   */
  @Override
  public Set<Fault> run(FormulaContext context, TraceFormula tf)
      throws CPATransferException, InterruptedException, SolverException, VerifyException {

    Selector.Factory selectorFactory = tf.getSelectorFactory();
    Solver solver = context.getSolver();
    BooleanFormulaManager bmgr = solver.getFormulaManager().getBooleanFormulaManager();
    stats.totalTime.start();
    // if precondition is not needed to guarantee unsatisfiability, do not add it and obtain better
    // results (i.e. find locations independently from the inputs first).
    BooleanFormula toVerify = bmgr.and(tf.getTrace(), tf.getPostcondition());
    if (!solver.isUnsat(toVerify)) {
      toVerify = bmgr.and(tf.getPrecondition(), toVerify);
    }

    // calculate an arbitrary UNSAT-core and filter the ones with selectors
    List<Selector> unsatCore =
        solver.unsatCore(toVerify).stream()
            .filter(l -> selectorFactory.selectorOf(l).isPresent())
            .map(l -> selectorFactory.selectorOf(l).orElseThrow())
            .collect(ImmutableList.toImmutableList());

    stats.totalTime.stop();
    Set<Fault> resultSet = new HashSet<>();
    resultSet.add(new Fault(new HashSet<>(unsatCore)));
    return resultSet;
  }

  @Override
  public void collectStatistics(Collection<Statistics> statsCollection) {
    statsCollection.add(stats);
  }
}
