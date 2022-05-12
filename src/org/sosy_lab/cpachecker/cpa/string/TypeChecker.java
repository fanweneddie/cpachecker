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
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.java.JClassInstanceCreation;
import org.sosy_lab.cpachecker.cfa.ast.java.JConstructorDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.java.JExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JReferencedMethodInvocationExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JUnaryExpression.UnaryOperator;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.Type;
import org.sosy_lab.cpachecker.cfa.types.java.JClassType;
import org.sosy_lab.cpachecker.cfa.types.java.JConstructorType;

/**
 * This class provides static methods to check a type or a method type.
 */
public class TypeChecker {

  /**
   * Check whether a given Java type is a generic java string type (e.g. String, StringBuilder).
   * @param type the given Java type
   * @return true if <code>type</code> is not null and is a generic string type
   */
  public static final boolean isJavaGenericStringType(Type type) {
    return isJavaStringType(type) || isJavaStringBuilderType(type);
  }

  /**
   * Check whether a given Java type is Java String type.
   * @param type the given Java type
   * @return true if <code>type</code> is not null and is a String type
   */
  public static final boolean isJavaStringType(Type type) {
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

  /**
   * Check whether a given Java type is Java StringBuilder type.
   * @param type the given Java type
   * @return true if <code>type</code> is not null and is a StringBuilder type
   */
  public static final boolean isJavaStringBuilderType(Type type) {
    if (!(type instanceof JClassType)) {
      return false;
    }

    JClassType classType = (JClassType) type;
    if (classType.getName().equals("java.lang.StringBuilder")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Check whether the given operator is "==".
   */
  public static boolean isEqualOperator(JBinaryExpression.BinaryOperator operator) {
    return operator == BinaryOperator.EQUALS;
  }

  /**
   * Check whether the given operator is "!".
   */
  public static boolean isNOTOperator(JUnaryExpression.UnaryOperator operator) {
    return operator == UnaryOperator.NOT;
  }

  /**
   * Check whether the given AssumeEdge is in assertion statement.
   * @param cfaEdge the given assumption edge
   * @return true if <code>cfaEdge</code> is in assertion statement,
   *        false if <code>cfaEdge</code> is in a condition statement
   */
  public static boolean isAssertion(AssumeEdge cfaEdge) {
    // for an assertion statement, its successor statement must be "assert success" or "assert fail".
    int succEdgeNum = cfaEdge.getSuccessor().getNumLeavingEdges();
    for (int i = 0; i < succEdgeNum; ++i) {
      CFAEdge succEdge = cfaEdge.getSuccessor().getLeavingEdge(i);
      if ((succEdge instanceof BlankEdge) && succEdge.getDescription().startsWith("assert")) {
        return true;
      }
    }

    return false;
  }

  /**
   * Check whether the given method is nondetString() of Verifier
   */
  public static boolean isNonDetString(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression) ||
        !(invocation.getReferencedVariable() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression qualifier = (JIdExpression) invocation.getReferencedVariable();

    return qualifier.toString().equals("Verifier") &&
        functionNameExpression.toString().equals("nondetString");
  }

  /**
   * Check whether the given method is equals() of String.
   */
  public static boolean isStringEquals(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
        functionNameExpression.toString().equals("equals");
  }

  /**
   * Check whether the given method is concat() of String or append() of StringBuilder.
   */
  public static boolean isStringConcat(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    List<JExpression> params = invocation.getParameterExpressions();

    return (params.size() == 1 || params.size() == 3) &&
        (functionNameExpression.toString().equals("concat") || functionNameExpression.toString().equals("append"));
  }

  public static boolean isToString(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    List<JExpression> params = invocation.getParameterExpressions();

    return params.size() == 0 &&
        (functionNameExpression.toString().equals("toString"));
  }

  /**
   * Check whether the given method is reverse() of String.
   */
  public static boolean isStringReverse(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaStringBuilderType(callerObject.getExpressionType()) &&
        params.size() == 0 &&
        functionNameExpression.toString().equals("reverse");
  }

  /**
   * Check whether the given method is the initialization of StringBuilder.
   */
  public static boolean isNewStringBuilder(JClassInstanceCreation initialization) {
    if (initialization == null) {
      return false;
    }

    JConstructorDeclaration declaration = initialization.getDeclaration();
    JConstructorType type = declaration.getType();

    if (type == null || type.getReturnType() == null) {
      return false;
    }

    return type.getReturnType().getName().equals("java.lang.StringBuilder");
  }

  /**
   * Check whether the given method is length() of String.
   */
  public static boolean isLength(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
        params.size() == 0 &&
        functionNameExpression.toString().equals("length");
  }

  /**
   * Check whether the given method is setLength() of String.
   */
  public static boolean isSetLength(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
        params.size() == 1 &&
        functionNameExpression.toString().equals("setLength");
  }

  /**
   * Check whether the given method is substring() of String.
   */
  public static boolean isSubstring(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
        (params.size() == 1 || params.size() == 2) &&
        functionNameExpression.toString().equals("substring");
  }

  /**
   * Check whether the given method is charAt() of String.
   */
  public static boolean isCharAt(JReferencedMethodInvocationExpression invocation) {
    if (!(invocation.getFunctionNameExpression() instanceof JIdExpression)) {
      return false;
    }

    JIdExpression functionNameExpression = (JIdExpression) invocation.getFunctionNameExpression();
    JIdExpression callerObject = invocation.getReferencedVariable();
    List<JExpression> params = invocation.getParameterExpressions();

    return isJavaGenericStringType(callerObject.getExpressionType()) &&
        params.size() == 1 &&
        functionNameExpression.toString().equals("charAt");
  }
}
