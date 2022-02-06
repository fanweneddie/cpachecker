// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.smg2.util.value;

import org.sosy_lab.cpachecker.cpa.smg2.SMGState;
import org.sosy_lab.cpachecker.cpa.value.type.Value;

public class ValueAndSMGState {

  private final Value value;
  private final SMGState state;

  private ValueAndSMGState(Value pValue, SMGState pState) {
    value = pValue;
    state = pState;
  }

  public static ValueAndSMGState of(Value pValue, SMGState pState) {
    return new ValueAndSMGState(pValue, pState);
  }

  public Value getValue() {
    return value;
  }

  public SMGState getState() {
    return state;
  }
}