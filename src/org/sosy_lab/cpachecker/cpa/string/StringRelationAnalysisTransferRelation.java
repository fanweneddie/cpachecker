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
import org.sosy_lab.cpachecker.cfa.ast.AAssignment;
import org.sosy_lab.cpachecker.cfa.ast.ADeclaration;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
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
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.java.JClassType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.string.StringRelationAnalysisState.StringRelationLabel;
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
      invocation = getFunctionReturnValue(operand1);
      boolValue = ((JBooleanLiteralExpression) operand2).getBoolean();
    } else {
      invocation = getFunctionReturnValue(operand2);
      boolValue = ((JBooleanLiteralExpression) operand1).getBoolean();
    }

    if (isStringEquals(invocation)) {
      return handleStringEquals(invocation, !(boolValue^truthValue));
    }

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

    MemoryLocationVisitor mlv = getVisitor();
    JIdExpression string1 = invocation.getReferencedVariable();
    JIdExpression string2 = (JIdExpression) parameter;

    MemoryLocation stringVar1 = getExpressionMemLocation(string1, mlv);
    MemoryLocation stringVar2 = getExpressionMemLocation(string2, mlv);

    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);
    newState.addRelation(stringVar1, stringVar2, StringRelationLabel.EQUAL);

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
        newState.copyInvocation(RHSVariable, LHSVariable);
      }
    }

    return newState;
  }

  /**
   * Handle a statement edge, which can be invocation assignment or expression assignment.
   * We handle those cases accordingly.
   * @param cfaEdge the given statement edge
   * @param expression the expression of the statement
   * @return the new state after <code>cfaEdge</code>
   * @throws UnrecognizedCodeException
   */
  @Override
  protected StringRelationAnalysisState handleStatementEdge(AStatementEdge cfaEdge, AStatement expression)
      throws UnrecognizedCodeException {

    if (!(expression instanceof AAssignment)) {
      return state;
    }

    AAssignment assignExpression = (AAssignment) expression;

    if (assignExpression instanceof JMethodInvocationAssignmentStatement) {
      JMethodInvocationAssignmentStatement invocationAssignment =
                    (JMethodInvocationAssignmentStatement) assignExpression;
      return handleInvocationAssignmentStatement(invocationAssignment);
    } else if (assignExpression instanceof JExpressionAssignmentStatement) {
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

    MemoryLocationVisitor mlv = getVisitor();
    MemoryLocation LHSVariable = getExpressionMemLocation(op1, mlv);
    JReferencedMethodInvocationExpression invocation = (JReferencedMethodInvocationExpression) op2;

    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);
    newState.setInvocation(LHSVariable, invocation);

    if (isStringConcat(invocation)) {
      return handleStringConcat(LHSVariable, invocation, newState);
    } // else reverse

    return newState;
  }

  private StringRelationAnalysisState handleStringConcat(MemoryLocation LHSVariable,
                                                         JReferencedMethodInvocationExpression invocation,
                                                         StringRelationAnalysisState curState) {
    JIdExpression callerObject = invocation.getReferencedVariable();
    JExpression param = invocation.getParameterExpressions().get(0);
    MemoryLocationVisitor mlv = getVisitor();
    // Todo: consider constant
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
    if (!isJavaGenericStringType(LHSExpression.getExpressionType())) {
      return state;
    }

    MemoryLocationVisitor mlv = getVisitor();
    JExpression RHS = expressionAssignment.getRightHandSide();
    MemoryLocation LHSVariable = getExpressionMemLocation(LHSExpression, mlv);
    StringRelationAnalysisState newState = StringRelationAnalysisState.deepCopyOf(state);

    // kill the original relation with LHSVariable
    newState.killVariableRelation(LHSVariable);

    // if RHS is a variable, add the equation relation between LHSVariable and RHSVariable
    if (RHS instanceof JIdExpression) {
      JIdExpression RHSExpression = (JIdExpression) RHS;
      MemoryLocation RHSVariable = getExpressionMemLocation(RHSExpression, mlv);
      if (RHSVariable != null) {
        newState.addRelation(LHSVariable, RHSVariable, StringRelationLabel.EQUAL);
        newState.copyInvocation(RHSVariable, LHSVariable);
      }
    }

    return newState;
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

  private static MemoryLocation getExpressionMemLocation(AExpression expression,
                                                  MemoryLocationVisitor mlv) {
    if (expression instanceof JIdExpression) {
      JIdExpression IdExpression = (JIdExpression) expression;
      return IdExpression.accept(mlv);
    } else {
      return null;
    }
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
    return getFunctionReturnValue(operand1) != null &&
          operand2 instanceof JBooleanLiteralExpression;
  }

  /**
   * Get the Invocation of an operand, if it is the return value of a function call.
   */
  private JReferencedMethodInvocationExpression getFunctionReturnValue(JExpression operand) {
    if (!(operand instanceof JIdExpression)) {
      return null;
    }

    MemoryLocationVisitor mlv = getVisitor();
    MemoryLocation variable = getExpressionMemLocation(operand, mlv);
    JReferencedMethodInvocationExpression invocation = state.getInvocation(variable);

    return invocation;
  }

  /**
   * Check whether the given method is equals() of String.
   */
  private static boolean isStringEquals(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
          functionNameExpression.toString().equals("equals");
  }

  /**
   * Check whether the given method is concat() of String.
   */
  private static boolean isStringConcat(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
          params.size() == 1 &&
          functionNameExpression.toString().equals("concat");
  }
}
