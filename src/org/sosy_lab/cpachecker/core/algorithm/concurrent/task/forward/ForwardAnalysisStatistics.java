// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.concurrent.task.forward;

import org.sosy_lab.cpachecker.cfa.blockgraph.Block;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.ConcurrentStatisticsCollector;
import org.sosy_lab.cpachecker.core.algorithm.concurrent.ConcurrentStatisticsCollector.TaskStatistics;

public class ForwardAnalysisStatistics implements TaskStatistics {
  private final Block target;
  
  public ForwardAnalysisStatistics(final Block pTarget) {
    target = pTarget;
  }
  
  public Block getTarget() {
    return target;
  }
  
  @Override
  public void accept(final ConcurrentStatisticsCollector collector) {
    collector.visit(this);
  }
}