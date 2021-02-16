// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.core.algorithm.residualprogram;

import org.sosy_lab.cpachecker.core.algorithm.residualprogram.TestGoalToConditionConverterAlgorithm.LeafStates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.c.CLabelNode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.location.LocationState;
import org.sosy_lab.cpachecker.util.AbstractStates;

/**
 * Finds all goals that are leafs in the CFA.
 */
public class LeafGoalStrategy implements IGoalFindingStrategy {
        /**
         * Performs a breadth-first-search to get all un-/covered nodes.
         * @param pWaitlist An initial list of ARGstates to check. Should be the exit node(s)
         * of the function.
         * @param coveredGoals A list of all covered goals (or rather their corresponding labels).
         * @return A map of (un-)/covered goals.
         */
        @Override
        public Map<LeafStates, List<CFANode>> findGoals(List<ARGState> pWaitlist, final Set<String> coveredGoals) {
                var waitList = new ArrayList<>(pWaitlist);
                Set<ARGState> reachedNodes = new HashSet<>();

                Map<LeafStates, List<CFANode>> leafGoals = new HashMap<>();
                leafGoals.put(LeafStates.COVERED, new ArrayList<>());
                leafGoals.put(LeafStates.UNCOVERED, new ArrayList<>());

                while(!waitList.isEmpty()) {
                        var argState = waitList.pop();
                        reachedNodes.add(argState);

                        var state = AbstractStates.extractStateByType(argState, LocationState.class);
                        if(state == null) {
                                continue; //Should never happen
                        }

                        var label = state.getLocationNode();

                        if(label instanceof CLabelNode && ((CLabelNode) label).getLabel().matches("^GOAL_[0-9]+$")) {
                                if(coveredGoals.contains(((CLabelNode) label).getLabel())) {
                                        leafGoals.get(LeafStates.COVERED).add(label);
                                } else {
                                        leafGoals.get(LeafStates.UNCOVERED).add(label);
                                }

                                continue;
                        }

                        for (var it: argState.getChildren()) {
                                if(!reachedNodes.contains(it)) {
                                        waitList.add(it);
                                }
                        }
                }

                return leafGoals;
        }
}
