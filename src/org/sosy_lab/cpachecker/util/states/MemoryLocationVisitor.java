/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/
package org.sosy_lab.cpachecker.util.states;

import org.sosy_lab.cpachecker.cfa.ast.java.JArrayCreationExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JArrayInitializer;
import org.sosy_lab.cpachecker.cfa.ast.java.JArrayLengthExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JBooleanLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JClassInstanceCreation;
import org.sosy_lab.cpachecker.cfa.ast.java.JClassLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JEnumConstantExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JMethodInvocationExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JNullLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JRightHandSideVisitor;
import org.sosy_lab.cpachecker.cfa.ast.java.JRunTimeTypeEqualsType;
import org.sosy_lab.cpachecker.cfa.ast.java.JStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JThisExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JVariableRunTimeType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.exceptions.NoException;

/**
 * This Visitor returns the variable memoryLocation from a simple expression.
 * The result may be null, i.e., the expression is a constant.
 */
public class MemoryLocationVisitor
    implements JRightHandSideVisitor<MemoryLocation, NoException> {

  private final String functionName;

  public MemoryLocationVisitor(String pFunctionName) {
    functionName = pFunctionName;
  }

  @Override
  public MemoryLocation visit(JCharLiteralExpression paCharLiteralExpression) throws NoException {
    return null;
  }

  /**
   * Return a temporary variable for a string constant.
   * The name of a string constant is "-" + its value.
   */
  @Override
  public MemoryLocation visit(JStringLiteralExpression paStringLiteralExpression)
      throws NoException {
    String value = "-" + paStringLiteralExpression.getValue();
    MemoryLocation memLoc = MemoryLocation.forLocalVariable(functionName, value);
    return memLoc;
  }

  @Override
  public MemoryLocation visit(JBinaryExpression paBinaryExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JUnaryExpression pAUnaryExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JIntegerLiteralExpression pJIntegerLiteralExpression)
      throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JBooleanLiteralExpression pJBooleanLiteralExpression)
      throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JFloatLiteralExpression pJFloatLiteralExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JArrayCreationExpression pJArrayCreationExpression)
      throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JArrayInitializer pJArrayInitializer) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JArrayLengthExpression pJArrayLengthExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JVariableRunTimeType pJThisRunTimeType) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JRunTimeTypeEqualsType pJRunTimeTypeEqualsType) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JNullLiteralExpression pJNullLiteralExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JEnumConstantExpression pJEnumConstantExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JCastExpression pJCastExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JThisExpression pThisExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JClassLiteralExpression pJClassLiteralExpression) throws NoException {
    return null;
  }

  @Override
  public MemoryLocation visit(JArraySubscriptExpression pAArraySubscriptExpression)
      throws NoException {
    return null;
  }

  /**
   * Return the memoryLocation of variable in the given JIdExpression.
   * @param pJIdExpression the given JidExpression, which must not be null
   * @return the memoryLocation of variable of <code>pJIdExpression</code>
   * @throws NoException
   */
  @Override
  public MemoryLocation visit(JIdExpression pJIdExpression) throws NoException {
    MemoryLocation memLoc;

    if (pJIdExpression.getDeclaration() != null) {
      memLoc = MemoryLocation.forDeclaration(pJIdExpression.getDeclaration());
    } else if (!ForwardingTransferRelation.isGlobal(pJIdExpression)) {
      memLoc = MemoryLocation.forLocalVariable(functionName, pJIdExpression.getName());
    } else {
      memLoc = MemoryLocation.forIdentifier(pJIdExpression.getName());
    }

    return memLoc;
  }

  @Override
  public MemoryLocation visit(JMethodInvocationExpression pAFunctionCallExpression)
      throws NoException {
    // Todo
    return null;
  }

  @Override
  public MemoryLocation visit(JClassInstanceCreation pJClassInstanceCreation) throws NoException {
    return null;
  }
}
