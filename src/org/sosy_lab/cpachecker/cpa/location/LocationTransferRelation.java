// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.location;

import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.StrategiesEnum;
import org.sosy_lab.cpachecker.cfa.postprocessing.summaries.SummaryInformation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.CFAUtils;

public class LocationTransferRelation implements TransferRelation {

  private final LocationStateFactory factory;
  private final CFA pCFA;

  public LocationTransferRelation(LocationStateFactory pFactory, CFA pCFA) {
    factory = pFactory;
    this.pCFA = pCFA;
  }

  @Override
  public Collection<LocationState> getAbstractSuccessorsForEdge(
      AbstractState element, Precision prec, CFAEdge cfaEdge) {

    CFANode node = ((LocationState) element).getLocationNode();

    if (CFAUtils.allLeavingEdges(node).contains(cfaEdge)) {
      return Collections.singleton(factory.getState(cfaEdge.getSuccessor()));
    }

    return ImmutableSet.of();
  }

  @Override
  public Collection<LocationState> getAbstractSuccessors(AbstractState element,
      Precision prec) throws CPATransferException {

    CFANode node = ((LocationState) element).getLocationNode();

    if (this.pCFA.getSummaryInformation().isEmpty()) {
      return CFAUtils.successorsOf(node).transform(n -> factory.getState(n)).toList();
    } else {
      SummaryInformation summaryInformation =  pCFA.getSummaryInformation().get();
      List<StrategiesEnum> availableStrategies =
          CFAUtils.successorsOf(node)
              .transform(n -> summaryInformation.getStrategyForNode(n))
              .toList();
      Set<StrategiesEnum> allowedStrategies =
          new HashSet<>(summaryInformation.getSummaryStrategy().filter(availableStrategies));
      allowedStrategies.removeAll(summaryInformation.getUnallowedStrategiesForNode(node));

      return CFAUtils.successorsOf(node)
          .filter(n -> allowedStrategies.contains(summaryInformation.getStrategyForNode(n)))
          .transform(n -> factory.getState(n))
          .toList();
    }
  }
}
