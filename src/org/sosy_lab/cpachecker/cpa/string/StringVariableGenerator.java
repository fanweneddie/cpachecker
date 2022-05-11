/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import java.util.HashMap;
import java.util.Map;
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.java.JStringLiteralExpression;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;
import org.sosy_lab.cpachecker.util.states.MemoryLocationVisitor;

/**
 * It provides static methods to generate a variable for a string constant.
 */
public class StringVariableGenerator {

  /**
   * Cache the MemoryLocationVisitor of each function
   */
  private static final Map<String, MemoryLocationVisitor> visitorCache = new HashMap<>();

  private static final Map<MemoryLocation, MemoryLocation> variableCache = new HashMap<>();

  /**
   * Get the MemoryLocationVisitor for a given function.
   * We leverage {@link #visitorCache} to save time.
   * @param functionName the name of the given function
   * @return the visitor for <code>functionName</code>
   */
  private static MemoryLocationVisitor getVisitor(String functionName) {
    if (visitorCache.containsKey(functionName)) {
      return visitorCache.get(functionName);
    } else {
      MemoryLocationVisitor mlv = new MemoryLocationVisitor(functionName);
      visitorCache.put(functionName, mlv);
      return mlv;
    }
  }

  /**
   * Get the memoryLocation of an expression in a function.
   * @param expression the given expression
   * @param functionName the given function
   * @return the memoryLocation of <code>expression</code> in function <code>functionName</code>.
   */
  public static MemoryLocation getExpressionMemLocation(AExpression expression,
                                                        String functionName) {
    MemoryLocationVisitor mlv = getVisitor(functionName);

    if (expression instanceof JIdExpression) {
      JIdExpression IdExpression = (JIdExpression) expression;
      return checkInCache(IdExpression.accept(mlv));
    } else if (expression instanceof JArraySubscriptExpression) {
      JArraySubscriptExpression arraySubscriptExpression = (JArraySubscriptExpression) expression;
      return checkInCache(arraySubscriptExpression.accept(mlv));
    } else if (expression instanceof JStringLiteralExpression) {
      JStringLiteralExpression stringLiteralExpression = (JStringLiteralExpression) expression;
      return checkInCache(stringLiteralExpression.accept(mlv));
    } else {
      return null;
    }
  }

  /**
   * Use {@link #visitorCache} to return cached memory location, in order to save memory.
   * @param pMemoryLocation the given memory location
   * @return the cached one if an identical one (to <code>pMemoryLocation</code> is stored in {@link #visitorCache}
   */
  private static MemoryLocation checkInCache(MemoryLocation pMemoryLocation) {
    if (variableCache.containsKey(pMemoryLocation)) {
      return variableCache.get(pMemoryLocation);
    } else {
      variableCache.put(pMemoryLocation, pMemoryLocation);
      return pMemoryLocation;
    }
  }
}
