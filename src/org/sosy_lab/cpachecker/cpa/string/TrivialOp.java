/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

/**
 * The class that defines some trivial operations.
 */
public class TrivialOp {

  public static boolean XNOR(boolean a, boolean b) {
    return !(a^b);
  }
}
