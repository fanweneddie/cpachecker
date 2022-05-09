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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.ast.java.JReferencedMethodInvocationExpression;
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

  /**
   * maps from a temporary variable to its invocation expression.
   * Note that {@link #invocationMap} must not be null
   */
  private final Map<MemoryLocation, JReferencedMethodInvocationExpression> invocationMap;

  public StringRelationAnalysisState() {
    this.relationGraph = new RelationGraph<>();
    this.invocationMap = new HashMap<>();
  }

  public StringRelationAnalysisState(
      RelationGraph<MemoryLocation, StringRelationLabel,
          RelationEdge<MemoryLocation, StringRelationLabel>> pRelationGraph,
      Map<MemoryLocation, JReferencedMethodInvocationExpression> pInvocationMap) {
      this.relationGraph = checkNotNull(pRelationGraph);
      this.invocationMap = checkNotNull(pInvocationMap);
  }

  public StringRelationAnalysisState(StringRelationAnalysisState state) {
    this.relationGraph = checkNotNull(state.relationGraph);
    this.invocationMap = checkNotNull(state.invocationMap);
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
   * add a variable into {@link #relationGraph}, if it is not present.
   * @param pMemoryLocation the given variable, which must not be null
   * @return true if {@link #relationGraph} is modified
   */
  public boolean addVariable(MemoryLocation pMemoryLocation) {
    assertNotNull(pMemoryLocation);

    return relationGraph.addNode(pMemoryLocation);
  }

  /**
   * Remove the variables in the given function from the relation graph.
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
  private boolean addRelation(MemoryLocation pMemoryLocation1,
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
   * Copy the invocation of the first variable to the second variable.
   * @param pMemoryLocationFrom the first variable, whose invocation is what we copy from
   * @param pMemoryLocationTo the second variable, whose invocation is what we copy to
   */
  public void copyInvocation(MemoryLocation pMemoryLocationFrom,
                             MemoryLocation pMemoryLocationTo) {
    JReferencedMethodInvocationExpression invocation = getInvocation(pMemoryLocationFrom);
    if (invocation != null) {
      setInvocation(pMemoryLocationTo, invocation);
    }
  }

  /**
   * Get the invocation assignment statement of a variable.
   */
  public JReferencedMethodInvocationExpression getInvocation(MemoryLocation pMemoryLocation) {
    return invocationMap.get(pMemoryLocation);
  }

  /**
   * Set the invocation assignment statement of a variable.
   */
  public void setInvocation(MemoryLocation pMemoryLocation,
                            JReferencedMethodInvocationExpression pInvocation) {
    invocationMap.put(pMemoryLocation, pInvocation);
  }

  /**
   * make two memory locations equal.
   * @param pMemoryLocation1 the first given memory location
   * @param pMemoryLocation2 the second given memory location
   */
  public void makeEqual(MemoryLocation pMemoryLocation1,
                        MemoryLocation pMemoryLocation2) {
    addRelation(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.EQUAL);
    addRelation(pMemoryLocation2, pMemoryLocation1, StringRelationLabel.EQUAL);
  }

  /**
   * make the first memory location as the concatenation of the second and third memory location.
   * @param pMemoryLocationTo the first given memory location, which is the result of concatenation
   * @param pMemoryLocationFrom1 the second given memory location, which is the prefix of concatenation
   * @param pMemoryLocationFrom2 the third given memory location, which is the suffix of concatenation
   */
  public void makeConcat(MemoryLocation pMemoryLocationTo,
                         MemoryLocation pMemoryLocationFrom1,
                         MemoryLocation pMemoryLocationFrom2) {
    // add relation between pMemoryLocationTo with pMemoryLocationFrom1 and pMemoryLocationFrom2
    addRelation(pMemoryLocationFrom1, pMemoryLocationTo, StringRelationLabel.CONCAT_AS_PREFIX);
    addRelation(pMemoryLocationFrom2, pMemoryLocationTo, StringRelationLabel.CONCAT_AS_SUFFIX);
    // add relation between pMemoryLocationFrom1 with pMemoryLocationFrom2
    addRelation(pMemoryLocationFrom1, pMemoryLocationFrom2, StringRelationLabel.CONCAT_WITH);
    addRelation(pMemoryLocationFrom2, pMemoryLocationFrom1, StringRelationLabel.CONCAT_WITH);
  }

  /**
   * make the given memory location reverse to others.
   * @param pMemoryLocation the given memory location
   */
  public void makeReverse(MemoryLocation pMemoryLocation) {
    // consider the relation starting from pMemoryLocation
    for (RelationEdge<MemoryLocation, StringRelationLabel> re :
                relationGraph.outEdges(pMemoryLocation)) {
     if (re.isSelfLoop()) {
       continue;
     }

      StringRelationLabel label = re.getLabel();
      switch (label) {
        case EQUAL:
          re.setLabel(StringRelationLabel.REVERSE_EQUAL);
          break;
        case REVERSE_EQUAL:
          re.setLabel(StringRelationLabel.EQUAL);
          break;
        case CONCAT_AS_PREFIX:
          re.setLabel(StringRelationLabel.REVERSE_CONCAT_AS_PREFIX);
          break;
        case CONCAT_AS_SUFFIX:
          re.setLabel(StringRelationLabel.REVERSE_CONCAT_AS_SUFFIX);
          break;
        case REVERSE_CONCAT_AS_PREFIX:
          re.setLabel(StringRelationLabel.CONCAT_AS_PREFIX);
          break;
        case REVERSE_CONCAT_AS_SUFFIX:
          re.setLabel(StringRelationLabel.CONCAT_AS_SUFFIX);
          break;
        default:
          break;
      }
    }

    // consider the relation ending at pMemoryLocation
    for (RelationEdge<MemoryLocation, StringRelationLabel> re :
        relationGraph.outEdges(pMemoryLocation)) {
      if (re.isSelfLoop()) {
        continue;
      }

      StringRelationLabel label = re.getLabel();
      switch (label) {
        case EQUAL:
          re.setLabel(StringRelationLabel.REVERSE_EQUAL);
          break;
        case REVERSE_EQUAL:
          re.setLabel(StringRelationLabel.EQUAL);
          break;
        case CONCAT_AS_PREFIX:
          re.setLabel(StringRelationLabel.REVERSE_CONCAT_AS_SUFFIX);
          break;
        case CONCAT_AS_SUFFIX:
          re.setLabel(StringRelationLabel.REVERSE_CONCAT_AS_PREFIX);
          break;
        case REVERSE_CONCAT_AS_PREFIX:
          re.setLabel(StringRelationLabel.CONCAT_AS_SUFFIX);
          break;
        case REVERSE_CONCAT_AS_SUFFIX:
          re.setLabel(StringRelationLabel.CONCAT_AS_PREFIX);
          break;
        default:
          break;
      }
    }
  }

  /**
   * Remove the invocation assignment statement of a variable.
   */
  private void removeInvocation(MemoryLocation pMemoryLocation) {
    invocationMap.remove(pMemoryLocation);
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

    Map<MemoryLocation, JReferencedMethodInvocationExpression> newInvocationMap = new HashMap<>();
    newInvocationMap.putAll(invocationMap);
    newInvocationMap.putAll(reachedState.invocationMap);

    return new StringRelationAnalysisState(newRelationGraph, newInvocationMap);
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
    Map<MemoryLocation, JReferencedMethodInvocationExpression> newInvocationMap = new HashMap<>();
    newInvocationMap.putAll(invocationMap);

    return new StringRelationAnalysisState(newRelationGraph, newInvocationMap);
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
    CONCAT_AS_PREFIX,
    /** y = z concat x, so x is y's suffix */
    CONCAT_AS_SUFFIX,
    /** y = (reverse x) concat z, so reverse x is y's prefix */
    REVERSE_CONCAT_AS_PREFIX,
    /** y = z concat (reverse x), so reverse x is y's suffix */
    REVERSE_CONCAT_AS_SUFFIX,
    /** z = (x or reverse x) concat (y or reverse y), so x and y concatenate with each other */
    CONCAT_WITH
  }
}