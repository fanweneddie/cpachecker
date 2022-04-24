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
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AInitializer;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JFieldDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.cfa.model.java.JAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.java.JDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.types.java.JClassType;
import org.sosy_lab.cpachecker.cfa.types.java.JType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

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
   * When we are exiting a function, we need to remove the variables in this function.
   * Note that function name has been set when we call {@link ForwardingTransferRelation#setInfo}.
   *
   * @param cfaEdge the edge to handle
   * @return the new abstract state after this edge
   */
  @Override
  protected StringRelationAnalysisState handleBlankEdge(BlankEdge cfaEdge) {
    if (cfaEdge.getSuccessor() instanceof FunctionExitNode) {
      try {
        state = (StringRelationAnalysisState) state.clone();
      } catch (CloneNotSupportedException cse) {
        state = new StringRelationAnalysisState();
      }
      //state.dropFrame(functionName);
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
  protected StringRelationAnalysisState handleReturnStatementEdge(AReturnStatementEdge returnEdge) {
    try {
      state = (StringRelationAnalysisState) state.clone();
    } catch (CloneNotSupportedException cse) {
      state = new StringRelationAnalysisState();
    }
    //state.dropFrame(functionName);

    return state;
  }

  @Override
  protected StringRelationAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {
    return null;
  }

  /**
   * Handle a Java declaration edge,
   * which may contain variable declaration or field declaration.
   *
   * @param cfaEdge the Java declaration edge to handle, which must not be null
   * @param decl    the Java declaration to handle, which must not be null
   * @return the new abstract state after this edge
   */
  @Override
  protected StringRelationAnalysisState handleDeclarationEdge(JDeclarationEdge cfaEdge, JDeclaration decl)
      throws CPATransferException {
    assertNotNull(cfaEdge);
    assertNotNull(decl);

    // 1. deal with variable declaration
    if (decl instanceof JVariableDeclaration) {
       return handleJVariableDeclaration((JVariableDeclaration) decl);
    }
    // 2. deal with field declaration
    else if (decl instanceof JFieldDeclaration){
      return handleJFieldDeclaration((JFieldDeclaration) decl);
    }
    // 3. otherwise, the state is not changed
    else {
      return state;
    }
  }

  /**
   * Handle a Java variable declaration, and generate a new abstract state if it is a string variable.
   * @param decl the given Java variable declaration, which must not be null
   * @return the new abstract state after this declaration
   */
  protected StringRelationAnalysisState handleJVariableDeclaration(JVariableDeclaration decl) {
    assertNotNull(decl);

    if (!isJavaGenericStringType(decl.getType())) {
      return state;
    }

    AInitializer init = decl.getInitializer();
    if (init instanceof JInitializerExpression) {
      JExpression exp = ((JInitializerExpression) init).getExpression();
      //initialValue =
    }


    return null;
  }

  protected StringRelationAnalysisState handleJFieldDeclaration(JFieldDeclaration decl) {
    return null;
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
  private static final boolean isJavaStringType(JType type) {
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

  private static final boolean isJavaGenericStringType(JType type) {
    return isJavaStringType(type); // ||
  }
}
