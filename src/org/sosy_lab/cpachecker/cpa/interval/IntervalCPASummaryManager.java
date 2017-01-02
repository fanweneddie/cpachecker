/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.interval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.summary.blocks.Block;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.Summary;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.SummaryManager;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;

/**
 * Summary manager for the interval CPA.
 */
public class IntervalCPASummaryManager implements SummaryManager {

  private final LogManager logger;

  IntervalCPASummaryManager(LogManager pLogger) {
    logger = pLogger;
  }

  @Override
  public List<AbstractState> getAbstractSuccessorsForSummary(
      AbstractState pFunctionCallState,
      Precision pFunctionCallPrecision,
      List<Summary> pSummaries,
      Block pBlock,
      CFANode pCallSite)
      throws CPAException, InterruptedException {

    List<AbstractState> out = new ArrayList<>(pSummaries.size());
    for (Summary s : pSummaries) {
      out.add(getAbstractSuccessorForSummary(
          pFunctionCallState, (IntervalSummary) s
      ));
    }
    return out;
  }

  private IntervalAnalysisState getAbstractSuccessorForSummary(
      AbstractState pFunctionCallState,
      IntervalSummary iSummary) throws CPATransferException, InterruptedException {
    // todo: remove all vars modified inside the block.
    IntervalAnalysisState copy = IntervalAnalysisState.copyOf(
        (IntervalAnalysisState) pFunctionCallState);

    IntervalAnalysisState joinedState = iSummary.getStateAtJoin();

    joinedState.getIntervalMap().forEach(
        (var, interval) -> copy.addInterval(var, interval, -1)
    );

    logger.log(Level.INFO, "Postcondition after application of the summary\n",
        iSummary, "to state\n", pFunctionCallState, "is\n", copy);
    return copy;
  }

  @Override
  public AbstractState getWeakenedCallState(
      AbstractState pCallState, Precision pPrecision, CFANode pCallNode, Block pBlock) {
    IntervalAnalysisState iState = (IntervalAnalysisState) pCallState;
    IntervalAnalysisState clone = IntervalAnalysisState.copyOf(iState);

    Set<String> readVarNames = pBlock.getReadVariablesForCallEdge(
        findEnteringEdge(pCallNode, pBlock.getStartNode())
    ).stream()
        .map(w -> w.get().getQualifiedName()).collect(Collectors.toSet());

    logger.log(Level.INFO, "Vars read inside the block", pBlock, "are: ", readVarNames);

    iState.getIntervalMap().keySet().stream()
        .filter(v -> !readVarNames.contains(v))
        .forEach(v -> clone.removeInterval(v));
    logger.log(Level.INFO, "Weakened ", iState, " to ", clone);
    return clone;
  }

  private CFAEdge findEnteringEdge(CFANode callNode, CFANode entryNode) {
    return IntStream.range(0, callNode.getNumLeavingEdges())
        .mapToObj(i -> callNode.getLeavingEdge(i))
        .filter(e -> e.getSuccessor() == entryNode)
        .findAny().get();
  }

  @Override
  public List<? extends Summary> generateSummaries(
      AbstractState pCallState,
      Precision pCallPrecision,
      List<? extends AbstractState> pJoinStates,
      List<Precision> pJoinPrecisions,
      CFANode pEntryNode,
      Block pBlock
  ) {
    IntervalAnalysisState iCallstackState = (IntervalAnalysisState) pCallState;

    assert !pJoinStates.isEmpty();
    Stream<IntervalAnalysisState> stream =
        pJoinStates.stream().map(s -> (IntervalAnalysisState) s);

    Optional<IntervalAnalysisState> out = stream.reduce((a, b) -> a.join(b));
    return Collections.singletonList(new IntervalSummary(iCallstackState, out.get()));
  }

  @Override
  public IntervalSummary merge(
      Summary pSummary1,
      Summary pSummary2) throws CPAException, InterruptedException {

    IntervalSummary iSummary1 = (IntervalSummary) pSummary1;
    IntervalSummary iSummary2 = (IntervalSummary) pSummary2;
    return new IntervalSummary(
        iSummary1.getStateAtCallsite().join(iSummary2.getStateAtCallsite()),
        iSummary2.getStateAtJoin().join(iSummary2.getStateAtJoin())
    );
  }

  @Override
  public boolean isDescribedBy(Summary pSummary1, Summary pSummary2) {
    IntervalSummary iSummary1 = (IntervalSummary) pSummary1;
    IntervalSummary iSummary2 = (IntervalSummary) pSummary2;

    return iSummary1.getStateAtCallsite().isLessOrEqual(
        iSummary2.getStateAtCallsite()
    ) && iSummary2.getStateAtJoin().isLessOrEqual(
        iSummary1.getStateAtJoin()
    );
  }

  @Override
  public boolean isSummaryApplicableAtCallsite(Summary pSummary, AbstractState pCallsite) {
    IntervalAnalysisState iState = (IntervalAnalysisState) pCallsite;
    IntervalSummary iSummary = (IntervalSummary) pSummary;

    return iState.isLessOrEqual(iSummary.getStateAtCallsite());
  }

  private static class IntervalSummary implements Summary {

    /**
     * Intervals over parameters, read global variables.
     */
    private final IntervalAnalysisState stateAtCallsite;

    /**
     * Intervals over returned variable, changed global variables.
     */
    private final IntervalAnalysisState stateAtJoin;

    private IntervalSummary(
        IntervalAnalysisState pStateAtCallsite,
        IntervalAnalysisState pStateAtJoin) {
      stateAtCallsite = pStateAtCallsite;
      stateAtJoin = pStateAtJoin;
    }

    IntervalAnalysisState getStateAtCallsite() {
      return stateAtCallsite;
    }

    IntervalAnalysisState getStateAtJoin() {
      return stateAtJoin;
    }

    @Override
    public boolean equals(@Nullable Object pO) {
      if (this == pO) {
        return true;
      }
      if (pO == null || getClass() != pO.getClass()) {
        return false;
      }
      IntervalSummary that = (IntervalSummary) pO;
      return Objects.equals(stateAtCallsite, that.stateAtCallsite) &&
          Objects.equals(stateAtJoin, that.stateAtJoin);
    }

    @Override
    public int hashCode() {
      return Objects.hash(stateAtCallsite, stateAtJoin);
    }

    @Override
    public String toString() {
      return "IntervalSummary{stateAtJoin=" + stateAtJoin + '}';
    }
  }
}
