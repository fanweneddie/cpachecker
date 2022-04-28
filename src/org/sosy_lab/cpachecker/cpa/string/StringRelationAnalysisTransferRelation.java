/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AInitializer;
import org.sosy_lab.cpachecker.cfa.ast.AInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.java.JAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.java.JClassType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.string.StringRelationAnalysisState.StringRelationLabel;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.states.MemoryLocationVisitor;

public class StringRelationAnalysisTransferRelation
    extends ForwardingTransferRelation<StringRelationAnalysisState, StringRelationAnalysisState, VariableTrackingPrecision> {

  public StringRelationAnalysisTransferRelation(
      StringRelationAnalysisState pState, VariableTrackingPrecision pPrecision, String pFunctionName) {
    super();
    state = pState;
    precision = pPrecision;
    functionName = pFunctionName;
  }

  @Override
  protected @Nullable StringRelationAnalysisState handleAssumption(
      JAssumeEdge cfaEdge, JExpression expression, boolean truthAssumption)
      throws CPATransferException {
    return null;
  }

  /**
   * Handle an edge that does nothing.
   * We do nothing in the first round.
   * However, in the strengthening stage, if the edge is a function exit edge,
   * we need to remove the relation with string variables that are no longer alive.
   *
   * @param cfaEdge the edge to handle
   * @return the same state prior to <code>cfaEdge</code>
   */
  @Override
  protected StringRelationAnalysisState handleBlankEdge(BlankEdge cfaEdge) {
    if (cfaEdge.getSuccessor() instanceof FunctionExitNode) {
      state = StringRelationAnalysisState.deepCopyOf(state);
      state.dropFrame(functionName);
    }

    return state;
  }

  /**
   * Handle a return edge of a function.
   * We just need to clear the relation among all variables that are defined in this function.
   *
   * @param returnEdge the given return edge to handle
   * @return the new abstract state after this edge
   */
  @Override
  protected StringRelationAnalysisState handleReturnStatementEdge(AReturnStatementEdge returnEdge) {
    state = StringRelationAnalysisState.deepCopyOf(state);
    state.dropFrame(functionName);

    return state;
  }

  @Override
  protected StringRelationAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {
    return null;
  }

  /**
   * Handle a declaration edge for local string variables.
   * We just first kill the relation between that LHS variable and other variables,
   * and then add equation relation between LHS variable and RHS variable.
   * @param declarationEdge the given declaration, which must not be null
   * @param declaration the declaration of <code>declarationEdge</code>
   * @return the new state after <code>declarationEdge</code>
   * @throws UnrecognizedCodeException
   */
  @Override
  protected StringRelationAnalysisState handleDeclarationEdge(
      ADeclarationEdge declarationEdge, ADeclaration declaration) throws UnrecognizedCodeException {

    // nothing interesting to see here, please move along
    // we also don't deal with declaration of global variable
    if (!(declaration instanceof AVariableDeclaration) ||
        !isJavaGenericStringType(declaration.getType()) ||
        declaration.isGlobal()) {
      return state;
    }

    AVariableDeclaration decl = (AVariableDeclaration) declaration;
    String varName = decl.getName();
    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);

    // kill the original relation with LHSVariable
    MemoryLocation LHSVariable = MemoryLocation.forLocalVariable(functionName, varName);
    newState.killVariableRelation(LHSVariable);

    // add the equation relation between LHSVariable and RHSVariable
    AInitializer init = decl.getInitializer();
    if (init instanceof AInitializerExpression) {
      MemoryLocationVisitor mlv = getVisitor();
      AExpression exp = ((AInitializerExpression) init).getExpression();
      MemoryLocation RHSVariable = getExpressionMemLocation(exp, mlv);
      if (RHSVariable != null) {
        newState.addRelation(LHSVariable, RHSVariable, StringRelationLabel.EQUAL);
      }
    }

    return newState;
  }

  @Override
  protected StringRelationAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {
    return null;
  }

  /**
   * Check whether a given Java Type is Java String type.
   * @param type the given Java type
   * @return true if <code>type</code> is not null and is a String type
   */
  private static final boolean isJavaStringType(Type type) {
    if (!(type instanceof JClassType)) {
      return false;
    }

    JClassType classType = (JClassType) type;
    if (classType.getName().equals("java.lang.String")) {
      return true;
    } else {
      return false;
    }
  }

  private static final boolean isJavaGenericStringType(Type type) {
    return isJavaStringType(type); // ||
  }

  /** returns an initialized, empty visitor */
  private MemoryLocationVisitor getVisitor() {
    return new MemoryLocationVisitor(functionName);
  }

  private MemoryLocation getExpressionMemLocation(AExpression expression,
                                                  MemoryLocationVisitor mlv) {
    if (expression instanceof JIdExpression) {
      JIdExpression IdExpression = (JIdExpression) expression;
      return IdExpression.accept(mlv);
    } else {
      return null;
    }
  }
}
