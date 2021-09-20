// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa.model.c;

import com.google.common.base.Optional;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.model.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

public class CAssumeEdge extends AssumeEdge {

  private static final long serialVersionUID = -3330760789129113642L;

  public CAssumeEdge(String pRawStatement, FileLocation pFileLocation, CFANode pPredecessor,
      CFANode pSuccessor, CExpression pExpression, boolean pTruthAssumption) {

    this(
        pRawStatement,
        pFileLocation,
        pPredecessor,
        pSuccessor,
        pExpression,
        pTruthAssumption,
        false,
        false);
  }

  public CAssumeEdge(
      String pRawStatement,
      FileLocation pFileLocation,
      CFANode pPredecessor,
      CFANode pSuccessor,
      CExpression pExpression,
      boolean pTruthAssumption,
      boolean pSwapped,
      boolean pArtificial) {

    super(
        pRawStatement,
        pFileLocation,
        pPredecessor,
        pSuccessor,
        pExpression,
        pTruthAssumption,
        pSwapped,
        pArtificial);
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.AssumeEdge;
  }

  @Override
  public CExpression getExpression() {
    return (CExpression) expression;
  }


  /**
   * TODO
   * Warning: for instances with {@link #getTruthAssumption()} == false, the
   * return value of this method does not represent exactly the return value
   * of {@link #getRawStatement()} (it misses the outer negation of the expression).
   */
  @Override
  public Optional<CExpression> getRawAST() {
    return Optional.of((CExpression)expression);
  }

  @Override
  public CAssumeEdge copyWith(CFANode pNewPredecessorNode, CFANode pNewSuccessorNode) {
    return new CAssumeEdge(
        getRawStatement(),
        getFileLocation(),
        pNewPredecessorNode,
        pNewSuccessorNode,
        getExpression(),
        getTruthAssumption(),
        isSwapped(),
        isArtificialIntermediate());
  }
}
