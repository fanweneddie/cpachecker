/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.assumptions.storage;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.sosy_lab.cpachecker.cfa.ast.IASTExpression;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AssumptionReportingState;
import org.sosy_lab.cpachecker.core.interfaces.conditions.AvoidanceReportingState;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCCodeException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.predicates.CtoFormulaConverter;
import org.sosy_lab.cpachecker.util.predicates.SSAMap;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;

import com.google.common.base.Preconditions;

/**
 * Transfer relation and strengthening for the DumpInvariant CPA
 */
public class AssumptionStorageTransferRelation implements TransferRelation {

  private final CtoFormulaConverter converter;
  private final FormulaManager formulaManager;

  private final Collection<AbstractState> topStateSet;

  public AssumptionStorageTransferRelation(CtoFormulaConverter pConverter,
      FormulaManager pFormulaManager, AbstractState pTopState) {
    converter = pConverter;
    formulaManager = pFormulaManager;
    topStateSet = Collections.singleton(pTopState);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState pElement, Precision pPrecision, CFAEdge pCfaEdge) {
    AssumptionStorageState element = (AssumptionStorageState)pElement;

    // If we must stop, then let's stop by returning an empty set
    if (element.isStop()) {
      return Collections.emptySet();
    }

    return topStateSet;
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState el, List<AbstractState> others, CFAEdge edge, Precision p) throws UnrecognizedCCodeException {
    AssumptionStorageState asmptStorageElem = (AssumptionStorageState)el;
    assert asmptStorageElem.getAssumption().isTrue();
    assert asmptStorageElem.getStopFormula().isTrue();
    String function = (edge.getSuccessor() != null) ? edge.getSuccessor().getFunctionName() : null;

    Formula assumption = formulaManager.makeTrue();
    Formula stopFormula = formulaManager.makeFalse(); // initialize with false because we create a disjunction

    // process stop flag
    boolean stop = false;

    for (AbstractState element : AbstractStates.asIterable(others)) {
      if (element instanceof AssumptionReportingState) {
        IASTExpression inv = ((AssumptionReportingState)element).getAssumption();
        Formula invFormula = converter.makePredicate(inv, edge, function, SSAMap.emptySSAMap().builder());
        assumption = formulaManager.makeAnd(assumption, formulaManager.uninstantiate(invFormula));
      }

      if (element instanceof AvoidanceReportingState) {
        AvoidanceReportingState e = (AvoidanceReportingState)element;

        if (e.mustDumpAssumptionForAvoidance()) {
          stopFormula = formulaManager.makeOr(stopFormula, e.getReasonFormula(formulaManager));
          stop = true;
        }
      }
    }
    Preconditions.checkState(!stopFormula.isTrue());

    if (!stop) {
      stopFormula = formulaManager.makeTrue();
    }

    if (assumption.isTrue() && stopFormula.isTrue()) {
      return null; // nothing has changed

    } else {
      return Collections.singleton(new AssumptionStorageState(assumption, stopFormula));
    }
  }
}