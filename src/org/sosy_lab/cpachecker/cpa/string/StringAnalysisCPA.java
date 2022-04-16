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

@Options(prefix = "cpa.string")
public class StringAnalysisCPA extends AbstractCPA {
    private final Configuration config;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;
    private final CFA cfa;

    public StringAnalysisCPA((Configuration pConfig, LogManager pLogger,
                              ShutdownNotifier pShutdownNotifier, CFA pCfa) {
        // Todo: create abstract domain for super class
        super();
        this.config           = pConfig;
        this.logger           = pLogger;
        this.shutdownNotifier = pShutdownNotifier;
        this.cfa              = pCfa;
    }

    @Override
    public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
        return new StringAnalysisState();
    }
}