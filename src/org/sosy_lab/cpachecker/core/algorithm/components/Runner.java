// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.components;

import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.components.tree.BlockNode;
import org.sosy_lab.cpachecker.core.algorithm.components.tree.BlockTree;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView;
import org.sosy_lab.java_smt.api.BooleanFormula;

public class Runner {

  private final BlockNode block;
  private final Algorithm analysis;
  private final LogManager logger;
  private final BooleanFormulaManagerView bmgr;

  private Runner(BlockNode pBlock, Algorithm pAlgorithm, LogManager pLogger) {
    block = pBlock;
    analysis = pAlgorithm;
    logger = pLogger;
    bmgr = block.getBmgr();
  }

  public static Runner on(BlockNode node, Algorithm algorithm, LogManager logger) {
    return new Runner(node, algorithm, logger);
  }

  public static void analyzeTree(BlockTree tree, Algorithm algorithm, LogManager logger) {
    tree.getDistinctNodes().forEach(node -> on(node, algorithm, logger).analyzeBlock());
  }

  public void analyzeBlock() {
    logger.log(Level.INFO, block);
    BooleanFormula precondition = block.getPreconditionUpdates().values().stream().collect(bmgr.toDisjunction());

    block.setPostcondition(bmgr.makeTrue());
    block.sendPostconditionToSuccessors();
  }
}
