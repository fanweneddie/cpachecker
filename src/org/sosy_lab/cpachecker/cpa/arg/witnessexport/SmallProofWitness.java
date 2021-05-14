// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.arg.witnessexport;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.AbstractStates;

public class SmallProofWitness {

  private SmallProofWitness() {

  }

  public static SmallProofWitness fromARG(ARGState argRoot) {
    Objects.requireNonNull(argRoot);

    return new SmallProofWitness();
  }

  private static ImmutableList<ImmutableList<ARGState>> findJoinLocations(ARGState argRoot) {
    // extract all nodes from the ARG which are at a location where multiple CFA paths join
    // and sort the list by location
    List<ARGState> argNodesAtJoinLocations =
        argRoot.getSubgraph()
          .filter(s -> {
            CFANode loc = AbstractStates.extractLocation(s);
            return loc != null && loc.getNumEnteringEdges() > 1;
          })
          .toSortedList(Comparator.comparing(AbstractStates::extractLocation));

    var argNodesPartitioned = ImmutableList.<ImmutableList<ARGState>>builder();

    for (int i = 0; i < argNodesAtJoinLocations.size();) {
      ARGState argNode = argNodesAtJoinLocations.get(i);
      CFANode location = AbstractStates.extractLocation(argNode);

      int locationRangeBegin = i;
      int j = i + 1;
      for (; j < argNodesAtJoinLocations.size(); j++) {
        argNode = argNodesAtJoinLocations.get(j);
        CFANode argLoc = AbstractStates.extractLocation(argNode);

        if (!Objects.equals(location, argLoc)) {
          break;
        }
      }
      int locationRangeEnd = j; // exclusive

      List<ARGState> locationNodes =
          argNodesAtJoinLocations.subList(locationRangeBegin, locationRangeEnd);
      argNodesPartitioned.add(ImmutableList.copyOf(locationNodes));
      i = locationRangeEnd;
    }

    return argNodesPartitioned.build();
  }
}
