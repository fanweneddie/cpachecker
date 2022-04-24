/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.value.type;

import static org.junit.Assert.assertNotNull;

import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.automaton4string.Automaton;
import org.sosy_lab.cpachecker.util.automaton4string.BasicAutomata;

/**
 * This class represents a string value.
 * It stores the possible value of a string variable.
 */
public class StringValue implements Value {

  /** The domain of string value */
  private final Automaton valueDomain;

  /** Whether the string value is unknown */
  public final boolean isUnknown;

  /**
   * The factory that creates the value of a given string.
   * @param pValue the given string
   * @return the value of <code>pValue</code> if it is not null; Or return the value for an unknown string
   */
  public static StringValue newStringValue(String pValue) {
    if (pValue != null) {
      return new StringValue(BasicAutomata.makeString(pValue), false);
    } else {
      return new StringValue();
    }
  }

  /**
   * Construct the value for an unknown string.
   */
  private StringValue() {
    this.valueDomain = BasicAutomata.makeAnyString();
    this.isUnknown = true;
  }

  /**
   * Construct the value for a known concrete string.
   */
  private StringValue(Automaton pValueDomain, boolean pIsUnknown) {
    this.valueDomain = pValueDomain;
    this.isUnknown = pIsUnknown;
  }

  public Automaton getValueDomain() {
    return valueDomain;
  }

  /**
   * Reverse a given string value, by doing reverse operation on its {@link #valueDomain}.
   * @param pStringValue the given string value, which must not be null
   * @return the result of the reverse of <code>pStringValue</code>
   */
  public static StringValue reverse(StringValue pStringValue) {
    assertNotNull(pStringValue);

    return null;
  }

  /**
   * Concatenate two given string values, by doing concat operation on their {@link #valueDomain}.
   * @param pStringValue1 the first given string value, which must not be null
   * @param pStringValue2 the second given string value, which must not be null
   * @return the result of the concatenation of <code>pStringValue1</code> and <code>pStringValue2</code>
   */
  public static StringValue concat(StringValue pStringValue1, StringValue pStringValue2) {
    assertNotNull(pStringValue1);
    assertNotNull(pStringValue2);

    return null;
  }

  /**
   * Always return <code>false</code>, since {@link StringValue}
   * always stores a string, not a number.
   * @return always <code>false</code>
   */
  @Override
  public boolean isNumericValue() {
    return false;
  }

  /**
   * Check whether this string is unknown.
   */
  @Override
  public boolean isUnknown() {
    return isUnknown;
  }

  /**
   * Check whether this string is known.
   */
  @Override
  public boolean isExplicitlyKnown() {
    return !isUnknown;
  }

  /**
   * We don't get the numeric representation of a string,
   * so it always throws an <code>AssertionError</code>.
   */
  @Deprecated
  @Override
  public @Nullable NumericValue asNumericValue() {
    throw new AssertionError("This method is not implemented");
  }

  /**
   * We don't get the Long representation of the string.
   * so it always throws an <code>AssertionError</code>.
   */
  @Deprecated
  @Override
  public @Nullable Long asLong(CType type) {
    throw new AssertionError("This method is not implemented");
  }

  /**
   * We currently don't support visitor for {@link #StringValue}
   */
  @Override
  public <T> T accept(ValueVisitor<T> pVisitor) {
    throw new AssertionError("This method is not implemented");
  }

  @Override
  public String toString() {
    if (isUnknown) {
      return "Value [unknown string]";
    } else {
      return "Value [string=" + valueDomain.toString() + "]";
    }
  }

  /**
   * Two instances of {@link StringValue} are equal if their value domain
   * describes the same language.
   */
  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof StringValue)) {
      return false;
    }
    StringValue otherStringValue = (StringValue) other;
    return isUnknown == otherStringValue.isUnknown &&
        Objects.equals(valueDomain, otherStringValue.valueDomain);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valueDomain, isUnknown);
  }
}
