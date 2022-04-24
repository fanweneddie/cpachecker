/*
This file is part of CPAchecker,
a tool for configurable software verification:
https://cpachecker.sosy-lab.org

Revised by fanweneddie in 2022
In order to do string analysis on Java program.

SPDX-License-Identifier: Apache-2.0
*/

package org.sosy_lab.cpachecker.cpa.string;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.cpa.string.StringRelationAnalysisState.StringRelationLabel;
import org.sosy_lab.cpachecker.util.graph.RelationEdge;
import org.sosy_lab.cpachecker.util.graph.RelationGraph;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

@Options(prefix = "cpa.string")
public class StringRelationAnalysisCPA extends AbstractCPA {
    private final Configuration config;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;
    private final CFA cfa;

    public StringRelationAnalysisCPA(Configuration pConfig, LogManager pLogger,
                                     ShutdownNotifier pShutdownNotifier, CFA pCfa) {
        // Todo: create abstract domain for super class
        super(null, null);
        this.config           = pConfig;
        this.logger           = pLogger;
        this.shutdownNotifier = pShutdownNotifier;
        this.cfa              = pCfa;
    }

    @Override
    public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
        return new StringRelationAnalysisState(
            (RelationGraph<MemoryLocation, StringRelationLabel, RelationEdge<MemoryLocation, StringRelationLabel>>) null);
    }
}