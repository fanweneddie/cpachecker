// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.postprocessing.summaries.loops;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.GhostCFA;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.StrategiesEnum;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.StrategyDependencies.StrategyDependencyInterface;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;

public class LoopUnrollingStrategy extends AbstractLoopStrategy {

  private Integer maxUnrollingsStrategy = 0;

  private StrategiesEnum strategyEnum;

  public LoopUnrollingStrategy(
      LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      int pMaxUnrollingsStrategy,
      StrategyDependencyInterface pStrategyDependencies,
      CFA pCFA) {
    super(pLogger, pShutdownNotifier, pStrategyDependencies, pCFA);
    maxUnrollingsStrategy = pMaxUnrollingsStrategy;
    strategyEnum = StrategiesEnum.LoopUnrolling;
  }

  protected Optional<GhostCFA> summarizeLoop(Loop pLoopStructure, CFANode pBeforeWhile) {

    CFANode startNodeGhostCFA = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());
    CFANode endNodeGhostCFA = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

    CFANode currentSummaryNodeCFA = startNodeGhostCFA;
    CFANode nextCFANode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

    for (int i = 0; i < maxUnrollingsStrategy; i++) {

      Optional<Pair<CFANode, CFANode>> unrolledLoopNodesMaybe =
          pLoopStructure.unrollOutermostLoop();
      if (unrolledLoopNodesMaybe.isEmpty()) {
        return Optional.empty();
      }

      CFANode startUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getFirst();
      CFANode endUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getSecond();

      currentSummaryNodeCFA.connectTo(startUnrolledLoopNode);
      endUnrolledLoopNode.connectTo(nextCFANode);

      currentSummaryNodeCFA = nextCFANode;
      nextCFANode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());
    }

    currentSummaryNodeCFA.connectTo(endNodeGhostCFA);

    CFANode leavingSuccessor;
    Iterator<CFAEdge> iter = pLoopStructure.getOutgoingEdges().iterator();
    if (iter.hasNext()) {
      leavingSuccessor = iter.next().getSuccessor();
    } else {
      return Optional.empty();
    }

    for (CFAEdge e : pLoopStructure.getOutgoingEdges()) {
      if (e.getSuccessor().getNodeNumber() != leavingSuccessor.getNodeNumber()) {
        return Optional.empty();
      }
    }

    return Optional.of(
        new GhostCFA(
            startNodeGhostCFA, endNodeGhostCFA, pBeforeWhile, pBeforeWhile, this.strategyEnum));
  }

  @Override
  public Optional<GhostCFA> summarize(final CFANode beforeWhile) {

    List<CFAEdge> filteredOutgoingEdges =
        this.summaryFilter.getEdgesForStrategies(
            beforeWhile.getLeavingEdges(),
            new HashSet<>(Arrays.asList(StrategiesEnum.Base, this.strategyEnum)));

    if (filteredOutgoingEdges.size() != 1) {
      return Optional.empty();
    }

    if (!filteredOutgoingEdges.get(0).getDescription().equals("while")) {
      return Optional.empty();
    }

    CFANode loopStartNode = filteredOutgoingEdges.get(0).getSuccessor();

    Optional<Loop> loopStructureMaybe = summaryInformation.getLoop(loopStartNode);
    if (loopStructureMaybe.isEmpty()) {
      return Optional.empty();
    }
    Loop loopStructure = loopStructureMaybe.orElseThrow();

    return summarizeLoop(loopStructure, beforeWhile);
  }

  @Override
  public boolean isPrecise() {
    return true;
  }
}
