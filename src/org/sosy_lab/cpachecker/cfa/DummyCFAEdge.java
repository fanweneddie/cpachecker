// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cfa;

import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

public class DummyCFAEdge implements CFAEdge {

  private static final long serialVersionUID = 1L;
  private final CFANode successor;
  private final CFANode predecessor;

  public DummyCFAEdge(CFANode pPredecessor, CFANode pSuccessor) {
    predecessor = pPredecessor;
    successor = pSuccessor;
  }

  @Override
  public String getRawStatement() {
    return "";
  }

  @Override
  public com.google.common.base.Optional<? extends AAstNode> getRawAST() {
    return com.google.common.base.Optional.absent();
  }

  @Override
  public CFANode getPredecessor() {
    return predecessor;
  }

  @Override
  public CFANode getSuccessor() {
    return successor;
  }

  @Override
  public int getLineNumber() {
    return getFileLocation().getStartingLineNumber();
  }

  @Override
  public FileLocation getFileLocation() {
    return FileLocation.DUMMY;
  }

  @Override
  public CFAEdgeType getEdgeType() {
    return CFAEdgeType.BlankEdge;
  }

  @Override
  public String getDescription() {
    return "";
  }

  @Override
  public String getCode() {
    return "";
  }

  @Override
  public DummyCFAEdge copyWith(CFANode pNewPredecessorNode, CFANode pNewSuccessorNode) {
    return new DummyCFAEdge(pNewPredecessorNode, pNewSuccessorNode);
  }

  @Override
  public void connect() {
    this.getPredecessor().addLeavingEdge(this);
    this.getSuccessor().addEnteringEdge(this);
  }
}