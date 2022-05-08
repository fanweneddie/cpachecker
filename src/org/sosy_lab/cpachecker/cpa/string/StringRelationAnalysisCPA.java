/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.cpa.value.ValueAnalysisState;

@Options(prefix = "cpa.string")
public class StringRelationAnalysisCPA extends AbstractCPA {

    @Option(secure=true, name="merge", toUppercase=true, values={"SEP", "JOIN"},
        description="which merge operator to use for StringRelationAnalysisCPA")
    private final String mergeType = "SEP";

    @Option(
        secure = true,
        name = "stop",
        toUppercase = true,
        values = {"SEP", "JOIN", "NEVER", "EQUALS"},
        description = "which stop operator to use for StringRelationAnalysisCPA")
    private final String stopType = "SEP";

    private final Configuration config;
    private final LogManager logger;
    private final CFA cfa;

    public StringRelationAnalysisCPA(Configuration pConfig,
                                     LogManager pLogger,
                                     CFA cCfa) throws InvalidConfigurationException  {
        super(DelegateAbstractDomain.<ValueAnalysisState>getInstance(), null);
        this.config           = pConfig;
        this.logger           = pLogger;
        this.cfa              = cCfa;
    }

    public static CPAFactory factory() {
        return AutomaticCPAFactory.forType(StringRelationAnalysisCPA.class);
    }

    @Override
    public MergeOperator getMergeOperator() {
        return buildMergeOperator(mergeType);
    }

    @Override
    public StopOperator getStopOperator() {
        return buildStopOperator(stopType);
    }

    @Override
    public StringRelationAnalysisTransferRelation getTransferRelation() {
        return new StringRelationAnalysisTransferRelation();
    }

    @Override
    public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
        return new StringRelationAnalysisState();
    }
}