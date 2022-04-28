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

import java.util.Objects;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.graph.RelationEdge;
import org.sosy_lab.cpachecker.util.graph.RelationGraph;
import org.sosy_lab.cpachecker.core.defaults.LatticeAbstractState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public final class StringRelationAnalysisState
    implements AbstractQueryableState, LatticeAbstractState<StringRelationAnalysisState>,
               Cloneable {

  /**
   * the graph that shows the relation among variables.
   * Note that {@link #relationGraph} must not be null
   */
  private final RelationGraph<MemoryLocation, StringRelationLabel,
      RelationEdge<MemoryLocation, StringRelationLabel>> relationGraph;

  public StringRelationAnalysisState() {
    this.relationGraph = new RelationGraph<>();
  }

  public StringRelationAnalysisState(
      RelationGraph<MemoryLocation, StringRelationLabel,
          RelationEdge<MemoryLocation, StringRelationLabel>> pRelationGraph) {
      this.relationGraph = checkNotNull(pRelationGraph);
  }

  public StringRelationAnalysisState(StringRelationAnalysisState state) {
    this.relationGraph = checkNotNull(state.relationGraph);
  }

  /**
   * Try to get a deep copy of the given state.
   * If the deep copy fails, we just get a shallow copy of it.
   * @param state the given state, which should not be null
   * @return the deep copied state if possible
   */
  public static StringRelationAnalysisState deepCopyOf(StringRelationAnalysisState state) {
    assertNotNull(state);

    StringRelationAnalysisState clonedState;
    try {
      clonedState = (StringRelationAnalysisState) state.clone();
    } catch (CloneNotSupportedException cse) {
      clonedState = new StringRelationAnalysisState(state);
    }
    return clonedState;
  }

  /**
   * remove the variables in the given function from the relation graph.
   * @param functionName the name of the given function
   */
  public void dropFrame(String functionName) {
    for (MemoryLocation variableName : relationGraph.nodes()) {
      if (variableName.isOnFunctionStack(functionName)) {
        removeVariable(variableName);
      }
    }
  }

  /**
   * Remove a given variable from the relation graph.
   * @param pMemoryLocation the given variable, which must not be null
   * @return true if the relation graph is modified
   */
  private boolean removeVariable(MemoryLocation pMemoryLocation) {
    assertNotNull(pMemoryLocation);

    return relationGraph.removeNode(pMemoryLocation);
  }

  /**
   * Kill the relation between a given variable and the other variables.
   * @param pMemoryLocation the given variable
   * @return true if the relation is killed by this operation
   */
  public boolean killVariableRelation(MemoryLocation pMemoryLocation) {
    assertNotNull(pMemoryLocation);

    return relationGraph.makeNodeIsolated(pMemoryLocation);
  }

  /**
   * Add a labeled relation between two given variables.
   * @param pMemoryLocation1 the first given variable, which must not be null
   * @param pMemoryLocation2 the second given variable, which must not be null
   * @param pStringRelationLabel the given label, which must not be null
   * @return true if the graph is modified.
   */
  public boolean addRelation(MemoryLocation pMemoryLocation1,
                             MemoryLocation pMemoryLocation2,
                             StringRelationLabel pStringRelationLabel) {
    assertNotNull(pMemoryLocation1);
    assertNotNull(pMemoryLocation2);
    assertNotNull(pStringRelationLabel);

    relationGraph.addNode(pMemoryLocation1);
    relationGraph.addNode(pMemoryLocation2);
    RelationEdge<MemoryLocation, StringRelationLabel> newEdge = new RelationEdge<>(
              pMemoryLocation1, pMemoryLocation2, pStringRelationLabel);
    return relationGraph.addEdge(pMemoryLocation1, pMemoryLocation2, newEdge);
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
    return "StringRelationAnalysis";
  }

  /**
   * Join two abstract states that meet in one location.
   * Here, we set join(e1, e2) = least_upper_bound(e1, e2), which means that
   * we should union {@link #relationGraph}.
   * @param reachedState the other abstract state to be joined with this state,
   *                     which must not be null
   * @return the result of join operation
   */
  @Override
  public StringRelationAnalysisState join(StringRelationAnalysisState reachedState)
      throws CPAException, InterruptedException {
    assertNotNull(reachedState);

    // union relationGraph
    RelationGraph<MemoryLocation, StringRelationLabel,
        RelationEdge<MemoryLocation, StringRelationLabel>> newRelationGraph;
    newRelationGraph = relationGraph.merge(reachedState.relationGraph);

    return new StringRelationAnalysisState(newRelationGraph);
  }

  /**
   * Check whether this state is lessOrEqual to the given abstract state.
   * Here, lessOrEqual is the partial order s.t.
   * e1 lessOrEqual e2 iff e1's relation is the subset of e2's,
   * i.e. e2's {@link #relationGraph} includes e1's.
   * @param other the given abstract state of string analysis, which must not be null
   * @return true if this abstract state is lessOrEqual to <code>other</code>
   */
  @Override
  public boolean isLessOrEqual(StringRelationAnalysisState other)
      throws CPAException, InterruptedException {
    assertNotNull(other);

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
    if (!(obj instanceof StringRelationAnalysisState)) {
      return false;
    }
    StringRelationAnalysisState stringAnalysisState = (StringRelationAnalysisState) obj;
    return Objects.equals(relationGraph, stringAnalysisState.relationGraph);
  }

  @Override
  public int hashCode() {
    return Objects.hash(relationGraph);
  }

  @Override
  public String toString() {
    String str = "[" + relationGraph.toString() + "]";
    return str;
  }

  /**
   * Return a deep copy of this StringAnalysisState.
   */
  public Object clone() throws CloneNotSupportedException {
    RelationGraph<MemoryLocation, StringRelationLabel,
        RelationEdge<MemoryLocation, StringRelationLabel>> newRelationGraph =
                                                            (RelationGraph) relationGraph.clone();
    return new StringRelationAnalysisState(newRelationGraph);
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