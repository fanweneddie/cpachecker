/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import java.util.Collection;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.AParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JAssumeEdge;
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

  @Override
  protected StringAnalysisState handleBlankEdge(BlankEdge cfaEdge) {
    return null;
  }

  @Override
  protected StringAnalysisState handleReturnStatementEdge(AReturnStatementEdge returnEdge) {
    return null;
  }

  @Override
  protected StringAnalysisState handleFunctionSummaryEdge(CFunctionSummaryEdge cfaEdge)
      throws CPATransferException {
    return null;
  }

  @Override
  protected StringAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {
    return null;
  }

  @Override
  protected StringAnalysisState handleDeclarationEdge(
      ADeclarationEdge declarationEdge, ADeclaration declaration) throws UnrecognizedCodeException {
    return null;
  }

  @Override
  protected StringAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {
    return null;
  }
}
