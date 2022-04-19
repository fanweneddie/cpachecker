/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.util.graph;

import java.util.Objects;

/**
 * Directed Edge in RelationGraph, which represents a relation between two nodes.
 * The label on each edge shows which relation it represents.
 * <p></p>
 * @param <N> the type of node
 * @param <L> the type of edge label
 */
public class RelationEdge<N, L> {

  /**
   * The subject of the relation,
   * and it is in the starting node of the edge
   */
  private final N startNode;

  /**
   * The object of the relation,
   * and it is in the ending node of the edge
   */
  private final N endNode;

  /**
   * The label on the edge that shows the type of relation
   */
  private final L label;

  public RelationEdge(
      N pStartNode,
      N pEndNode,
      L pLabel) {
    this.startNode = pStartNode;
    this.endNode = pEndNode;
    this.label = pLabel;
  }

  public N getStartNode() {
    return startNode;
  }

  public N getEndNode() {
    return endNode;
  }

  public L getLabel() {
    return label;
  }

  /**
   * Check whether this edge starts from a given node.
   */
  public boolean startsFrom(N node) {
    return startNode == node;
  }

  /**
   * Check whether this edge ends at a given node.
   */
  public boolean endsAt(N node) {
    return endNode == node;
  }

  /**
   * Check whether the fields are valid, i.e. whether it has null field.
   */
  public boolean valid() {
    return startNode != null && endNode != null && label != null;
  }

  /**
   * Check whether this edge connects two nodes.
   */
  public boolean connects(N node1, N node2) {
    return startsFrom(node1) && endsAt(node2);
  }

  /**
   * Get the edge whose relation is the reverse of this edge.
   * @return the reverse edge
   */
  public RelationEdge getReservedEdge() {
    return new RelationEdge(endNode, startNode, label);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this){
      return true;
    }
    if (!(obj instanceof RelationEdge)) {
      return false;
    }
    RelationEdge relationEdge = (RelationEdge) obj;
    return Objects.equals(startNode, relationEdge.startNode)
        && Objects.equals(endNode, relationEdge.endNode)
        && label == relationEdge.label;
  }

  @Override
  public int hashCode() {
    return Objects.hash(startNode, endNode, label);
  }

  @Override
  public String toString() {
    String str = "[" + startNode.toString() + "] to ["
                  + endNode.toString() + "] with label "
                  + label.toString();
    return str;
  }
}