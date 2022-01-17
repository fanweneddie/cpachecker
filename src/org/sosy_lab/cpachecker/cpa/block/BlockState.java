// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.block;

import com.google.common.collect.ImmutableSet;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.AbstractStateWithLocation;
import org.sosy_lab.cpachecker.core.interfaces.Partitionable;
import org.sosy_lab.cpachecker.core.interfaces.Targetable;
import org.sosy_lab.cpachecker.util.CFAUtils;

public class BlockState
    implements AbstractStateWithLocation, AbstractQueryableState, Partitionable,
               Serializable, Targetable {

  private static final long serialVersionUID = 3805801L;

  private final Set<TargetInformation> targetInformation;
  private final CFANode targetNode;
  private final CFANode node;
  private final boolean forward;

  public BlockState(CFANode pNode, CFANode pTargetNode, boolean pForward) {
    node = pNode;
    targetNode = pTargetNode;
    targetInformation = new HashSet<>();
    forward = pForward;
    if (isTarget()) {
      targetInformation.add(new BlockStartReachedTargetInformation(pTargetNode));
    }
  }

  @Override
  public CFANode getLocationNode() {
    return node;
  }

  @Override
  public Iterable<CFANode> getLocationNodes() {
    return ImmutableSet.of(node);
  }

  @Override
  public Iterable<CFAEdge> getOutgoingEdges() {
    return CFAUtils.allLeavingEdges(node);
  }

  @Override
  public Iterable<CFAEdge> getIngoingEdges() {
    return CFAUtils.allEnteringEdges(node);
  }

  @Override
  public String getCPAName() {
    return "block";
  }

  @Override
  public @Nullable Object getPartitionKey() {
    return this;
  }

  @Override
  public String toString() {
    return "Location: " + node;
  }

  @Override
  public @NonNull Set<TargetInformation> getTargetInformation() throws IllegalStateException {
    return targetInformation;
  }

  @Override
  public boolean isTarget() {
    return !forward && targetNode.equals(node);
  }

}