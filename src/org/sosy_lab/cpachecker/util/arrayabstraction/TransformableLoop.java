// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.arrayabstraction;

import com.google.common.collect.ImmutableSet;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CLeftHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.CFAVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.TraversalProcess;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

/** Represents a loop in a program that can be transformed/abstracted. */
public final class TransformableLoop {

  private final CFANode loopCfaNode;
  private final CIdExpression loopIndexExpression;

  private final CStatementEdge initLoopIndexCfaEdge;
  private final CStatementEdge updateLoopIndexCfaEdge;
  private final CAssumeEdge enterLoopCfaEdge;
  private final CAssumeEdge exitLoopCfaEdge;

  private final ImmutableSet<CFAEdge> loopEdges;

  private TransformableLoop(
      CFANode pLoopCfaNode,
      CIdExpression pLoopIndexExpression,
      CStatementEdge pInitLoopIndexCfaEdge,
      CStatementEdge pUpdateLoopIndexCfaEdge,
      CAssumeEdge pEnterLoopCfaEdge,
      CAssumeEdge pExitLoopCfaEdge,
      ImmutableSet<CFAEdge> pLoopEdges) {

    loopCfaNode = pLoopCfaNode;
    loopIndexExpression = pLoopIndexExpression;
    initLoopIndexCfaEdge = pInitLoopIndexCfaEdge;
    updateLoopIndexCfaEdge = pUpdateLoopIndexCfaEdge;
    enterLoopCfaEdge = pEnterLoopCfaEdge;
    exitLoopCfaEdge = pExitLoopCfaEdge;
    loopEdges = pLoopEdges;
  }

  /**
   * Returns all transformable loops in the specified CFA.
   *
   * @param pCfa the CFA to find transformable loops in
   * @return all transformable loops in the specified CFA
   * @throws NullPointerException if {@code pCfa == null}
   */
  public static ImmutableSet<TransformableLoop> getTransformableLoops(CFA pCfa) {

    Objects.requireNonNull(pCfa, "pCfa must not be null");

    ImmutableSet.Builder<TransformableLoop> transformableLoops = ImmutableSet.builder();

    for (CFANode node : pCfa.getAllNodes()) {
      if (node.isLoopStart()) {

        TransformableLoop.Builder builder = new TransformableLoop.Builder();
        if (builder.initialize(node)) {
          transformableLoops.add(builder.build());
        }
      }
    }

    return transformableLoops.build();
  }

  public CFANode getLoopCfaNode() {
    return loopCfaNode;
  }

  public CIdExpression getLoopIndexExpression() {
    return loopIndexExpression;
  }

  public MemoryLocation getLoopIndexMemoryLocation() {
    return ArrayAbstractionUtils.getMemoryLocation(loopIndexExpression);
  }

  public CStatementEdge getInitLoopIndexCfaEdge() {
    return initLoopIndexCfaEdge;
  }

  public CStatementEdge getUpdateLoopIndexCfaEdge() {
    return updateLoopIndexCfaEdge;
  }

  public CAssumeEdge getEnterLoopCfaEdge() {
    return enterLoopCfaEdge;
  }

  public CAssumeEdge getExitLoopCfaEdge() {
    return exitLoopCfaEdge;
  }

  public ImmutableSet<CFAEdge> getLoopEdges() {
    return loopEdges;
  }

  private static final class Builder {

    private CFANode node;

    private CIdExpression indexCIdExpression = null;

    private CStatementEdge initLoopIndexCfaEdge = null;
    private CStatementEdge updateLoopIndexCfaEdge = null;
    private CAssumeEdge enterLoopCfaEdge = null;
    private CAssumeEdge exitLoopCfaEdge = null;

    private boolean initStatementEdges() {

      for (CFAEdge enteringEdge : CFAUtils.allEnteringEdges(node)) {
        if (enteringEdge instanceof CStatementEdge) {

          CStatementEdge statementEdge = (CStatementEdge) enteringEdge;
          CStatement statement = statementEdge.getStatement();

          if (statement instanceof CExpressionAssignmentStatement) {

            CExpressionAssignmentStatement assignmentStatement =
                (CExpressionAssignmentStatement) statement;

            // handle LHS
            CLeftHandSide lhs = assignmentStatement.getLeftHandSide();
            if (lhs instanceof CIdExpression) {

              CIdExpression lhsIdExpression = (CIdExpression) lhs;

              if (indexCIdExpression == null) {
                indexCIdExpression = (CIdExpression) lhs;
              } else {

                MemoryLocation lhsMemoryLocation =
                    ArrayAbstractionUtils.getMemoryLocation(lhsIdExpression);
                MemoryLocation indexMemoryLocation =
                    ArrayAbstractionUtils.getMemoryLocation(indexCIdExpression);

                if (!lhsMemoryLocation.equals(indexMemoryLocation)) {
                  return false;
                }
              }
            } else {
              return false;
            }

            // handle RHS
            CExpression rhs = assignmentStatement.getRightHandSide();
            if (rhs instanceof CIntegerLiteralExpression && initLoopIndexCfaEdge == null) {

              initLoopIndexCfaEdge = statementEdge;

            } else if (rhs instanceof CBinaryExpression && updateLoopIndexCfaEdge == null) {

              CBinaryExpression rhsBinary = (CBinaryExpression) rhs;
              CExpression sndOperand = rhsBinary.getOperand2();
              CBinaryExpression.BinaryOperator operator = rhsBinary.getOperator();

              if (operator.equals(CBinaryExpression.BinaryOperator.PLUS)
                  || operator.equals(CBinaryExpression.BinaryOperator.MINUS)) {

                if (!rhsBinary.getOperand1().equals(lhs)) {
                  return false;
                }

                if (!(sndOperand instanceof CIntegerLiteralExpression)) {
                  return false;
                }

                BigInteger stepValue = ((CIntegerLiteralExpression) sndOperand).getValue();

                if (stepValue.equals(BigInteger.ONE)) {
                  updateLoopIndexCfaEdge = statementEdge;
                } else {
                  return false;
                }
              }
            } else {
              return false;
            }
          } else {
            return false;
          }
        }
      }

      return indexCIdExpression != null
          && initLoopIndexCfaEdge != null
          && updateLoopIndexCfaEdge != null;
    }

    private boolean initAssumeEdges() {

      for (CFAEdge leavingEdge : CFAUtils.allLeavingEdges(node)) {
        if (leavingEdge instanceof CAssumeEdge) {

          CAssumeEdge assumeEdge = (CAssumeEdge) leavingEdge;
          CExpression expression = assumeEdge.getExpression();

          if (expression instanceof CBinaryExpression) {

            CBinaryExpression binaryExpression = (CBinaryExpression) expression;
            CExpression fstOperand = binaryExpression.getOperand1();

            if (!(fstOperand instanceof CIdExpression)) {
              return false;
            }

            CIdExpression fstOperandIdExpression = (CIdExpression) fstOperand;
            MemoryLocation fstOperandMemoryLocation =
                ArrayAbstractionUtils.getMemoryLocation(fstOperandIdExpression);
            MemoryLocation indexMemoryLocation =
                ArrayAbstractionUtils.getMemoryLocation(indexCIdExpression);

            if (!fstOperandMemoryLocation.equals(indexMemoryLocation)) {
              return false;
            }

            CBinaryExpression.BinaryOperator operator = binaryExpression.getOperator();

            if (operator.equals(CBinaryExpression.BinaryOperator.LESS_THAN)
                || operator.equals(CBinaryExpression.BinaryOperator.GREATER_THAN)
                || operator.equals(CBinaryExpression.BinaryOperator.LESS_EQUAL)
                || operator.equals(CBinaryExpression.BinaryOperator.GREATER_EQUAL)) {

              if (assumeEdge.getTruthAssumption() && enterLoopCfaEdge == null) {
                enterLoopCfaEdge = assumeEdge;
              } else if (!assumeEdge.getTruthAssumption() && exitLoopCfaEdge == null) {
                exitLoopCfaEdge = assumeEdge;
              } else {
                return false;
              }
            }
          } else {
            return false;
          }
        } else {
          return false;
        }
      }

      return enterLoopCfaEdge != null && exitLoopCfaEdge != null;
    }

    private boolean initialize(CFANode pNode) {

      node = pNode;

      return initStatementEdges() && initAssumeEdges();
    }

    private TransformableLoop build() {

      CFAEdge skipEdge = exitLoopCfaEdge;
      Set<CFAEdge> visitedEdges = new HashSet<>();

      CFATraversal.dfs()
          .ignoreFunctionCalls()
          .traverseOnce(
              node,
              new CFAVisitor() {

                @Override
                public TraversalProcess visitEdge(CFAEdge pEdge) {

                  visitedEdges.add(pEdge);

                  return pEdge.equals(skipEdge) ? TraversalProcess.SKIP : TraversalProcess.CONTINUE;
                }

                @Override
                public TraversalProcess visitNode(CFANode pNode) {
                  return TraversalProcess.CONTINUE;
                }
              });

      return new TransformableLoop(
          node,
          indexCIdExpression,
          initLoopIndexCfaEdge,
          updateLoopIndexCfaEdge,
          enterLoopCfaEdge,
          exitLoopCfaEdge,
          ImmutableSet.copyOf(visitedEdges));
    }
  }
}