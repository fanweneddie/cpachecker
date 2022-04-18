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
import java.util.Collection;
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
 * which contains two adjacent lists, with one storing edges starting from each node
 * and the other storing edges ending at each node.
 * <p></p>
 * @param <N> the type of node in the graph.
 * @param <L> the type of label in the edge.
 * @param <RE> the type of relation edge in the graph.
 */
public class RelationGraph<N, L, RE extends RelationEdge<N, L>>
    implements MutableNetwork<N, RE> {

    /**
     * Adjacent list to represent the graph,
     * which stores the relation edges starting from each node
     * <p></p>
     * e.g. if e connects n1 to n2, then <code>adjStartList</code>[n1] contains e
     */
    private final HashMap<N, HashSet<RE>> adjStartList;

    /**
     * Adjacent list to represent the graph,
     * which stores the relation edges ending at each node
     * <p></p>
     * e.g. if e connects n1 to n2, then <code>adjStartList</code>[n2] contains e
     */
    private final HashMap<N, HashSet<RE>> adjEndList;

    public RelationGraph() {
        this.adjStartList = new HashMap<>();
        this.adjEndList = new HashMap<>();
    }

    /**
     * Add a node into the graph, if it is not present.
     * <p></p>
     * Note that <code>adjStartList</code> and <code>adjEndList</code> should have consistent nodes and relation edges.
     * <p></p>
     * @param pN the node to be added, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addNode(N pN) {
        assertNotNull(pN);

        if (!containsNode(pN)) {
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
     * Also note that <code>adjStartList</code> and <code>adjEndList</code> should have consistent nodes and relation edges.
     * <p></p>
     * @param pN the starting node, which must not be null and must have been in graph.
     * @param pN1 the ending node, which must not be null and must have been in graph.
     * @param pRE the newly added relation edge, which must not be null
     *             and valid and must connect <code>pN</code> to <code>pN1</code>.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addEdge(N pN, N pN1, RE pRE) {
        assertNotNull(pN);
        assertNotNull(pN1);
        assertNotNull(pRE);
        assert containsNode(pN) && containsNode(pN1);
        assert pRE.valid() && pRE.connects(pN, pN1);

        if (!adjStartList.get(pN).contains(pRE)) {
            adjStartList.get(pN).add(pRE);
            adjEndList.get(pN1).add(pRE);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add a directed edge from one node to the other in the given <code>EndpointPair</code>,
     * if the edge is not present.
     * We just call <code>addEdge(N pN, N pN1, E pE)</code> to solve the problem.
     * <p></p>
     * @param pEndpointPair a pair of nodes, whose first element is starting node
     *                     and second element is ending point. It must not be null.
     * @param pRE the newly added edge.
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
     * Note that <code>adjStartList</code> and <code>adjEndList</code> should have consistent nodes and relation edges.
     * <p></p>
     * @param pN the node to be removed, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean removeNode(N pN) {
        assertNotNull(pN);

        if (containsNode(pN)) {
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
     * Note that <code>adjStartList</code> and <code>adjEndList</code> should have consistent nodes and relation edges.
     * <p></p>
     * @param pRE the edge to be removed, which must not be null.
     * @return true if the graph is modified.
     */
    @Override
    public boolean removeEdge(RE pRE) {
        assertNotNull(pRE);
        assert pRE.valid();

        N startNode = pRE.getStartNode();
        N endNode = pRE.getEndNode();
        if (containsNode(startNode)
            && adjStartList.get(startNode).contains(pRE)) {
            adjStartList.get(startNode).remove(pRE);
            adjEndList.get(endNode).remove(pRE);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get all nodes in the graph.
     * <p></p>
     * This lazy method is slow.
     */
    @Override
    public Set<N> nodes() {
        return adjStartList.keySet();
    }

    /**
     * Get all relation edges in the graph.
     * <p></p>
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
     * This graph is a directed graph.
     */
    @Override
    public boolean isDirected() {
        return true;
    }

    /**
     * This graph does not allow the same edge between two nodes.
     */
    @Override
    public boolean allowsParallelEdges() {
        return false;
    }

    /**
     * This graph allows self loop on a node.
     */
    @Override
    public boolean allowsSelfLoops() {
        return true;
    }

    /**
     * We don't use this method.
     */
    @Override
    public ElementOrder<N> nodeOrder() {
        return null;
    }

    /**
     * We don't use this method.
     */
    @Override
    public ElementOrder<RE> edgeOrder() {
        return null;
    }

    /**
     * Get the adjacent nodes of a given node.
     * We just call <code>predecessors()</code> and <code>successors()</code> to solve the problem.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of adjacent nodes of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public Set<N> adjacentNodes(N pN) {
        assertNotNull(pN);

        Set<N> adjNodes = predecessors(pN);
        adjNodes.addAll(successors(pN));
        return adjNodes;
    }

    /**
     * Get the predecessors of a given node.
     * @param pN the given node, which must not be null and must be in the graph.
     * @return the set of nodes that point to the given node.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public Set<N> predecessors(N pN) {
        assertNotNull(pN);

        // get the nodes that point to pN
        if (containsNode(pN)) {
            Set<N> adjNodes = new HashSet<>();
            for (RE relationEdge : adjEndList.get(pN)) {
                adjNodes.add(relationEdge.getStartNode());
            }
            return adjNodes;
        } else {
            throw new IllegalArgumentException("node must be in the graph");
        }
    }

    /**
     * Get the successors of a given node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of nodes that the given node points to.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public Set<N> successors(N pN) {
        assertNotNull(pN);

        // get the nodes that pN points to
        if (containsNode(pN)) {
            Set<N> adjNodes = new HashSet<>();
            for (RE relationEdge : adjStartList.get(pN)) {
                adjNodes.add(relationEdge.getEndNode());
            }
            return adjNodes;
        } else {
            throw new IllegalArgumentException("node must be in the graph");
        }
    }

    @Override
    public Set<RE> incidentEdges(N pN) {
        return null;
    }

    /**
     * Get the relation edges that end at a given node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of in-edges of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public Set<RE> inEdges(N pN) {
        assertNotNull(pN);

        // get the relation edges that ends at pN
        if (containsNode(pN)) {
            Set<RE> inEdges = new HashSet<>();
            inEdges.addAll(adjEndList.get(pN));
            return inEdges;
        } else {
            throw new IllegalArgumentException("node must be in the graph");
        }
    }

    /**
     * Get the relation edges that start from a given node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of out-edges of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public Set<RE> outEdges(N pN) {
        assertNotNull(pN);

        // get the relation edges that start from pN
        if (containsNode(pN)) {
            Set<RE> outEdges = new HashSet<>();
            outEdges.addAll(adjStartList.get(pN));
            return outEdges;
        } else {
            throw new IllegalArgumentException("node must be in the graph");
        }
    }

    /**
     * Count the degree of a given node, i.e. in-degree + out-degree,
     * where self loop is counted twice.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the degree of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public int degree(N pN) {
        assertNotNull(pN);

        return inDegree(pN) + outDegree(pN);
    }

    /**
     * Count the in-degree of a given node, i.e. the number of edges that end at the node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the in-degree of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public int inDegree(N pN) {
        assertNotNull(pN);

        return inEdges(pN).size();
    }

    /**
     * Count the out-degree of a given node, i.e. the number of edges that start from the node
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the out-degree of <code>pN</code>.
     * @throws <code>IllegalArgumentException</code> if <code>pN</code> is not in the graph.
     */
    @Override
    public int outDegree(N pN) {
        assertNotNull(pN);

        return outEdges(pN).size();
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

    /**
     * Check whether this graph contains a given node.
     * @param pN the given node, which must not be null
     * @return true if this graph contains the given node.
     */
    public boolean containsNode(N pN) {
        assertNotNull(pN);

        return nodes().contains(pN);
    }

    /**
     * Check whether this graph contains a given relation edge.
     * @param pRE the given relation edge, which must not be null and must be valid.
     * @return true if this graph contains the given relation edge
     */
    public boolean containsEdge(RE pRE) {
        assertNotNull(pRE);
        assert pRE.valid();

        return edges().contains(pRE);
    }
}