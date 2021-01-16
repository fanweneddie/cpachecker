// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.dependencegraph;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.AFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.dependencegraph.CallGraph.SuccessorResult;

final class CallGraphUtils {

  static CallGraph<AFunctionDeclaration> createCallGraph(CFA pCfa) {

    return CallGraph.createCallGraph(
        new CFASuccessorFunction(), ImmutableSet.of(pCfa.getMainFunction()));
  }

  private static final class CFASuccessorFunction
      implements Function<CFANode, List<CallGraph.SuccessorResult<AFunctionDeclaration, CFANode>>> {

    @Override
    public List<SuccessorResult<AFunctionDeclaration, CFANode>> apply(CFANode pNode) {

      List<CallGraph.SuccessorResult<AFunctionDeclaration, CFANode>> edges = new ArrayList<>();
      for (CFAEdge edge : CFAUtils.leavingEdges(pNode)) {

        if (edge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
          CFANode successor = edge.getSuccessor();
          edges.add(
              CallGraph.SuccessorResult.createCallSuccessor(
                  pNode.getFunction(), successor.getFunction(), successor));
        } else {
          edges.add(
              CallGraph.SuccessorResult.createNonCallSuccessor(
                  pNode.getFunction(), edge.getSuccessor()));
        }
      }

      return edges;
    }
  }
}
