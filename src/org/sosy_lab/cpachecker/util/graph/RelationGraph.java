/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.util.graph;

import static org.junit.Assert.assertNotNull;

import com.google.common.graph.ElementOrder;
import com.google.common.graph.EndpointPair;
import com.google.common.graph.Graph;
import com.google.common.graph.MutableNetwork;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The directed graph that shows the relation among nodes,
 * where each directed edge represents a relation between two variables.
 * <p></p>
 * We use a double directed adjacent list to store the graph,
 * which contains two adjacent lists of starting edges and ending edges at each node.
 * <p></p>
 * @param <N> The type of node in the graph.
 * @param <L> The type of label in the edge.
 * @param <RE> The type of relation edge in the graph.
 */
public class RelationGraph<N, L, RE extends RelationEdge<N, L>>
    implements MutableNetwork<N, RE> {

    /**
     * Adjacent list to represent the graph,
     * which stores the relation edges starting from each node
     * <p></p>
     * e.g. if e connects n1 to n2, then adjStartList[n1] contains e
     */
    private final HashMap<N, HashSet<RE>> adjStartList;

    /**
     * Adjacent list to represent the graph,
     * which stores the relation edges ending at each node
     * <p></p>
     * e.g. if e connects n1 to n2, then adjStartList[n2] contains e
     */
    private final HashMap<N, HashSet<RE>> adjEndList;

    public RelationGraph() {
        this.adjStartList = new HashMap<>();
        this.adjEndList = new HashMap<>();
    }

    /**
     * Add a node into the graph, if it is not present.
     * <p></p>
     * Note that adjStartList and adjEndList should have consistent nodes and relation edges.
     * <p></p>
     * @param pN The node to be added, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addNode(N pN) {
        assertNotNull(pN);

        if (!adjStartList.containsKey(pN)) {
            adjStartList.put(pN, new HashSet<>());
            adjEndList.put(pN, new HashSet<>());
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add a directed relation edge from one node to the other, if it is not present.
     * <p></p>
     * Note that there can be multiple relation edges between two nodes,
     * as long as their labels are distinctive.
     * Also note that adjStartList and adjEndList should have consistent nodes and relation edges.
     * <p></p>
     * @param pN The starting node, which must not be null and must have been in graph.
     * @param pN1 The ending node, which must not be null and must have been in graph.
     * @param pRE The newly added relation edge, which must not be null and must connect pN to pN1.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addEdge(N pN, N pN1, RE pRE) {
        assertNotNull(pN);
        assertNotNull(pN1);
        assertNotNull(pRE);
        assert pRE.valid();
        assert adjStartList.containsKey(pN) && adjEndList.containsKey(pN1);
        assert pRE.startFrom(pN) && pRE.endAt(pN1);

        if (!adjStartList.get(pN).contains(pRE)) {
            adjStartList.get(pN).add(pRE);
            adjEndList.get(pN1).add(pRE);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add a directed edge from one node to the other in EndpointPair,
     * if the edge is not present.
     * We just call addEdge(N pN, N pN1, E pE) to solve the problem.
     * <p></p>
     * @param pEndpointPair A pair of nodes, whose first element is starting node
     *                     and second element is ending point. It must not be null.
     * @param pE: The newly added edge.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addEdge(EndpointPair<N> pEndpointPair, RE pRE) {
        assertNotNull(pEndpointPair);

        return addEdge(pEndpointPair.nodeU(), pEndpointPair.nodeV(), pRE);
    }

    /**
     * Remove a node from the graph, if it is present in the graph.
     * The edges that connect with the node is also removed.
     * <p></p>
     * Note that adjStartList and adjEndList should have consistent nodes and relation edges.
     * <p></p>
     * @param pN the node to be removed, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean removeNode(N pN) {
        assertNotNull(pN);

        if (adjStartList.containsKey(pN)) {
            // remove edges that start from pN
            for (RE relationEdge : adjStartList.get(pN)) {
                removeEdge(relationEdge);
            }
            // remove edges that ends at pN
            for (RE relationEdge : adjEndList.get(pN)) {
                removeEdge(relationEdge);
            }
            // remove the node
            adjStartList.remove(pN);
            adjEndList.remove(pN);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Remove a relation edge from this graph, if it is present.
     * The nodes that the edge is connected with are not removed.
     * <p></p>
     * Note that the relation edge must not be null.
     * Also note that adjStartList and adjEndList should have consistent nodes and relation edges.
     * <p></p>
     * @param pRE the edge to be removed, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean removeEdge(RE pRE) {
        assertNotNull(pRE);
        assert(pRE.valid());

        N startNode = pRE.getStartNode();
        N endNode = pRE.getEndNode();
        if (adjStartList.containsKey(startNode)
            && adjStartList.get(startNode).contains(pRE)) {
            adjStartList.get(startNode).remove(pRE);
            adjEndList.get(endNode).remove(pRE);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get all nodes in the graph
     */
    @Override
    public Set<N> nodes() {
        return adjStartList.keySet();
    }

    /**
     * Get all relation edges in the graph.
     * This lazy method is slow.
     */
    @Override
    public Set<RE> edges() {
        Set<RE> relationEdgeSet = new HashSet<>();
        for (HashSet<RE> startEdgeSet: adjStartList.values()) {
            relationEdgeSet.addAll(startEdgeSet);
        }
        return relationEdgeSet;
    }

    /**
     * We don't use this method.
     */
    @Override
    public Graph<N> asGraph() {
        return null;
    }

    /**
     * Check whether this graph is a directed graph.
     */
    @Override
    public boolean isDirected() {
        return true;
    }

    @Override
    public boolean allowsParallelEdges() {
        return false;
    }

    @Override
    public boolean allowsSelfLoops() {
        return false;
    }

    @Override
    public ElementOrder<N> nodeOrder() {
        return null;
    }

    @Override
    public ElementOrder<RE> edgeOrder() {
        return null;
    }

    @Override
    public Set<N> adjacentNodes(N pN) {
        return null;
    }

    @Override
    public Set<N> predecessors(N pN) {
        return null;
    }

    @Override
    public Set<N> successors(N pN) {
        return null;
    }

    @Override
    public Set<RE> incidentEdges(N pN) {
        return null;
    }

    @Override
    public Set<RE> inEdges(N pN) {
        return null;
    }

    @Override
    public Set<RE> outEdges(N pN) {
        return null;
    }

    @Override
    public int degree(N pN) {
        return 0;
    }

    @Override
    public int inDegree(N pN) {
        return 0;
    }

    @Override
    public int outDegree(N pN) {
        return 0;
    }

    @Override
    public EndpointPair<N> incidentNodes(RE pRE) {
        return null;
    }

    @Override
    public Set<RE> adjacentEdges(RE pE) {
        return null;
    }

    @Override
    public Set<RE> edgesConnecting(N pN, N pN1) {
        return null;
    }

    @Override
    public Set<RE> edgesConnecting(EndpointPair<N> pEndpointPair) {
        return null;
    }

    @Override
    public Optional<RE> edgeConnecting(N pN, N pN1) {
        return Optional.empty();
    }

    @Override
    public Optional<RE> edgeConnecting(EndpointPair<N> pEndpointPair) {
        return Optional.empty();
    }

    @Nullable
    @Override
    public RE edgeConnectingOrNull(N pN, N pN1) {
        return null;
    }

    @Nullable
    @Override
    public RE edgeConnectingOrNull(EndpointPair<N> pEndpointPair) {
        return null;
    }

    @Override
    public boolean hasEdgeConnecting(N pN, N pN1) {
        return false;
    }

    @Override
    public boolean hasEdgeConnecting(EndpointPair<N> pEndpointPair) {
        return false;
    }
}