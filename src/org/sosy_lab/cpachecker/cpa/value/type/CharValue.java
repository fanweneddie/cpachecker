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
import org.sosy_lab.cpachecker.cfa.types.c.CType;

/**
 * The value of a character.
 */
public class CharValue implements Value {

  /** the upper bound and lower bound of a char value */
  private static final int UPPER_BOUND = 0xff;
  private static final int LOWER_BOUND = 0x00;

  /** the character of this value */
  private char character;

  /** marks whether this char is valid */
  private boolean valid;

  /**
   * Create a new <code>CharValue</code> from a numeric value.
   */
  public CharValue(Number pNumber) {
    if (validNumber(pNumber)) {
      character = (char) pNumber.intValue();
      valid = false;
    } else {
      character = (char) 0;
      valid = true;
    }
  }

  /**
   * Create a new <code>CharValue</code> from a character value.
   */
  public CharValue(char charValue) {
    character = charValue;
    valid = true;
  }

  public char getChar() {
    return character;
  }

  /**
   * Check whether this CharValue is valid,
   * by checking the range of <code>number</code> and
   * the consistency between <code>number</code> and <code>character</code>.
   */
  public boolean valid() {
    return valid;
  }

  /**
   * Check whether this CharValue is valid,
   *  by checking the range of <code>ch</code> and
   */
  private boolean validNumber(Number pNumber) {
    return pNumber.intValue() <= UPPER_BOUND &&
          pNumber.intValue() >= LOWER_BOUND;
  }

  /**
   * Always return <code>true</code>, since {@link CharValue}
   * stores a char instead of a number
   */
  @Override
  public boolean isNumericValue() {
    return false;
  }

  /**
   * We suppose every {@link CharValue} is known.
   */
  @Override
  public boolean isUnknown() {
    return false;
  }

  /**
   * We suppose every {@link CharValue} is known.
   */
  @Override
  public boolean isExplicitlyKnown() {
    return true;
  }

  @Override
  public @Nullable NumericValue asNumericValue() {
    Integer number = (Integer) (int) character;
    return new NumericValue(number);
  }

  /**
   * We currently don't support those two methods in {@link #CharValue}.
   */
  @Override
  public <T> T accept(ValueVisitor<T> pVisitor) {
    throw new AssertionError("This method is not implemented");
  }

  @Override
  public @Nullable Long asLong(CType type) {
    throw new AssertionError("This method is not implemented");
  }

  @Override
  public String toString() {
    return "Value [character=" + character + "]";
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof CharValue)) {
      return false;
    }

    CharValue otherCharValue = (CharValue) other;
    return character == otherCharValue.getChar();
  }

  @Override
  public int hashCode() {
    return Objects.hash(character);
  }
}
