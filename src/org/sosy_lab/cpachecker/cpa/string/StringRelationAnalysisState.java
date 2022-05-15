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
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
   * @return true if the graph is modified
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

  /**
   * Kill a given relation between two given variables (if possible).
   * @param pMemoryLocation1 the first given variable, which must not be null
   * @param pMemoryLocation2 the second given variable, which must not be null
   * @param pStringRelationLabel the given label, which must not be null
   * @return true if the graph is modified
   */
  private boolean killRelation(MemoryLocation pMemoryLocation1,
                               MemoryLocation pMemoryLocation2,
                               StringRelationLabel pStringRelationLabel) {
    assertNotNull(pMemoryLocation1);
    assertNotNull(pMemoryLocation2);
    assertNotNull(pStringRelationLabel);

    Set<RelationEdge<MemoryLocation, StringRelationLabel>> possibleEdges
        = relationGraph.edgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, pStringRelationLabel);

    boolean modified = false;
    for (RelationEdge<MemoryLocation, StringRelationLabel> edge : possibleEdges) {
      modified |= relationGraph.removeEdge(edge);
    }
    return modified;
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
   * Make two memory locations equal or unequal.
   * @param pMemoryLocation1 the first given memory location
   * @param pMemoryLocation2 the second given memory location
   * @param truthValue equal or unequal
   */
  public void makeEquality(MemoryLocation pMemoryLocation1,
                        MemoryLocation pMemoryLocation2,
                        boolean truthValue) {
    if (truthValue) {
      addRelation(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.EQUAL);
      addRelation(pMemoryLocation2, pMemoryLocation1, StringRelationLabel.EQUAL);
    } else {
      killRelation(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.EQUAL);
      killRelation(pMemoryLocation2, pMemoryLocation1, StringRelationLabel.EQUAL);
    }
  }

  /**
   * Make the given length variable as the length of the given string variable.
   * @param lengthVariable the given length variable
   * @param stringVariable the given string variable
   */
  public void makeLengthOf(MemoryLocation lengthVariable,
                           MemoryLocation stringVariable) {
    addRelation(lengthVariable, stringVariable, StringRelationLabel.LENGTH_OF);
  }

  /**
   * Make the first memory location as the concatenation of the second and third memory location.
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
  }

  /**
   * Make the given memory location reverse to others.
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
        relationGraph.inEdges(pMemoryLocation)) {
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

  public Set<RelationEdge<MemoryLocation, StringRelationLabel>> getOutEdges(MemoryLocation pMemoryLocation) {
    return relationGraph.outEdges(pMemoryLocation);
  }

  public Set<RelationEdge<MemoryLocation, StringRelationLabel>> getInEdges(MemoryLocation pMemoryLocation) {
    return relationGraph.inEdges(pMemoryLocation);
  }


  /**
   * Check whether the given property between two variables stands.
   * If any one of those two relation is not in {@link #relationGraph}, then return false.
   * @param pMemoryLocation1 the first given variable, which must not be null
   * @param pMemoryLocation2 the second given variable, which must not be null
   * @param property the given property, which must not be null
   * @return true if an equality relation is reasoned in this method
   */
  public boolean checkProperty(MemoryLocation pMemoryLocation1, MemoryLocation pMemoryLocation2, RelationProperty property) {
    assertNotNull(pMemoryLocation1);
    assertNotNull(pMemoryLocation2);
    assertNotNull(property);

    if (!relationGraph.containsNode(pMemoryLocation1) || !relationGraph.containsNode(pMemoryLocation2)) {
      return false;
    }

    // stores the variables that have been searched, in order to avoid repetitious searching
    Set<MemoryLocation> searchedVars = new HashSet<>();

    switch (property) {
      case EQUAL:
        return checkEqualityProperty(pMemoryLocation1, pMemoryLocation2, searchedVars, true);
      case PREFIX:
        return checkPrefixProperty(pMemoryLocation1, pMemoryLocation2, searchedVars);
      case SUFFIX:
        return checkSuffixProperty(pMemoryLocation1, pMemoryLocation2, searchedVars);
      default:
        return false;
    }

  }

  /**
   * Check whether two given variables are equal.
   * We convert the constraint-reasoning problem to graph-searching problem.
   * @param pMemoryLocation1 the first variable
   * @param pMemoryLocation2 the second variable
   * @param searchedVars stores the searched variables as a close list
   * @param isEqualRelation whether to check EQUAL (true) or REVERSE_EQUAL (false)
   */
  private boolean checkEqualityProperty(MemoryLocation pMemoryLocation1,
                                        MemoryLocation pMemoryLocation2,
                                        Set<MemoryLocation> searchedVars,
                                        boolean isEqualRelation) {
    // the relation of equality (i.e. EQUAL or REVERSE_EQUAL)
    StringRelationLabel relation = isEqualRelation?
                                   StringRelationLabel.EQUAL : StringRelationLabel.REVERSE_EQUAL;
    // check repetition
    if (searchedVars.contains(pMemoryLocation1) || searchedVars.contains(pMemoryLocation2)) {
      return false;
    }

    // trivial boundary case
    if (relation == StringRelationLabel.EQUAL && pMemoryLocation1.equals(pMemoryLocation2)) {
      return true;
    }

    // 1. judge equality directly
    if (relationGraph.hasEdgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, relation)) {
      return true;
    }

    // 2. propagate through EQUAL edges or REVERSE_EQUAL edge
    searchedVars.add(pMemoryLocation2);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtBack(pMemoryLocation1, pMemoryLocation2,
                                                        searchedVars, isEqualRelation)) {
      return true;
    }
    searchedVars.remove(pMemoryLocation2);

    searchedVars.add(pMemoryLocation1);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtFront(pMemoryLocation1, pMemoryLocation2,
        searchedVars, isEqualRelation)) {
      return true;
    }

    searchedVars.add(pMemoryLocation2);
    // 3. propagate through CONCAT or REVERSE_CONCAT edges
    if (propagateThroughCONCAT(pMemoryLocation1, pMemoryLocation2, searchedVars, isEqualRelation)) {
      return true;
    }

    // backtrace
    searchedVars.remove(pMemoryLocation1);
    searchedVars.remove(pMemoryLocation2);
    return false;
  }

  /**
   * Check whether <code>pMemoryLocation1</code> is the prefix of <code>pMemoryLocation2</code>.
   */
  private boolean checkPrefixProperty(MemoryLocation pMemoryLocation1,
                                      MemoryLocation pMemoryLocation2,
                                      Set<MemoryLocation> searchedVars) {
    // check repetition
    if (searchedVars.contains(pMemoryLocation1) || searchedVars.contains(pMemoryLocation2)) {
      return false;
    }

    // trivial boundary case
    if (pMemoryLocation1.equals(pMemoryLocation2)) {
      return true;
    }

    // 1. judge prefix directly
    if (relationGraph.hasEdgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.EQUAL)
        || relationGraph.hasEdgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.CONCAT_AS_PREFIX)) {
      return true;
    }

    // 2. propagate through EQUAL edges or REVERSE_EQUAL
    searchedVars.add(pMemoryLocation2);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtBack(pMemoryLocation1, pMemoryLocation2,
        searchedVars, true)) {
      return true;
    }
    searchedVars.remove(pMemoryLocation2);

    searchedVars.add(pMemoryLocation1);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtFront(pMemoryLocation1, pMemoryLocation2,
        searchedVars, true)) {
      return true;
    }

    // 3. propagate through CONCAT_AS_PREFIX
    // (note that pMemoryLocation1 is still in close list)
    if (propagateThroughCONCAT_AS_PREFIX_AtFront(pMemoryLocation1, pMemoryLocation2, searchedVars)) {
      return true;
    }

    // backtrace
    searchedVars.remove(pMemoryLocation1);
    searchedVars.remove(pMemoryLocation2);
    return false;
  }

  /**
   * Check whether <code>pMemoryLocation1</code> is the suffix of <code>pMemoryLocation2</code>.
   */
  private boolean checkSuffixProperty(MemoryLocation pMemoryLocation1,
                                      MemoryLocation pMemoryLocation2,
                                      Set<MemoryLocation> searchedVars) {
    // check repetition
    if (searchedVars.contains(pMemoryLocation1) || searchedVars.contains(pMemoryLocation2)) {
      return false;
    }

    // trivial boundary case
    if (pMemoryLocation1.equals(pMemoryLocation2)) {
      return true;
    }

    // 1. judge suffix directly
    if (relationGraph.hasEdgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.EQUAL)
        || relationGraph.hasEdgesConnectingWithLabel(pMemoryLocation1, pMemoryLocation2, StringRelationLabel.CONCAT_AS_SUFFIX)) {
      return true;
    }

    // 2. propagate through EQUAL edges or REVERSE_EQUAL
    searchedVars.add(pMemoryLocation2);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtBack(pMemoryLocation1, pMemoryLocation2,
        searchedVars, true)) {
      return true;
    }
    searchedVars.remove(pMemoryLocation2);

    searchedVars.add(pMemoryLocation1);
    if (propagateThroughEQUAL_OrREVERSE_EQUAL_AtFront(pMemoryLocation1, pMemoryLocation2,
        searchedVars, true)) {
      return true;
    }

    // 3. propagate through CONCAT_AS_SUFFIX
    // (note that pMemoryLocation1 is still in close list)
    if (propagateThroughCONCAT_AS_SUFFIX_AtFront(pMemoryLocation1, pMemoryLocation2, searchedVars)) {
      return true;
    }

    // backtrace
    searchedVars.remove(pMemoryLocation1);
    searchedVars.remove(pMemoryLocation2);
    return false;
  }

  private boolean checkContainProperty(MemoryLocation pMemoryLocation1,
                                       MemoryLocation pMemoryLocation2,
                                       Set<MemoryLocation> searchedVars) {
    return false;
  }

  /**
   * Further propagate the equality checking to the neighboring variables of <code>pMemoryLocation1</code>
   * EQUAL or REVERSE_EQUAL edges and <code>pMemoryLocation2</code>.
   * @param isEqualRelation whether to check EQUAL (true) or REVERSE_EQUAL (false)
   */
  private boolean propagateThroughEQUAL_OrREVERSE_EQUAL_AtFront(MemoryLocation pMemoryLocation1,
                                                       MemoryLocation pMemoryLocation2,
                                                       Set<MemoryLocation> searchedVars,
                                                       boolean isEqualRelation) {
    // get all the variables that are equal or reverse_equal to pMemoryLocation1
    // equalVarsTo1 means the set of variables that have EQUAL edge to pMemoryLocation1
    // reverseEqualVarsTo1 means the set of variables that have REVERSE_EQUAL edge to pMemoryLocation1
    Set<MemoryLocation> equalVarsTo1 = relationGraph.outNodesWithLabel(pMemoryLocation1, StringRelationLabel.EQUAL);
    Set<MemoryLocation> reverseEqualVarsTo1 = relationGraph.outNodesWithLabel(pMemoryLocation2, StringRelationLabel.REVERSE_EQUAL);

    // check the equality relation between pMemoryLocation1's neighboring node and pMemoryLocation2
    for (MemoryLocation equalVarTo1 : equalVarsTo1) {
      if (checkEqualityProperty(equalVarTo1, pMemoryLocation2, searchedVars, isEqualRelation)) {
        return true;
      }
    }

    for (MemoryLocation reverseEqualVarTo1 : reverseEqualVarsTo1) {
      if (checkEqualityProperty(reverseEqualVarTo1, pMemoryLocation2, searchedVars, !isEqualRelation)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Further propagate the equality checking to <code>pMemoryLocation1</code>
   * and the neighboring variables of <code>pMemoryLocation2</code> with EQUAL or REVERSE_EQUAL edges.
   * @param isEqualRelation whether to check EQUAL (true) or REVERSE_EQUAL (false)
   */
  private boolean propagateThroughEQUAL_OrREVERSE_EQUAL_AtBack(MemoryLocation pMemoryLocation1,
                                                              MemoryLocation pMemoryLocation2,
                                                              Set<MemoryLocation> searchedVars,
                                                              boolean isEqualRelation) {
    // get all the variables that are equal or reverse_equal to pMemoryLocation2
    // equalVarsTo2 means the set of variables that have EQUAL edge to pMemoryLocation2
    // reverseEqualVarsTo2 means the set of variables that have REVERSE_EQUAL edge to pMemoryLocation2
    Set<MemoryLocation> equalVarsTo2 = relationGraph.outNodesWithLabel(pMemoryLocation2, StringRelationLabel.EQUAL);
    Set<MemoryLocation> reverseEqualVarsTo2 = relationGraph.outNodesWithLabel(pMemoryLocation2, StringRelationLabel.REVERSE_EQUAL);

    // check the equality relation between pMemoryLocation1 and pMemoryLocation2's neighboring node
    for (MemoryLocation equalVarTo2 : equalVarsTo2) {
      if (checkEqualityProperty(pMemoryLocation1, equalVarTo2, searchedVars, isEqualRelation)) {
        return true;
      }
    }

    for (MemoryLocation reverseEqualVarTo2 : reverseEqualVarsTo2) {
      if (checkEqualityProperty(pMemoryLocation1, reverseEqualVarTo2, searchedVars, !isEqualRelation)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Further propagate the prefix checking to the extension of <code>pMemoryLocation1</code>
   * and <code>pMemoryLocation2</code>.
   */
  private boolean propagateThroughCONCAT_AS_PREFIX_AtFront(MemoryLocation pMemoryLocation1,
                                                              MemoryLocation pMemoryLocation2,
                                                              Set<MemoryLocation> searchedVars) {
    // get all the variables that extend pMemoryLocation1
    Set<MemoryLocation> extendedVarsTo1 = relationGraph.outNodesWithLabel(pMemoryLocation1,
                                                              StringRelationLabel.CONCAT_AS_PREFIX);

    // check the equality relation between pMemoryLocation1's neighboring node and pMemoryLocation2
    for (MemoryLocation extendedVarTo1 : extendedVarsTo1) {
      if (checkPrefixProperty(extendedVarTo1, pMemoryLocation2, searchedVars)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Further propagate the suffix checking to the extension of <code>pMemoryLocation1</code>
   * and <code>pMemoryLocation2</code>.
   */
  private boolean propagateThroughCONCAT_AS_SUFFIX_AtFront(MemoryLocation pMemoryLocation1,
                                                           MemoryLocation pMemoryLocation2,
                                                           Set<MemoryLocation> searchedVars) {
    // get all the variables that extend pMemoryLocation1
    Set<MemoryLocation> extendedVarsTo1 = relationGraph.outNodesWithLabel(pMemoryLocation1,
                                                            StringRelationLabel.CONCAT_AS_SUFFIX);

    // check the equality relation between pMemoryLocation1's neighboring node and pMemoryLocation2
    for (MemoryLocation extendedVarTo1 : extendedVarsTo1) {
      if (checkSuffixProperty(extendedVarTo1, pMemoryLocation2, searchedVars)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Further propagate the equality checking to the prefix and suffix of two given variables
   * @param isEqualRelation whether to check EQUAL (true) or REVERSE_EQUAL (false)
   */
  private boolean propagateThroughCONCAT(MemoryLocation pMemoryLocation1,
                                        MemoryLocation pMemoryLocation2,
                                        Set<MemoryLocation> searchedVars,
                                        boolean isEqualRelation) {

    // get the prefix and suffix of pMemoryLocation1 and pMemoryLocation2
    // prefixEdgesTo1 is the edges from pMemoryLocation1's prefix (so and so forth)
    Set<RelationEdge<MemoryLocation, StringRelationLabel>> prefixEdgesTo1
        = relationGraph.inEdgesWithLabel(pMemoryLocation1, StringRelationLabel.CONCAT_AS_PREFIX);
    prefixEdgesTo1.addAll(relationGraph.inEdgesWithLabel(pMemoryLocation1, StringRelationLabel.REVERSE_CONCAT_AS_PREFIX));

    Set<RelationEdge<MemoryLocation, StringRelationLabel>> suffixEdgesTo1
        = relationGraph.inEdgesWithLabel(pMemoryLocation1, StringRelationLabel.CONCAT_AS_SUFFIX);
    suffixEdgesTo1.addAll(relationGraph.inEdgesWithLabel(pMemoryLocation1, StringRelationLabel.REVERSE_CONCAT_AS_SUFFIX));

    Set<RelationEdge<MemoryLocation, StringRelationLabel>> prefixEdgesTo2
        = relationGraph.inEdgesWithLabel(pMemoryLocation2, StringRelationLabel.CONCAT_AS_PREFIX);
    prefixEdgesTo2.addAll(relationGraph.inEdgesWithLabel(pMemoryLocation2, StringRelationLabel.REVERSE_CONCAT_AS_PREFIX));

    Set<RelationEdge<MemoryLocation, StringRelationLabel>> suffixEdgesTo2
        = relationGraph.inEdgesWithLabel(pMemoryLocation2, StringRelationLabel.CONCAT_AS_SUFFIX);
    suffixEdgesTo2.addAll(relationGraph.inEdgesWithLabel(pMemoryLocation2, StringRelationLabel.REVERSE_CONCAT_AS_SUFFIX));

    // we don't consider cases with multiple prefix and suffix
    if (prefixEdgesTo1.size() == 1 && suffixEdgesTo1.size() == 1 &&
        prefixEdgesTo2.size() == 1 && suffixEdgesTo2.size() == 1) {
      // prefixVarTo1 is the variable as pMemoryLocation1's prefix (so and so forth)
      MemoryLocation prefixVarTo1 = prefixEdgesTo1.iterator().next().getStartNode();
      MemoryLocation suffixVarTo1 = suffixEdgesTo1.iterator().next().getStartNode();
      MemoryLocation prefixVarTo2 = prefixEdgesTo2.iterator().next().getStartNode();
      MemoryLocation suffixVarTo2 = suffixEdgesTo2.iterator().next().getStartNode();

      // prefixRelationTo1 is the equality relation between prefixVarTo1 and pMemoryLocation1 (so and so forth)
      StringRelationLabel prefixRelationTo1 = prefixEdgesTo1.iterator().next().getLabel();
      StringRelationLabel suffixRelationTo1 = suffixEdgesTo1.iterator().next().getLabel();
      StringRelationLabel prefixRelationTo2 = prefixEdgesTo2.iterator().next().getLabel();
      StringRelationLabel suffixRelationTo2 = suffixEdgesTo2.iterator().next().getLabel();

      boolean concatEqual = false;
      // propagate through CONCAT, based on isEqualRelation
      if (isEqualRelation) {
        // check EQUAL relation between pMemoryLocation1 and pMemoryLocation2
        if (prefixRelationTo1 == prefixRelationTo2) {
          concatEqual = checkEqualityProperty(prefixVarTo1, prefixVarTo2, searchedVars, true);
        } else {
          concatEqual = checkEqualityProperty(prefixVarTo1, prefixVarTo2, searchedVars, false);
        }

        if (concatEqual) {
          if (suffixRelationTo1 == suffixRelationTo2) {
            concatEqual = checkEqualityProperty(suffixVarTo1, suffixVarTo2, searchedVars, true);
          } else {
            concatEqual = checkEqualityProperty(suffixVarTo1, suffixVarTo2, searchedVars, false);
          }
        }
      } else {
        // check REVERSE_EQUAL relation between pMemoryLocation1 and pMemoryLocation2
        if (prefixRelationTo1 == suffixRelationTo2) {
          concatEqual = checkEqualityProperty(prefixVarTo1, suffixVarTo2, searchedVars, false);
        } else {
          concatEqual = checkEqualityProperty(prefixVarTo1, suffixVarTo2, searchedVars, true);
        }

        if (concatEqual) {
          if (suffixRelationTo1 == prefixRelationTo2) {
            concatEqual = checkEqualityProperty(suffixVarTo1, prefixVarTo2, searchedVars, false);
          } else {
            concatEqual = checkEqualityProperty(suffixVarTo1, prefixVarTo2, searchedVars, true);
          }
        }
      }

      if (concatEqual) {
        return true;
      }
    }
    return false;
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
  public enum StringRelationLabel {
    /** x and y are equal */
    EQUAL,
    /** x and y are reverse to each other */
    REVERSE_EQUAL,
    /** y = x concat z, so x concatenates to y as prefix */
    CONCAT_AS_PREFIX,
    /** y = x concat z, so z concatenates to y as suffix */
    CONCAT_AS_SUFFIX,
    /** y = (reverse x) concat z, so x reversely concatenates to y as prefix */
    REVERSE_CONCAT_AS_PREFIX,
    /** y = x concat (reverse z), so z reversely concatenates to y as suffix */
    REVERSE_CONCAT_AS_SUFFIX,
    /** y = length(x), so y is the length of x */
    LENGTH_OF
  }

  /**
   * The property of relation between two strings (in order to be checked).
   */
  public enum RelationProperty {
    EQUAL,
    PREFIX,
    SUFFIX
  }
}