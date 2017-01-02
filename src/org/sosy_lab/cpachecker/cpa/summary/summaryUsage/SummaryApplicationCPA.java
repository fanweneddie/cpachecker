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
package org.sosy_lab.cpachecker.cpa.summary.summaryUsage;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.cpa.summary.blocks.Block;
import org.sosy_lab.cpachecker.cpa.summary.blocks.BlockManager;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.Summary;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.SummaryManager;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.UseSummaryCPA;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.AbstractStates;

/**
 * Guiding the summary computation.
 *
 * <p>Operates over wrapped states directly.
 */
public class SummaryApplicationCPA implements ConfigurableProgramAnalysis,
                                              PrecisionAdjustment,
                                              AbstractDomain,
                                              TransferRelation,
                                              StatisticsProvider {

  private final UseSummaryCPA wrapped;
  private final Multimap<String, Summary> summaryMapping;
  private final SummaryManager wrappedSummaryManager;
  private final AbstractDomain wrappedAbstractDomain;
  private final TransferRelation wrappedTransferRelation;
  private final PrecisionAdjustment wrappedPrecisionAdjustment;
  private final LogManager logger;
  private final MergeOperator wrappedMergeOperator;
  private final StopOperator wrappedStopOperator;
  private final List<SummaryComputationRequest> summaryComputationRequests;
  private final BlockManager blockManager;

  public SummaryApplicationCPA(
      ConfigurableProgramAnalysis pWrapped,
      Multimap<String, Summary> pSummaryMapping,
      BlockManager pBlockManager,
      LogManager pLogger) throws InvalidConfigurationException, CPATransferException {
    summaryMapping = pSummaryMapping;
    Preconditions.checkArgument(pWrapped instanceof UseSummaryCPA,
        "Top-level CPA for summary computation has to implement UseSummaryCPA.");
    wrapped = (UseSummaryCPA) pWrapped;
    wrappedSummaryManager = wrapped.getSummaryManager();
    wrappedAbstractDomain = wrapped.getAbstractDomain();
    wrappedTransferRelation = wrapped.getTransferRelation();
    wrappedPrecisionAdjustment = wrapped.getPrecisionAdjustment();
    wrappedMergeOperator = wrapped.getMergeOperator();
    wrappedStopOperator = wrapped.getStopOperator();
    logger = pLogger;
    summaryComputationRequests = new ArrayList<>();
    blockManager = pBlockManager;
  }

  public List<SummaryComputationRequest> getSummaryComputationRequests() {
    return summaryComputationRequests;
  }

  @Override
  public AbstractState getInitialState(
      CFANode node, StateSpacePartition partition) throws InterruptedException {
    return wrapped.getInitialState(node, partition);
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessors(
      AbstractState state, Precision precision) throws CPATransferException, InterruptedException {

    try {
      return getAbstractSuccessors0(state, precision);
    } catch (CPAException pE) {
      throw new CPATransferException("Exception occurred", pE);
    }
  }

  private Collection<? extends AbstractState> getAbstractSuccessors0(
      AbstractState state, Precision precision) throws CPAException, InterruptedException {

    CFANode node = AbstractStates.extractLocation(state);
    Block block = blockManager.getBlockForNode(node);
    Optional<Block> blockToEnter = blockManager.blockToEnter(node);

    if (block.getExitNode() == node) {

      logger.log(Level.INFO, "Leaving the block '",
          block.getName(), "', not computing the successors.");
      return Collections.emptyList();

    } else if (blockToEnter.isPresent()) {

      // Attempt to calculate a postcondition using summaries
      // we have.
      // If our summaries do not cover the callsite request the generation
      // of the new ones.
      return applySummaries(node, state, precision, blockToEnter.get());
    } else {

      // Simply delegate.
      return wrappedTransferRelation.getAbstractSuccessors(state, precision);
    }
  }

  public Collection<? extends AbstractState> getDelegatedSuccessors(
      AbstractState pState, Precision pPrecision) throws CPATransferException, InterruptedException {
    return wrappedTransferRelation.getAbstractSuccessors(pState, pPrecision);
  }

  /**
   * @param pCallsite State <b>outside</b> of the called block,
   *                  from where it was currently called.
   * @param pPrecision Precision associated with the state {@code pCallsite}.
   * @param pBlock Block we are just outside of (one more transition should make it inside).
   */
  private Collection<? extends AbstractState> applySummaries(
      CFANode pCallNode,
      AbstractState pCallsite,
      Precision pPrecision,
      Block pBlock
  ) throws CPAException, InterruptedException {

    String calledFunctionName = pBlock.getName();
    Collection<Summary> summaries = summaryMapping.get(calledFunctionName);
    List<Summary> matchingSummaries = new ArrayList<>();

    // Weaken the call state.
    AbstractState weakenedCallState = wrappedSummaryManager.getWeakenedCallState(
        pCallsite, pPrecision, pCallNode, pBlock
    );

    // We can return multiple postconditions, one for each matching summary.
    for (Summary summary : summaries) {
      if (wrappedSummaryManager.isSummaryApplicableAtCallsite(summary, weakenedCallState)) {
        matchingSummaries.add(summary);
      }
    }

    if (matchingSummaries.isEmpty()) {
      logger.log(Level.INFO, "No matching summary found for '",
          calledFunctionName, "', requesting summary computation. Assuming for now the call is "
              + "unreachable.");

      // Generate the state associated with the function entry.
      Collection<? extends AbstractState> entryState =
          wrappedTransferRelation.getAbstractSuccessors(weakenedCallState, pPrecision);
      Preconditions.checkState(entryState.size() == 1,
          "Processing function call edge should create a unique successor");

      // Communicate the desire to recompute the summary.
      summaryComputationRequests.add(new SummaryComputationRequest(
          pCallsite,
          entryState.iterator().next(),
          pPrecision,
          pBlock));
      return Collections.emptyList();
    } else {
      logger.log(Level.INFO, "Found matching summaries", matchingSummaries);
      Collection<? extends AbstractState> out = wrappedSummaryManager.getAbstractSuccessorsForSummary(
          pCallsite, pPrecision, matchingSummaries, pBlock, AbstractStates.extractLocation(pCallsite));
      logger.log(Level.INFO, "Successors of the state", pCallsite, "after summary application "
          + "are\n\n", out);
      return out;
    }
  }

  @Override
  public Optional<PrecisionAdjustmentResult> prec(
      AbstractState state,
      Precision precision,
      UnmodifiableReachedSet states,
      Function<AbstractState, AbstractState> stateProjection,
      AbstractState fullState) throws CPAException, InterruptedException {

   return wrappedPrecisionAdjustment.prec(
        state, precision, states, stateProjection, fullState);
  }


  @Override
  public TransferRelation getTransferRelation() {
    return this;
  }

  @Override
  public MergeOperator getMergeOperator() {
    return wrappedMergeOperator;
  }

  @Override
  public StopOperator getStopOperator() {
    return wrappedStopOperator;
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return this;
  }


  @Override
  public Precision getInitialPrecision(
      CFANode node, StateSpacePartition partition) throws InterruptedException {
    return wrapped.getInitialPrecision(node, partition);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return this;
  }

  @Override
  public boolean isLessOrEqual(
      AbstractState state1, AbstractState state2) throws CPAException, InterruptedException {
    return wrappedAbstractDomain.isLessOrEqual(state1, state2);
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (wrapped instanceof StatisticsProvider) {
      ((StatisticsProvider) wrapped).collectStatistics(pStatsCollection);
    }
  }

  public SummaryManager getSummaryManager() {
    return wrappedSummaryManager;
  }

  @Override
  public AbstractState join(
      AbstractState state1, AbstractState state2) throws CPAException, InterruptedException {
    throw new UnsupportedOperationException("Unexpected API call");
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(
      AbstractState state, Precision precision, CFAEdge cfaEdge)
      throws CPATransferException, InterruptedException {
    throw new UnsupportedOperationException("Unexpected API Call");
  }
}
