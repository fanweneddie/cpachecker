// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.string;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.java.JStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AbstractCPA;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.DelegateAbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.StateSpacePartition;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.EdgeCollectingCFAVisitor;
import org.sosy_lab.cpachecker.util.CFAUtils;

@Options(prefix = "cpa.string")
public class StringCPA extends AbstractCPA {

  @Option(
    secure = true,
    name = "merge",
    toUppercase = true,
    values = {"SEP", "JOIN"},
    description = "which merge operator to use for StringCPA")
  private String mergeType = "SEP";

  @Option(
    secure = true,
    name = "stop",
    toUppercase = true,
    values = {"SEP", "JOIN"},
    description = "which stop operator to use for StringCPA")
  private String stopType = "SEP";

  private Configuration config;
  private StringOptions options;
  private final LogManager logger;
  private final CFA cfa;

  private StringCPA(Configuration pConfig, LogManager pLogger, CFA pCfa)
      throws InvalidConfigurationException {
    super(
        DelegateAbstractDomain.<StringState>getInstance(),
        null);
    this.config = pConfig;
    this.logger = pLogger;
    this.cfa = pCfa;
    options = new StringOptions(pConfig, getAllStringLiterals());
    config.inject(this, StringCPA.class);
    getMergeOperator();
    getStopOperator();
  }

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(StringCPA.class);
  }

  @Override
  public StringTransferRelation getTransferRelation() {
    return new StringTransferRelation(logger, options);
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
  public AbstractState getInitialState(CFANode pNode, StateSpacePartition pPartition) {
    return new StringState(ImmutableMap.of(), options);
  }

  @Override
  public AbstractDomain getAbstractDomain() {
    return DelegateAbstractDomain.<StringState>getInstance();
  }

  private ImmutableSet<String> getAllStringLiterals() {
    ImmutableSet.Builder<String> builder = new ImmutableSet.Builder<>();
    EdgeCollectingCFAVisitor edgeVisitor = new CFATraversal.EdgeCollectingCFAVisitor();
    CFATraversal.dfs().traverseOnce(cfa.getMainFunction(), edgeVisitor);
    for (CFAEdge edge : edgeVisitor.getVisitedEdges()) {
      Optional<AAstNode> optNode = edge.getRawAST();
      if (optNode.isPresent()) {
        AAstNode aNode = optNode.get();
        CFAUtils.traverseRecursively(aNode)
          .filter(JStringLiteralExpression.class)
          .forEach(jexp -> builder.add(jexp.getValue()));
      }
    }
    return builder.build();
  }
}
