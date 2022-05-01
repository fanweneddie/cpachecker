/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import org.sosy_lab.cpachecker.cfa.ast.AAssignment;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.AInitializer;
import org.sosy_lab.cpachecker.cfa.ast.AInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.ARightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.AStatement;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBooleanLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.java.JMethodInvocationAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.java.JReferencedMethodInvocationExpression;
import org.sosy_lab.cpachecker.cfa.model.ADeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.AReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.string.StringRelationAnalysisState.StringRelationLabel;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public class StringRelationAnalysisTransferRelation
    extends ForwardingTransferRelation<StringRelationAnalysisState, StringRelationAnalysisState, VariableTrackingPrecision> {

  public StringRelationAnalysisTransferRelation(
      StringRelationAnalysisState pState, VariableTrackingPrecision pPrecision, String pFunctionName) {
    super();
    state = pState;
    precision = pPrecision;
    functionName = pFunctionName;
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

  /**
   * Handle an assumption edge.
   * We only consider the JBinaryExpression consisting of a function result and a boolean value.
   * @param cfaEdge the given assumption edge to handle
   * @param expression the expression on <code>cfaEdge</code>
   * @param truthValue the assumption result of <code>expression</code>
   * @return the new abstract state after this edge
   */
  @Override
  protected StringRelationAnalysisState handleAssumption(
      AssumeEdge cfaEdge, AExpression expression, boolean truthValue) {

    if (!IsBinaryExpressionOfFunctionResultAndBoolean(expression)) {
      return state;
    }

    JBinaryExpression binaryExpression = (JBinaryExpression) expression;
    JExpression operand1 = binaryExpression.getOperand1();
    JExpression operand2 = binaryExpression.getOperand2();

    JReferencedMethodInvocationExpression invocation;
    boolean boolValue;

    if (AreFunctionReturnValueAndBooleanValue(operand1, operand2)) {
      invocation = getFunctionInvocation(operand1);
      boolValue = ((JBooleanLiteralExpression) operand2).getBoolean();
    } else {
      invocation = getFunctionInvocation(operand2);
      boolValue = ((JBooleanLiteralExpression) operand1).getBoolean();
    }

    if (TypeChecker.isStringEquals(invocation)) {
      return handleStringEquals(invocation, !(boolValue^truthValue));
    }
    // todo: startWith and endWith

    return state;
  }

  /**
   * Handle the assumption of method equals of String.
   * If the assumption is true, then the comparison is between two variables,
   * then we add an EQUAL relation between those two variables.
   * @param invocation the invocation to method equals in assumption
   * @param truthValue the boolean value of the assumption
   * @return the new abstract state after this assumption
   */
  private StringRelationAnalysisState handleStringEquals(JReferencedMethodInvocationExpression invocation,
                                                         boolean truthValue) {
    if (!truthValue) {
      return state;
    }

    JExpression parameter = invocation.getParameterExpressions().get(0);
    if (!(parameter instanceof JIdExpression)) {
      return state;
    }

    JIdExpression string1 = invocation.getReferencedVariable();
    JIdExpression string2 = (JIdExpression) parameter;

    MemoryLocation stringVar1 = StringVariableGenerator.getExpressionMemLocation(string1, functionName);
    MemoryLocation stringVar2 = StringVariableGenerator.getExpressionMemLocation(string2, functionName);

    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);
    newState.addRelation(stringVar1, stringVar2, StringRelationLabel.EQUAL);
    newState.addRelation(stringVar2, stringVar1, StringRelationLabel.EQUAL);

    return newState;
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
        !TypeChecker.isJavaGenericStringType(declaration.getType()) ||
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
      AExpression exp = ((AInitializerExpression) init).getExpression();
      MemoryLocation RHSVariable = StringVariableGenerator.getExpressionMemLocation(exp, functionName);
      if (RHSVariable != null) {
        newState.addRelation(LHSVariable, RHSVariable, StringRelationLabel.EQUAL);
        newState.addRelation(RHSVariable, LHSVariable, StringRelationLabel.EQUAL);
        newState.copyInvocation(RHSVariable, LHSVariable);
      }
    }

    return newState;
  }

  /**
   * Handle a statement edge, which can be assignment statement or function call statement.
   * We handle those cases accordingly.
   * @param cfaEdge the given statement edge
   * @param expression the expression of the statement
   * @return the new state after <code>cfaEdge</code>
   * @throws UnrecognizedCodeException
   */
  @Override
  protected StringRelationAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {

    if (expression instanceof AAssignment) {
      AAssignment assignExpression = (AAssignment) expression;
      return handleAssignmentStatement(assignExpression);
    }

    if (expression instanceof AFunctionCallStatement) {
      AFunctionCallStatement functionCall = (AFunctionCallStatement) expression;
      return handleFunctionCallStatement(functionCall);
    }

    return state;
  }

  /**
   * Handle an assignment statement, which can be with invocation or expression.
   * We handle those cases accordingly.
   * @param assignExpression the given assignment expression
   * @return the new state after <code>assignExpression</code>
   */
  private StringRelationAnalysisState handleAssignmentStatement(AAssignment assignExpression) {

    if (assignExpression instanceof JMethodInvocationAssignmentStatement) {
      JMethodInvocationAssignmentStatement invocationAssignment =
          (JMethodInvocationAssignmentStatement) assignExpression;
      return handleInvocationAssignmentStatement(invocationAssignment);
    }

    if (assignExpression instanceof JExpressionAssignmentStatement) {
      JExpressionAssignmentStatement expressionAssignment =
          (JExpressionAssignmentStatement) assignExpression;
      return handleExpressionAssignmentStatement(expressionAssignment);
    }

    return state;
  }

  /**
   * Handle an invocation assignment.
   * We set the mapping of its caller object and invocation expression.
   * @param invocationAssignment the given invocation assignment statement
   * @return the new state after <code>invocationAssignment</code>
   */
  private StringRelationAnalysisState handleInvocationAssignmentStatement(
                          JMethodInvocationAssignmentStatement invocationAssignment) {

    AExpression op1 = invocationAssignment.getLeftHandSide();
    ARightHandSide op2 = invocationAssignment.getRightHandSide();

    if (!(op1 instanceof JIdExpression) ||
        !(op2 instanceof JReferencedMethodInvocationExpression)) {
      return state;
    }

    MemoryLocation LHSVariable = StringVariableGenerator.getExpressionMemLocation(op1, functionName);
    JReferencedMethodInvocationExpression invocation = (JReferencedMethodInvocationExpression) op2;

    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);
    newState.setInvocation(LHSVariable, invocation);

    if (TypeChecker.isStringConcat(invocation)) {
      return handleStringConcat(LHSVariable, invocation, newState);
    }

    return newState;
  }

  /**
   * Handle a string concat invocation.
   * We need to add the concat relation among those strings.
   * @param returnValue the return value of the invocation
   * @param invocation the given invocation
   * @param curState the current abstract state
   * @return the new abstract state after <code>invocation</code>
   */
  private StringRelationAnalysisState handleStringConcat(MemoryLocation returnValue,
                                                         JReferencedMethodInvocationExpression invocation,
                                                         StringRelationAnalysisState curState) {

    JIdExpression caller = invocation.getReferencedVariable();
    JExpression param = invocation.getParameterExpressions().get(0);
    MemoryLocation callerVariable = StringVariableGenerator.getExpressionMemLocation(caller, functionName);
    MemoryLocation paramVariable = StringVariableGenerator.getExpressionMemLocation(param, functionName);

    if (callerVariable == null || paramVariable == null) {
      return curState;
    }

    // kill the original relation with LHSVariable
    curState.killVariableRelation(returnValue);

    // add the new relation as callerVariable.concat(paramVariable) = LHSVariable
    curState.addRelation(callerVariable, returnValue, StringRelationLabel.CONCAT_1);
    curState.addRelation(paramVariable, returnValue, StringRelationLabel.CONCAT_2);

    return curState;
  }

  /**
   * Handle an expression assignment statement.
   * If the variables are string,
   * we just first kill the relation between that LHS variable and other variables,
   * and then add equation relation between LHS variable and RHS variable
   * (Similar to {@link #handleDeclarationEdge}).
   * @param expressionAssignment the given expression assignment statement
   * @return the new state after <code>expressionAssignment</code>
   */
  private StringRelationAnalysisState handleExpressionAssignmentStatement(
      JExpressionAssignmentStatement expressionAssignment) {

    JLeftHandSide LHS = expressionAssignment.getLeftHandSide();
    if (!(LHS instanceof JIdExpression)) {
      return state;
    }

    JIdExpression LHSExpression = (JIdExpression) LHS;
    if (!TypeChecker.isJavaGenericStringType(LHSExpression.getExpressionType())) {
      return state;
    }

    JExpression RHS = expressionAssignment.getRightHandSide();
    MemoryLocation LHSVariable = StringVariableGenerator.getExpressionMemLocation(LHSExpression, functionName);
    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);

    // kill the original relation with LHSVariable
    newState.killVariableRelation(LHSVariable);

    // if RHS is a variable, add the equation relation between LHSVariable and RHSVariable
    if (RHS instanceof JIdExpression) {
      JIdExpression RHSExpression = (JIdExpression) RHS;
      MemoryLocation RHSVariable = StringVariableGenerator.getExpressionMemLocation(RHSExpression, functionName);
      if (RHSVariable != null) {
        newState.addRelation(LHSVariable, RHSVariable, StringRelationLabel.EQUAL);
        newState.addRelation(RHSVariable, LHSVariable, StringRelationLabel.EQUAL);
        newState.copyInvocation(RHSVariable, LHSVariable);
      }
    }

    return newState;
  }

  /**
   * handle a function call statement.
   * We currently just consider method reverse() of StringBuilder.
   * @param functionCall the given function call
   * @return the new state after <code>functionCall</code>
   */
  private StringRelationAnalysisState handleFunctionCallStatement(AFunctionCallStatement functionCall) {

    AFunctionCallExpression functionCallExpression = functionCall.getFunctionCallExpression();
    if (!(functionCallExpression instanceof JReferencedMethodInvocationExpression)) {
      return state;
    }

    JReferencedMethodInvocationExpression invocation = (JReferencedMethodInvocationExpression) functionCallExpression;
    if (TypeChecker.isStringReverse(invocation)) {
      return handleStringReverse(invocation);
    }

    return state;
  }

  /**
   * handle reverse() method of StringBuilder.
   * @param invocation the invocation of reverse() method
   * @return the new state after <code>invocation</code>
   */
  private StringRelationAnalysisState handleStringReverse(JReferencedMethodInvocationExpression invocation) {

    JIdExpression callerObject = invocation.getReferencedVariable();
    MemoryLocation callerVariable = StringVariableGenerator.getExpressionMemLocation(callerObject, functionName);

    if (callerVariable == null) {
      return state;
    }

    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);
    newState.makeReverse(callerVariable);

    return newState;
  }

  /**
   * Check whether the given expression is a JBinaryExpression
   * consisting of a function result and a boolean value.
   */
  private boolean IsBinaryExpressionOfFunctionResultAndBoolean(AExpression expression) {

    if (!(expression instanceof JBinaryExpression)) {
      return false;
    }

    JBinaryExpression binaryExpression = (JBinaryExpression) expression;
    JExpression operand1 = binaryExpression.getOperand1();
    JExpression operand2 = binaryExpression.getOperand2();

    return AreFunctionReturnValueAndBooleanValue(operand1, operand2) ||
          AreFunctionReturnValueAndBooleanValue(operand2, operand1);
  }

  /**
   * Check whether the first operand is a function return value
   * and the second operand is a boolean value.
   */
  private boolean AreFunctionReturnValueAndBooleanValue(JExpression operand1, JExpression operand2) {
    return getFunctionInvocation(operand1) != null &&
          operand2 instanceof JBooleanLiteralExpression;
  }

  /**
   * Get the Invocation of an operand, if it is the return value of a function call.
   */
  private JReferencedMethodInvocationExpression getFunctionInvocation(JExpression operand) {
    if (!(operand instanceof JIdExpression)) {
      return null;
    }

    MemoryLocation variable = StringVariableGenerator.getExpressionMemLocation(operand, functionName);
    JReferencedMethodInvocationExpression invocation = state.getInvocation(variable);

    return invocation;
  }
}
