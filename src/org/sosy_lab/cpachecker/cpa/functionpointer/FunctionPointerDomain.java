/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.cpa.functionpointer;

import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.exceptions.CPAException;

class FunctionPointerDomain implements AbstractDomain {

  private final AbstractDomain wrappedDomain;

  FunctionPointerDomain(AbstractDomain pWrappedDomain) {
    wrappedDomain = pWrappedDomain;
  }

  @Override
  public AbstractElement join(AbstractElement pElement1, AbstractElement pElement2) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isLessOrEqual(AbstractElement pElement1, AbstractElement pElement2) throws CPAException {
    // returns true if element1 < element2 on lattice

    FunctionPointerElement elem1 = (FunctionPointerElement) pElement1;
    FunctionPointerElement elem2 = (FunctionPointerElement) pElement2;

    return elem1.isLessOrEqualThan(elem2)
        && wrappedDomain.isLessOrEqual(elem1.getWrappedElement(), elem2.getWrappedElement());
  }
}