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
     * @param pRE the newly added relation edge, which must not be null,
     *             and must be valid and must connect <code>pN</code> to <code>pN1</code>.
     * @return true if the graph is modified.
     */
    @Override
    public boolean addEdge(N pN, N pN1, RE pRE) {
        assertNotNull(pN);
        assertNotNull(pN1);
        assertNotNull(pRE);
        assert containsNode(pN) && containsNode(pN1);
        assert pRE.valid() && pRE.connects(pN, pN1);

        if (!containsEdge(pRE)) {
            adjStartList.get(pN).add(pRE);
            adjEndList.get(pN1).add(pRE);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add a directed relation edge from one node to the other in the given pair of nodes,
     * if the edge is not present.
     * We just call {@link RelationGraph#addEdge(Object, Object, RelationEdge)} to solve the problem.
     * @param pEndpointPair the given pair of nodes, whose first element is starting node
     *                     and second element is ending point. It must not be null.
     * @param pRE the newly added relation edge, which must not be null,
     *            and must be valid and must connect two nodes in <code>pEndpointPair</code>.
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
            for (RE relationEdge : adjStartList.get(pN)) {
                removeEdge(relationEdge);
            }
            for (RE relationEdge : adjEndList.get(pN)) {
                removeEdge(relationEdge);
            }
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
     * @param pRE the relation edge to be removed, which must not be null and must be valid.
     * @return true if the graph is modified.
     */
    @Override
    public boolean removeEdge(RE pRE) {
        assertNotNull(pRE);
        assert pRE.valid();

        N startNode = pRE.getStartNode();
        N endNode = pRE.getEndNode();
        if (containsEdge(pRE)) {
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
    @Deprecated
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
    @Deprecated
    @Override
    public ElementOrder<N> nodeOrder() {
        return null;
    }

    /**
     * We don't use this method.
     */
    @Deprecated
    @Override
    public ElementOrder<RE> edgeOrder() {
        return null;
    }

    /**
     * Get the adjacent nodes of a given node.
     * We just call <code>predecessors()</code> and <code>successors()</code> to solve the problem.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of adjacent nodes of <code>pN</code>.
     */
    @Override
    public Set<N> adjacentNodes(N pN) {
        assertNotNull(pN);

        Set<N> adjNodes = predecessors(pN);
        adjNodes.addAll(successors(pN));
        return adjNodes;
    }

    /**
     * Get the predecessors of a given node, i.e. the nodes that point to the given node.
     * @param pN the given node, which must not be null and must be in the graph.
     * @return the set of predecessors of <code>pN</code>.
     */
    @Override
    public Set<N> predecessors(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        Set<N> adjNodes = new HashSet<>();
        for (RE relationEdge : inEdges(pN)) {
            adjNodes.add(relationEdge.getStartNode());
        }
        return adjNodes;
    }

    /**
     * Get the successors of a given node, i.e. the nodes that the given node points to.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of successors of <code>pN</code>.
     */
    @Override
    public Set<N> successors(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        Set<N> adjNodes = new HashSet<>();
        for (RE relationEdge : outEdges(pN)) {
            adjNodes.add(relationEdge.getEndNode());
        }
        return adjNodes;
    }

    /**
     * Get the relation edges that a given node connects with.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the union set of {@link RelationGraph#inEdges} and {@link RelationGraph#outEdges}.
     */
    @Override
    public Set<RE> incidentEdges(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        Set<RE> incidentEdges = new HashSet<>();
        incidentEdges.addAll(inEdges(pN));
        incidentEdges.addAll(outEdges(pN));
        return incidentEdges;
    }

    /**
     * Get the relation edges that end at a given node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of in-edges of <code>pN</code>.
     */
    @Override
    public Set<RE> inEdges(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        Set<RE> inEdges = new HashSet<>();
        inEdges.addAll(adjEndList.get(pN));
        return inEdges;
    }

    /**
     * Get the relation edges that start from a given node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the set of out-edges of <code>pN</code>.
     */
    @Override
    public Set<RE> outEdges(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        Set<RE> outEdges = new HashSet<>();
        outEdges.addAll(adjStartList.get(pN));
        return outEdges;
    }

    /**
     * Count the degree of a given node, which equals to in-degree + out-degree,
     * where self loop is counted twice.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the degree of <code>pN</code>.
     */
    @Override
    public int degree(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        return inDegree(pN) + outDegree(pN);
    }

    /**
     * Count the in-degree of a given node, i.e. the number of edges that end at the node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the in-degree of <code>pN</code>.
     */
    @Override
    public int inDegree(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        return inEdges(pN).size();
    }

    /**
     * Count the out-degree of a given node, i.e. the number of edges that start from the node.
     * @param pN the given node, which must not be null and must be in this graph.
     * @return the out-degree of <code>pN</code>.
     */
    @Override
    public int outDegree(N pN) {
        assertNotNull(pN);
        assert containsNode(pN);

        return outEdges(pN).size();
    }

    /**
     * Get the nodes that connect with a given relation edge.
     * <p></p>
     * We don't use this method, since we can simply call {@link RelationEdge#getStartNode}
     * and {@link RelationEdge#getEndNode}.
     */
    @Deprecated
    @Override
    public EndpointPair<N> incidentNodes(RE pRE) {
        return null;
    }

    /**
     * Get the relation edges that are adjacent to the given relation edge,
     * i.e. they share at least a common node.
     * <p></p>
     * Note that a relation edge is not adjacent to itself.
     * @param pRE the given relation edge, which must not be null and must be valid,
     *            and must be in the graph.
     * @return the set of adjacent edges of <code>pRE</code>.
     */
    @Override
    public Set<RE> adjacentEdges(RE pRE) {
        assertNotNull(pRE);
        assert pRE.valid();
        assert containsEdge(pRE);

        N startNode = pRE.getStartNode();
        N endNode = pRE.getEndNode();

        Set<RE> adjEdges = new HashSet<>();
        adjEdges.addAll(incidentEdges(startNode));
        adjEdges.addAll(incidentEdges(endNode));
        adjEdges.remove(pRE);
        return adjEdges;
    }

    /**
     * Get the relation edges that start from one given node and end at the other given node.
     * @param pN the expected starting node, which must not be null and must be in this graph.
     * @param pN1 the expected ending node, which must not be null and must be in this graph.
     * @return the set of relation edges from <code>pN</code> to <code>pN1</code>.
     */
    @Override
    public Set<RE> edgesConnecting(N pN, N pN1) {
        assertNotNull(pN);
        assertNotNull(pN1);
        assert containsNode(pN) && containsNode(pN);

        Set<RE> edges = new HashSet<>();
        for (RE outEdge : outEdges(pN)) {
            if (outEdge.endsAt(pN1)) {
                edges.add(outEdge);
            }
        }
        return edges;
    }

    /**
     * Get the relation edges that start from the first node and
     * end at the second node of the given pair of nodes.
     * <p></p>
     * We just call {@link RelationGraph#edgesConnecting(Object, Object)} to solve this problem.
     * @param pEndpointPair the given pair of nodes, whose first element is starting node
     *                      and second element is ending point. It must not be null.
     * @return the set of relation edges from <code>pEndpointPair.nodeU()</code>
     *         to <code>pEndpointPair.nodeV()</code>.
     */
    @Override
    public Set<RE> edgesConnecting(EndpointPair<N> pEndpointPair) {
        assertNotNull(pEndpointPair);

        return edgesConnecting(pEndpointPair.nodeU(), pEndpointPair.nodeV());
    }

    /**
     * We don't use this method.
     * Please use {@link RelationGraph#edgesConnecting(Object, Object)}.
     */
    @Deprecated
    @Override
    public Optional<RE> edgeConnecting(N pN, N pN1) {
        return Optional.empty();
    }

    /**
     * We don't use this method.
     * Please use {@link RelationGraph#edgesConnecting(EndpointPair)}.
     */
    @Deprecated
    @Override
    public Optional<RE> edgeConnecting(EndpointPair<N> pEndpointPair) {
        return Optional.empty();
    }

    /**
     * We don't use this method.
     * Please use {@link RelationGraph#edgesConnecting(Object, Object)}.
     */
    @Deprecated
    @Nullable
    @Override
    public RE edgeConnectingOrNull(N pN, N pN1) {
        return null;
    }

    /**
     * We don't use this method.
     * Please use {@link RelationGraph#edgesConnecting(EndpointPair)}.
     */
    @Deprecated
    @Nullable
    @Override
    public RE edgeConnectingOrNull(EndpointPair<N> pEndpointPair) {
        return null;
    }

    /**
     * Check whether there is any relation edge that starts from one given node and
     * ends at the other given node.
     * @param pN the expected starting node, which must not be null and must be in this graph.
     * @param pN1 the expected ending node, which must not be null and must be in this graph.
     * @return true if there exist a relation edge from <code>pN</code> to <code>pN1</code>.
     */
    @Override
    public boolean hasEdgeConnecting(N pN, N pN1) {
       assertNotNull(pN);
       assertNotNull(pN1);
       assert containsNode(pN) && containsNode(pN1);

       return edgesConnecting(pN, pN1).size() > 0;
    }

    /**
     * Check whether there is any relation edge that starts from the first node and
     * ends at the second node of the given pair of nodes.
     * <p></p>
     * We just call {@link RelationGraph#hasEdgeConnecting(Object, Object)} to solve this problem.
     * @param pEndpointPair the given pair of nodes, whose first element is starting node
     *                      and second element is ending point. It must not be null.
     * @return true if there exist a relation edge from <code>pEndpointPair.nodeU()</code>
     *         to <code>pEndpointPair.nodeV()</code>.
     */
    @Override
    public boolean hasEdgeConnecting(EndpointPair<N> pEndpointPair) {
        assertNotNull(pEndpointPair);

        return hasEdgeConnecting(pEndpointPair.nodeU(), pEndpointPair.nodeV());
    }

    /**
     * Check whether this graph contains a given node.
     * @param pN the given node, which must not be null.
     * @return true if this graph contains <code>pN</code>.
     */
    public boolean containsNode(N pN) {
        assertNotNull(pN);

        return adjStartList.containsKey(pN);
    }

    /**
     * Check whether this graph contains a given relation edge.
     * @param pRE the given relation edge, which must not be null and must be valid.
     * @return true if this graph contains <code>pRE</code>.
     */
    public boolean containsEdge(RE pRE) {
        assertNotNull(pRE);
        assert pRE.valid();

        N startNode = pRE.getStartNode();
        if (adjStartList.containsKey(startNode)
            && adjStartList.get(startNode).contains(pRE)) {
            return true;
        } else {
            return false;
        }
    }
}