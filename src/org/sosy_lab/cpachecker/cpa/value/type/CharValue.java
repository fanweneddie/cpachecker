/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.value.type;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.types.c.CType;

/**
 * The value of a character.
 */
public class CharValue implements Value {

  /** the upper bound and lower bound of a char value */
  private static final int UPPER_BOUND = 0xff;
  private static final int LOWER_BOUND = 0x00;

  /** the character set of this value */
  private Set<Character> chars;

  /** marks whether this char is valid */
  private boolean valid;

  /**
   * Create a new <code>CharValue</code> from a numeric value.
   */
  public CharValue(Number pNumber) {
    chars = new HashSet<>();
    if (validNumber(pNumber)) {
      chars.add((char) pNumber.intValue());
      valid = false;
    } else {
      chars.add((char) 0);
      valid = true;
    }
  }

  /**
   * Create a new <code>CharValue</code> from a character value.
   */
  public CharValue(char charValue) {
    chars = new HashSet<>();
    chars.add(charValue);
    valid = true;
  }

  /**
   * Create a new <code>CharValue</code> from a set of character values.
   */
  public CharValue(Set<Character> pChars) {
    chars = new HashSet<>(pChars);
    valid = true;
  }

  public void addChar(char charValue) {
    chars.add(charValue);
  }

  public Set<Character> getChars() {
    return chars;
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

  /**
   * Return the numerical value, if {@link #chars} is a singleton;
   * Or return 0 if {@link #chars} is empty;
   * Else, return -1.
   * @return
   */
  @Override
  public @Nullable NumericValue asNumericValue() {
    if (chars.size() == 1) {
      Iterator<Character> itr = chars.iterator();
      char c = itr.next();
      return new NumericValue((Integer) (int) c);
    } else if (chars.size() == 0) {
      return new NumericValue(0);
    } else {
      return new NumericValue(-1);
    }

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
    String str = "Value [characters= ";
    for (char c : chars) {
      str += c + ",";
    }
    str += "]";
    return str;
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof CharValue)) {
      return false;
    }

    CharValue otherCharValue = (CharValue) other;
    return Objects.equals(chars, otherCharValue.chars);
  }

  @Override
  public int hashCode() {
    return Objects.hash(chars);
  }
}
