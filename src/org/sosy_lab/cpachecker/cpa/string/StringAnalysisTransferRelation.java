/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import static org.junit.Assert.assertNotNull;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.java.JAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JDeclarationEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class StringAnalysisTransferRelation
    extends ForwardingTransferRelation<StringAnalysisState, StringAnalysisState, VariableTrackingPrecision> {

  public StringAnalysisTransferRelation(
      StringAnalysisState pState, VariableTrackingPrecision pPrecision, String pFunctionName) {
    super();
    state = pState;
    precision = pPrecision;
    functionName = pFunctionName;
  }

  @Override
  protected @Nullable StringAnalysisState handleAssumption(
      JAssumeEdge cfaEdge, JExpression expression, boolean truthAssumption)
      throws CPATransferException {
    return null;
  }

  /**
   * Handle an edge that does nothing.
   * When we are exiting a function, we need to remove the variables in this function.
   * Note that function name has been set when we call {@link ForwardingTransferRelation#setInfo}.
   *
   * @param cfaEdge the edge to handle
   * @return the new abstract state after this edge
   */
  @Override
  protected StringAnalysisState handleBlankEdge(BlankEdge cfaEdge) {
    if (cfaEdge.getSuccessor() instanceof FunctionExitNode) {
      try {
        state = (StringAnalysisState) state.clone();
      } catch (CloneNotSupportedException cse) {
        state = new StringAnalysisState();
      }
      state.dropFrame(functionName);
    }

    return state;
  }

  /**
   * Handle a return edge of a function.
   * We just need to clear all variables that are defined in this function.
   *
   * @param returnEdge the given return edge to handle
   * @return the new abstract state after this edge
   */
  @Override
  protected StringAnalysisState handleReturnStatementEdge(AReturnStatementEdge returnEdge) {
    try {
      state = (StringAnalysisState) state.clone();
    } catch (CloneNotSupportedException cse) {
      state = new StringAnalysisState();
    }
    state.dropFrame(functionName);

    return state;
  }

  @Override
  protected StringAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {
    return null;
  }

  /**
   * Handle a declaration edge.
   * Note that we only deal with Java declaration.
   *
   * @param cfaEdge the Java declaration edge to handle, which must not be null
   * @param decl    the Java declaration to handle, which must not be null
   * @return the new abstract state after this edge
   */
  @Override
  protected StringAnalysisState handleDeclarationEdge(JDeclarationEdge cfaEdge, JDeclaration decl)
      throws CPATransferException {
    assertNotNull(cfaEdge);
    assertNotNull(decl);

    return null;
  }

  @Override
  protected StringAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {
    return null;
  }
}
