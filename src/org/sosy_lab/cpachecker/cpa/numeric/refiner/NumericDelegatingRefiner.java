// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.numeric.refiner;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Refiner;
import org.sosy_lab.cpachecker.cpa.arg.AbstractARGBasedRefiner;
import org.sosy_lab.cpachecker.cpa.numeric.NumericCPA;
import org.sosy_lab.cpachecker.cpa.predicate.PredicateCPARefiner;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisPathInterpolator;
import org.sosy_lab.cpachecker.cpa.value.refiner.ValueAnalysisStrongestPostOperator;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ValueAnalysisFeasibilityChecker;
import org.sosy_lab.cpachecker.cpa.value.refiner.utils.ValueAnalysisPrefixProvider;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.refinement.FeasibilityChecker;
import org.sosy_lab.cpachecker.util.refinement.StrongestPostOperator;

/**
 * Refiner implementation that delegates to {@link ValueAnalysisPathInterpolator}, and if this
 * fails, optionally delegates also to {@link PredicateCPARefiner}.
 */
public abstract class NumericDelegatingRefiner implements Refiner {

  public static Refiner create(ConfigurableProgramAnalysis cpa)
      throws InvalidConfigurationException {
    NumericCPA numericCPA =
        CPAs.retrieveCPAOrFail(cpa, NumericCPA.class, NumericDelegatingRefiner.class);
    final Configuration config = numericCPA.getConfiguration();
    final LogManager logger = numericCPA.getLogger();
    final ShutdownNotifier shutdownNotifier = numericCPA.getShutdownNotifier();
    final CFA cfa = numericCPA.getCFA();

    final StrongestPostOperator<ValueAnalysisState> strongestPostOp =
        new ValueAnalysisStrongestPostOperator(logger, Configuration.defaultConfiguration(), cfa);

    final FeasibilityChecker<ValueAnalysisState> feasibilityChecker =
        new ValueAnalysisFeasibilityChecker(strongestPostOp, logger, cfa, config);

    final ValueAnalysisPathInterpolator interpolatingRefiner =
        new ValueAnalysisPathInterpolator(
            feasibilityChecker,
            strongestPostOp,
            new ValueAnalysisPrefixProvider(logger, cfa, config, shutdownNotifier),
            config,
            logger,
            shutdownNotifier,
            cfa);

    NumericARGBasedDelegatingRefiner refiner =
        new NumericARGBasedDelegatingRefiner(
            config,
            logger,
            shutdownNotifier,
            cfa,
            numericCPA.getManagerSupplier().get(),
            numericCPA.getTransferRelation(),
            feasibilityChecker,
            interpolatingRefiner);
    return AbstractARGBasedRefiner.forARGBasedRefiner(refiner, cpa);
  }
}
