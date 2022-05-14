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

import java.util.Arrays;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.automaton4string.Automaton;
import org.sosy_lab.cpachecker.util.automaton4string.BasicAutomata;
import org.sosy_lab.cpachecker.util.automaton4string.SpecialOperations;

/**
 * This class represents a string value.
 * It stores the possible value of a string variable.
 */
public class StringValue implements Value {

  /** The domain of string value */
  private final Automaton valueDomain;

  /**
   * The factory that creates the value of a given string.
   * @param pValue the given string
   * @return the value of <code>pValue</code> if it is not null; Or return the value for an unknown string
   */
  public static StringValue newStringValue(String pValue) {
    if (pValue != null) {
      return new StringValue(BasicAutomata.makeString(pValue));
    } else {
      return new StringValue();
    }
  }

  /**
   * Construct the value for an unknown string.
   */
  private StringValue() {
    this.valueDomain = BasicAutomata.makeAnyString();
  }

  /**
   * Construct the value for a known concrete string.
   */
  public StringValue(Automaton pValueDomain) {
    this.valueDomain = pValueDomain;
  }

  public Automaton getValueDomain() {
    return valueDomain;
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
    assertNotNull(pStringValue1.valueDomain);
    assertNotNull(pStringValue2.valueDomain);

    Automaton newValueDomain = Automaton.concatenate(Arrays.asList(pStringValue1.valueDomain,
                                                                    pStringValue2.valueDomain));
    return new StringValue(newValueDomain);
  }

  public static StringValue reverse(StringValue pStringValue) {
    assertNotNull(pStringValue);
    assertNotNull(pStringValue.valueDomain);

    Automaton newDomain = pStringValue.valueDomain.clone();
    newDomain.reverse();
    return new StringValue(newDomain);
  }

  public static StringValue suffix(StringValue pStringValue) {
    assertNotNull(pStringValue);
    assertNotNull(pStringValue.valueDomain);

    Automaton newDomain = Automaton.getSuffix(pStringValue.valueDomain);
    return new StringValue(newDomain);
  }

  public static StringValue prefix(StringValue pStringValue) {
    assertNotNull(pStringValue);
    assertNotNull(pStringValue.valueDomain);

    Automaton newDomain = Automaton.getPrefix(pStringValue.valueDomain);
    return new StringValue(newDomain);
  }

  public static StringValue extendAtBack(StringValue pStringValue) {
    assertNotNull(pStringValue);
    assertNotNull(pStringValue.valueDomain);

    Automaton newDomain = Automaton.getExtendAtBack(pStringValue.valueDomain);
    return new StringValue(newDomain);
  }

  public static StringValue extendAtFront(StringValue pStringValue) {
    assertNotNull(pStringValue);
    assertNotNull(pStringValue.valueDomain);

    Automaton newDomain = Automaton.getExtendAtFront(pStringValue.valueDomain);
    return new StringValue(newDomain);
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
   * We don't set all string values as known
   */
  @Override
  public boolean isUnknown() {
    return false;
  }

  /**
   * Check whether this string is known.
   */
  @Override
  public boolean isExplicitlyKnown() {
    return true;
  }

  /**
   * We don't get the numeric representation of a string,
   * so it always throws an <code>AssertionError</code>.
   */
  @Deprecated
  @Override
  public @Nullable NumericValue asNumericValue() {
    return new NumericValue(0);
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
      return "Value [string=" + valueDomain.toString() + "]";
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
    return Objects.equals(valueDomain, otherStringValue.valueDomain);
  }

  @Override
  public int hashCode() {
    return Objects.hash(valueDomain);
  }
}
