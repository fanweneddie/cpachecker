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
 * This class contains some global variables.
 * I don't want to define global variables in Java.
 * However, it is too troublesome to pass arguments in visitor pattern :(
 */
public class GlobalVars {

  /**
   * Whether the current assumption is in an assertion statement
   */
  public static boolean isAssertion;

}
