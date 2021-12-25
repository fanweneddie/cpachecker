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
import org.sosy_lab.cpachecker.cfa.ast.AExpression;
import org.sosy_lab.cpachecker.cfa.ast.AVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.factories.AExpressionFactory;
import org.sosy_lab.cpachecker.cfa.ast.factories.TypeFactory;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.GhostCFA;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.StrategiesEnum;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.StrategyDependencies.StrategyDependencyInterface;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CStorageClass;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.LoopStructure.Loop;
import org.sosy_lab.cpachecker.util.Pair;

public class ConstantExtrapolationStrategy extends AbstractLoopExtrapolationStrategy {

  private StrategiesEnum strategyEnum;

  public ConstantExtrapolationStrategy(
      final LogManager pLogger,
      ShutdownNotifier pShutdownNotifier,
      StrategyDependencyInterface pStrategyDependencies,
      CFA pCFA) {
    super(pLogger, pShutdownNotifier, pStrategyDependencies, pCFA);

    this.strategyEnum = StrategiesEnum.LoopConstantExtrapolation;
  }

  protected Optional<GhostCFA> summarizeLoop(
      AExpression pIterations,
      AExpression pLoopBoundExpression,
      Loop pLoopStructure,
      CFANode pBeforeWhile) {

    CFANode startNodeGhostCFA = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());
    CFANode endNodeGhostCFA = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

    Optional<Pair<CFANode, CFANode>> unrolledLoopNodesMaybe = pLoopStructure.unrollOutermostLoop();
    if (unrolledLoopNodesMaybe.isEmpty()) {
      return Optional.empty();
    }

    CFANode startUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getFirst();
    CFANode endUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getSecond();

    startNodeGhostCFA.connectTo(startUnrolledLoopNode);

    CFANode currentSummaryNodeCFA = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

    CFAEdge loopBoundCFAEdge =
        new CAssumeEdge(
            "Loop Bound Assumption",
            FileLocation.DUMMY,
            endUnrolledLoopNode,
            currentSummaryNodeCFA,
            (CExpression) pLoopBoundExpression,
            true); // TODO: this may not be the correct way to do this; Review
    loopBoundCFAEdge.connect();

    CAssumeEdge negatedBoundCFAEdge =
        ((CAssumeEdge) loopBoundCFAEdge).negate().copyWith(endUnrolledLoopNode, endNodeGhostCFA);
    negatedBoundCFAEdge.connect();


    Optional<Pair<CFANode, AVariableDeclaration>> nextNodeAndIterationsVariable =
        createIterationsVariable(currentSummaryNodeCFA, pIterations, pBeforeWhile);

    if (nextNodeAndIterationsVariable.isEmpty()) {
      return Optional.empty();
    }

    currentSummaryNodeCFA = nextNodeAndIterationsVariable.orElseThrow().getFirst();
    AVariableDeclaration iterationsVariable = nextNodeAndIterationsVariable.orElseThrow().getSecond();

    CFANode nextSummaryNode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

    // Make Summary of Loop
    for (AVariableDeclaration var : pLoopStructure.getModifiedVariables()) {
      Optional<Integer> deltaMaybe = pLoopStructure.getDelta(var.getQualifiedName());
      if (deltaMaybe.isEmpty()) {
        return Optional.empty();
      }

      // Create a new tmp variable in order for the overflow check to work
      CVariableDeclaration newVariableForOverflows =
          new CVariableDeclaration(
              FileLocation.DUMMY,
              false,
              CStorageClass.AUTO,
              (CType) var.getType(),
              var.getName() + "TmpVariableReallyReallyTmp" + nameCounter,
              var.getOrigName() + "TmpVariableReallyReallyTmp" + nameCounter,
              var.getQualifiedName() + "::TmpVariableReallyReallyTmp" + nameCounter,
              null);

      CFAEdge newVarInitEdge =
          new CDeclarationEdge(
              newVariableForOverflows.toString(),
              FileLocation.DUMMY,
              currentSummaryNodeCFA,
              nextSummaryNode,
              newVariableForOverflows);
      newVarInitEdge.connect();

      currentSummaryNodeCFA = nextSummaryNode;
      nextSummaryNode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

      AExpressionFactory expressionFactory = new AExpressionFactory();
      CExpressionAssignmentStatement newVariableAssignmentExpression =
          (CExpressionAssignmentStatement)
              expressionFactory.from(var).assignTo(newVariableForOverflows);

      CFAEdge dummyEdge =
          new CStatementEdge(
              newVariableAssignmentExpression.toString(),
              newVariableAssignmentExpression,
              FileLocation.DUMMY,
              currentSummaryNodeCFA,
              nextSummaryNode);
      dummyEdge.connect();

      currentSummaryNodeCFA = nextSummaryNode;
      nextSummaryNode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

      // Make the extrapolation
      Integer delta = deltaMaybe.orElseThrow();

      CExpression leftHandSide =
          (CExpression)
              new AExpressionFactory()
                  .from(iterationsVariable)
                  .binaryOperation(
                      Integer.valueOf(1),
                      new CSimpleType(
                          false,
                          false,
                          CBasicType.INT,
                          true,
                          false,
                          true,
                          false,
                          false,
                          false,
                          false),
                      CBinaryExpression.BinaryOperator.MINUS)
                  .binaryOperation(
                      delta,
                      new CSimpleType(
                          false,
                          false,
                          CBasicType.INT,
                          true,
                          false,
                          true,
                          false,
                          false,
                          false,
                          false),
                      CBinaryExpression.BinaryOperator.MULTIPLY)
                  .binaryOperation(
                      new CIdExpression(FileLocation.DUMMY, newVariableForOverflows),
                      CBinaryExpression.BinaryOperator.PLUS)
                  .build();

      CExpressionAssignmentStatement assignmentExpressionExtrapolation =
          (CExpressionAssignmentStatement)
              new AExpressionFactory().from(leftHandSide).assignTo(var);

      CFAEdge extrapolationDummyEdge =
          new CStatementEdge(
              assignmentExpressionExtrapolation.toString(),
              assignmentExpressionExtrapolation,
              FileLocation.DUMMY,
              currentSummaryNodeCFA,
              nextSummaryNode);
      extrapolationDummyEdge.connect();

      currentSummaryNodeCFA = nextSummaryNode;
      nextSummaryNode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());

      // Since the formula for checking for an overflow explicitly is very expensive
      // we add an if statement to the CFA, which checkss if the possibility of an overflow exists.
      // If it does, the statement is executed in order to explicitly find the overflow
      // TODO

      // Make a statement in order to check for an overflow
      // INT_MAX + (  ((int)(x + incr)) == x + incr  )  to raise the overflow if it happens, since
      // the c standard implicitly calculates modulo when a long is assigned to an int

      CExpression overflowExpression =
          (CExpression)
              new AExpressionFactory()
                  .from(var)
                  .binaryOperation(leftHandSide, BinaryOperator.NOT_EQUALS)
                  .binaryOperation(
                      TypeFactory.getUpperLimit(var.getType()),
                      var.getType(),
                      var.getType(),
                      BinaryOperator.PLUS)
                  .build();

      CFAEdge overflowCheckEdge =
          new CStatementEdge(
              overflowExpression.toString(),
              new CExpressionStatement(FileLocation.DUMMY, overflowExpression),
              FileLocation.DUMMY,
              currentSummaryNodeCFA,
              nextSummaryNode);
      overflowCheckEdge.connect();

      currentSummaryNodeCFA = nextSummaryNode;
      nextSummaryNode = CFANode.newDummyCFANode(pBeforeWhile.getFunctionName());
    }

    // Unroll Loop two times

    unrolledLoopNodesMaybe = pLoopStructure.unrollOutermostLoop();
    if (unrolledLoopNodesMaybe.isEmpty()) {
      return Optional.empty();
    }

    startUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getFirst();
    endUnrolledLoopNode = unrolledLoopNodesMaybe.orElseThrow().getSecond();
    currentSummaryNodeCFA.connectTo(startUnrolledLoopNode);

    unrolledLoopNodesMaybe = pLoopStructure.unrollOutermostLoop();
    if (unrolledLoopNodesMaybe.isEmpty()) {
      return Optional.empty();
    }

    CFANode secondStartUnrolledNode = unrolledLoopNodesMaybe.orElseThrow().getFirst();
    CFANode secondEndUnrolledNode = unrolledLoopNodesMaybe.orElseThrow().getSecond();

    endUnrolledLoopNode.connectTo(secondStartUnrolledNode);
    secondEndUnrolledNode.connectTo(endNodeGhostCFA);

    CFAEdge leavingEdge;
    Iterator<CFAEdge> iter =
        pLoopStructure.getOutgoingEdges().iterator();
    if (iter.hasNext()) {
      leavingEdge = iter.next();
      if (iter.hasNext()) {
        return Optional.empty();
      }
    } else {
      return Optional.empty();
    }

    return Optional.of(
        new GhostCFA(
            startNodeGhostCFA,
            endNodeGhostCFA,
            pBeforeWhile,
            leavingEdge.getSuccessor(),
            this.strategyEnum));
  }

  @Override
  public Optional<GhostCFA> summarize(final CFANode beforeWhile) {

    List<CFAEdge> filteredOutgoingEdges =
        this.summaryFilter.getEdgesForStrategies(beforeWhile.getLeavingEdges(), new HashSet<>(Arrays.asList(StrategiesEnum.Base, this.strategyEnum)));

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

    if (!loopStructure.hasOnlyConstantVariableModifications()
        || loopStructure.amountOfInnerAssumeEdges() != 1) {
      return Optional.empty();
    }

    Optional<AExpression> loopBoundExpressionMaybe = loopStructure.getBound();
    if (loopBoundExpressionMaybe.isEmpty()) {
      return Optional.empty();
    }
    AExpression loopBoundExpression = loopBoundExpressionMaybe.orElseThrow();

    Optional<AExpression> iterationsMaybe = this.loopIterations(loopBoundExpression, loopStructure);

    if (iterationsMaybe.isEmpty()) {
      return Optional.empty();
    }

    AExpression iterations = iterationsMaybe.orElseThrow();

    Optional<GhostCFA> summarizedLoopMaybe =
        summarizeLoop(iterations, loopBoundExpression, loopStructure, beforeWhile);

    this.nameCounter += 1;

    return summarizedLoopMaybe;

  }
}
