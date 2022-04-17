/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import static com.google.common.base.Preconditions.checkNotNull;

import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.util.graph.RelationEdge;
import org.sosy_lab.cpachecker.util.graph.RelationGraph;
import org.sosy_lab.cpachecker.util.automaton4string.Automaton;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public final class StringAnalysisState
    implements AbstractQueryableState, LatticeAbstractState<StringAnalysisState> {

  /**
   * the map from string variables to their possible set of values
   */
  private PersistentMap<MemoryLocation, Automaton> stringMap;

  /**
   * the graph that shows the relation among variables
   */
  private RelationGraph<MemoryLocation, StringRelationLabel,
      RelationEdge<MemoryLocation, StringRelationLabel>> relationGraph;

  /**
   * hashCode should be updated after every change of {@link #stringMap}
   */
  private int hashCode = 0;

  public StringAnalysisState() {
      stringMap = null;
      hashCode = 0;
  }

  private StringAnalysisState(PersistentMap<MemoryLocation, Automaton> pStringMap) {
      stringMap = checkNotNull(pStringMap);
      hashCode = pStringMap.hashCode();
  }

  private StringAnalysisState(StringAnalysisState state) {
    stringMap = checkNotNull(state.stringMap);
    hashCode = state.hashCode;
    assert hashCode == stringMap.hashCode();
  }

  /**
   * Assign the value of a string variable
   * @param variableName name of the string variable
   * @param value value to be assigned
   */
  void assignStringValue(String variableName, Value value) {
  // Todo
  }

  /**
   * Put the value of a string variable into the map
   * @param pMemLoc name of the string variable
   * @param pValue value to be put
   */
  private void putToStringMap(final MemoryLocation pMemLoc, final Value pValue) {
  // Todo
  }

  @Override
  public String getCPAName() {
    return "StringAnalysis";
  }

  @Override
  public StringAnalysisState join(StringAnalysisState reachedState) {
    // Todo
    return reachedState;
  }

  @Override
  public boolean isLessOrEqual(StringAnalysisState other) {
    // Todo
    return true;
  }
}

/**
 * The type of relation between two variables
 * Here, we name the variable represented by starting node as x,
 * and the variable represented by ending node as y
 */
enum StringRelationLabel {
  EQUAL,              // x and y are equal
  REVERSE_EQUAL,      // x and y are reverse to each other
  CONCAT_1,           // y = x concat z, so x is y's prefix
  CONCAT_2,           // y = z concat x, so x is y's suffix
  REVERSE_CONCAT_1,   // y = (reverse x) concat z, so reverse x is y's prefix
  REVERSE_CONCAT_2    // y = z concat (reverse x), so reverse x is y's suffix
}