/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import java.util.List;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
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

  /**
   *
   * @param callEdge
   * @param arguments
   * @param parameters
   * @param calledFunctionName
   * @return
   * @throws UnrecognizedCodeException
   */
  @Override
  protected StringAnalysisState handleFunctionCallEdge(
      FunctionCallEdge callEdge,
      List<? extends AExpression> arguments, List<? extends AParameterDeclaration> parameters,
      String calledFunctionName) throws UnrecognizedCodeException {

  }

  @Override
  protected StringAnalysisState handleBlankEdge(BlankEdge cfaEdge) {
  }

  @Override
  protected StringAnalysisState handleReturnStatementEdge(AReturnStatementEdge returnEdge) {

  }

  @Override
  protected StringAnalysisState handleFunctionReturnEdge(
      FunctionReturnEdge functionReturnEdge,
      FunctionSummaryEdge summaryEdge, AFunctionCall exprOnSummary, String callerFunctionName)
      throws UnrecognizedCodeException {
  }

  @Override
  protected StringAnalysisState handleFunctionSummaryEdge(CFunctionSummaryEdge cfaEdge)
      throws CPATransferException {
  }

  @Override
  protected StringAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {

  }

  @Override
  protected StringAnalysisState handleDeclarationEdge(
      ADeclarationEdge declarationEdge, ADeclaration declaration) throws UnrecognizedCodeException {
  }

  @Override
  protected StringAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {
  }
}
