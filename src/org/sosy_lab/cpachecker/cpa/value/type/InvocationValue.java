/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.value.type;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.ast.java.JReferencedMethodInvocationExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

/**
 * The wrapper of a method invocation.
 */
public class InvocationValue implements Value {

  private JReferencedMethodInvocationExpression invocation;

  public InvocationValue() {
    invocation = null;
  }

  public InvocationValue(JReferencedMethodInvocationExpression pInvocation) {
    invocation = pInvocation;
  }

  public JReferencedMethodInvocationExpression getInvocation() {
    return invocation;
  }

  @Override
  public boolean isNumericValue() {
    return false;
  }

  @Override
  public boolean isUnknown() {
    return invocation == null;
  }

  @Override
  public boolean isExplicitlyKnown() {
    return invocation != null;
  }

  @Override
  public @Nullable NumericValue asNumericValue() {
    return null;
  }

  @Override
  public @Nullable Long asLong(CType type) {
    return null;
  }

  @Override
  public <T> T accept(ValueVisitor<T> pVisitor) {
    return null;
  }

  @Override
  public String toString() {
    String str = "[invocation: ";
    if (isExplicitlyKnown()) {
      str += invocation.toString();
    }
    str += "]";
    return str;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof InvocationValue)) {
      return false;
    }

    InvocationValue otherInvocationValue = (InvocationValue) other;
    return Objects.equals(invocation, otherInvocationValue.invocation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(invocation);
  }
}
