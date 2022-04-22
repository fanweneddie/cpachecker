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
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.automaton4string.BasicAutomata;
import org.sosy_lab.cpachecker.util.automaton4string.BasicOperations;
import org.sosy_lab.cpachecker.util.graph.RelationEdge;
import org.sosy_lab.cpachecker.util.graph.RelationGraph;
import org.sosy_lab.cpachecker.util.automaton4string.Automaton;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public final class StringAnalysisState
    implements AbstractQueryableState, LatticeAbstractState<StringAnalysisState>,
               Cloneable {

  /**
   * the map from string variables to their possible set of values.
   * Note that {@link #stringMap} and its Value must not be null
   */
  private final Map<MemoryLocation, Automaton> stringMap;

  /**
   * the graph that shows the relation among variables.
   * Note that {@link #relationGraph} must not be null
   */
  private final RelationGraph<MemoryLocation, StringRelationLabel,
      RelationEdge<MemoryLocation, StringRelationLabel>> relationGraph;

  public StringAnalysisState() {
    this.stringMap = new HashMap<>();
    this.relationGraph = new RelationGraph<>();
  }

  public StringAnalysisState(
      Map<MemoryLocation, Automaton> pStringMap,
      RelationGraph<MemoryLocation, StringRelationLabel,
          RelationEdge<MemoryLocation, StringRelationLabel>> pRelationGraph) {
      this.stringMap = checkNotNull(pStringMap);
      this.relationGraph = checkNotNull(pRelationGraph);
  }

  public StringAnalysisState(StringAnalysisState state) {
    this.stringMap = checkNotNull(state.stringMap);
    this.relationGraph = checkNotNull(state.relationGraph);
  }

  /**
   * Assign the value of a string variable.
   * @param variableName the name of the string variable, which must not be null
   * @param value the value to be assigned, which must not be null
   */
  public void assignStringValue(String variableName, String value) {
    checkNotNull(variableName);
    checkNotNull(value);

    MemoryLocation memoryLocation = MemoryLocation.parseExtendedQualifiedName(variableName);
    Automaton automaton = BasicAutomata.makeString(value);
    stringMap.put(memoryLocation, automaton);
  }

  /**
   * Get the set of value of a string variable.
   * @param variableName the name of the string variable, which must not be null
   * @return the automaton that represents the set of value of <code>variableName</code>;
   *         and null if <code>variableName</code> is not recorded in {@link #stringMap}
   */
  public Automaton getStringValue(String variableName) {
    checkNotNull(variableName);

    MemoryLocation memoryLocation = MemoryLocation.parseExtendedQualifiedName(variableName);
    return stringMap.get(memoryLocation);
  }

  /**
   * Remove a given string variable from this state, if it is in {@link #stringMap}.
   * @param pMemoryLocation the given string variable, which must not be null
   * @return true if {@link #stringMap} or {@link #relationGraph} has been modified
   */
  public boolean removeStringVariable(MemoryLocation pMemoryLocation) {
    assertNotNull(pMemoryLocation);

    if (!stringMap.containsKey(pMemoryLocation)) {
      return false;
    }

    stringMap.remove(pMemoryLocation);
    relationGraph.removeNode(pMemoryLocation);
    return true;
  }

  /**
   * Drop all entries belonging to the stack frame of a function.
   * It should be called right before leaving a function.
   * @param functionName the name of the function that is about to be left
   */
  void dropFrame(String functionName) {
    for (MemoryLocation variable : stringMap.keySet()) {
      if (variable.isOnFunctionStack(functionName)) {
        removeStringVariable(variable);
      }
    }
  }

  // Todo: set a relation between two string variables
  public void setRelation() {

  }

  /**
   * Propagate the value of other constrained variables after an assignment.
   * Here, the constraint is shown in {@link #relationGraph}.
   */
  private void propagateValueOnConstraint() {
  }

  @Override
  public Object evaluateProperty(String pProperty) throws InvalidQueryException {
    return null;
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    return false;
  }

  @Override
  public String getCPAName() {
    return "StringAnalysis";
  }

  /**
   * Join two abstract states that meet in one location.
   * Here, we set join(e1, e2) = least_upper_bound(e1, e2), which means that
   * we both union {@link #stringMap} and {@link #relationGraph}.
   * @param reachedState the other abstract state to be joined with this state,
   *                     which must not be null
   * @return the result of join operation
   */
  @Override
  public StringAnalysisState join(StringAnalysisState reachedState)
      throws CPAException, InterruptedException {
    assertNotNull(reachedState);

    // union stringMap
    Map<MemoryLocation, Automaton> newStringMap = new HashMap<>(stringMap);
    reachedState.stringMap.forEach(
        (key, value) ->
            newStringMap.merge(key, value, (value1, value2) ->
                  Automaton.union(Arrays.asList(value1, value2))));

    // union relationGraph
    RelationGraph<MemoryLocation, StringRelationLabel,
        RelationEdge<MemoryLocation, StringRelationLabel>> newRelationGraph;
    newRelationGraph = relationGraph.merge(reachedState.relationGraph);

    return new StringAnalysisState(newStringMap, newRelationGraph);
  }

  /**
   * Check whether this state is lessOrEqual to the given abstract state.
   * Here, lessOrEqual is the partial order s.t.
   * e1 lessOrEqual e2 iff e1's value domain and relation is the subset of e2's,
   * i.e. e2's {@link #stringMap} and {@link #relationGraph} both include e1's.
   * @param other the given abstract state of string analysis, which must not be null
   * @return true if this abstract state is lessOrEqual to <code>other</code>
   */
  @Override
  public boolean isLessOrEqual(StringAnalysisState other)
      throws CPAException, InterruptedException {
    assertNotNull(other);

    // check the subset case of stringMap
    boolean isSubset = stringMap.keySet().stream()
        .allMatch(key -> other.stringMap.get(key) != null
                        && BasicOperations.subsetOf(stringMap.get(key), other.stringMap.get(key)));
    if (!isSubset) {
      return false;
    }

    // check the subgraph case of relationGraph
    if (!relationGraph.subgraphOf(other.relationGraph)) {
      return false;
    }

    return true;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this){
      return true;
    }
    if (!(obj instanceof StringAnalysisState)) {
      return false;
    }
    StringAnalysisState stringAnalysisState = (StringAnalysisState) obj;
    return Objects.equals(stringMap, stringAnalysisState.stringMap)
        && Objects.equals(relationGraph, stringAnalysisState.relationGraph);
  }

  @Override
  public int hashCode() {
    return Objects.hash(stringMap, relationGraph);
  }

  @Override
  public String toString() {
    String str = stringMap.toString() + ", " + relationGraph.toString();
    return str;
  }

  /**
   * Return a deep copy of this StringAnalysisState.
   */
  public Object clone() throws CloneNotSupportedException {
    Map<MemoryLocation, Automaton> newStringMap = new HashMap<>(stringMap);
    RelationGraph<MemoryLocation, StringRelationLabel,
        RelationEdge<MemoryLocation, StringRelationLabel>> newRelationGraph =
                                                            (RelationGraph) relationGraph.clone();
    return new StringAnalysisState(newStringMap, newRelationGraph);
  }

  /**
   * The type of relation between two variables.
   * <p></p>
   * In the comments below, we suppose the variable represented by starting node as x,
   * and the variable represented by ending node as y.
   */
    enum StringRelationLabel {
    /** x and y are equal */
    EQUAL,
    /** x and y are reverse to each other */
    REVERSE_EQUAL,
    /** y = x concat z, so x is y's prefix */
    CONCAT_1,
    /** y = z concat x, so x is y's suffix */
    CONCAT_2,
    /** y = (reverse x) concat z, so reverse x is y's prefix */
    REVERSE_CONCAT_1,
    /** y = z concat (reverse x), so reverse x is y's suffix */
    REVERSE_CONCAT_2
  }
}