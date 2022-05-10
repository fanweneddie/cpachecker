/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cfa.types.java;

/**
 * The type of java String or StringBuilder.
 */
public class JStringType implements JType {
  public JStringType() {
  }

  @Override
  public String toASTString(String declarator) {
    return "''";
  }
}
