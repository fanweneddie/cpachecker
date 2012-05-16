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

import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class FunctionPointerMergeOperator implements MergeOperator {

  private final MergeOperator wrappedMerge;

  public FunctionPointerMergeOperator(MergeOperator pWrappedMerge) {
    wrappedMerge = pWrappedMerge;
  }

  @Override
  public AbstractElement merge(AbstractElement pElement1,
      AbstractElement pElement2, Precision pPrecision) throws CPAException {

    FunctionPointerElement fpElement1 = (FunctionPointerElement)pElement1;
    FunctionPointerElement fpElement2 = (FunctionPointerElement)pElement2;

    if (!fpElement1.isLessOrEqualThan(fpElement2)) {
      // don't merge here
      return pElement2;
    }

    AbstractElement wrappedElement1 = fpElement1.getWrappedElement();
    AbstractElement wrappedElement2 = fpElement2.getWrappedElement();
    AbstractElement retElement = wrappedMerge.merge(wrappedElement1, wrappedElement2, pPrecision);
    if (retElement.equals(wrappedElement2)) {
      return pElement2;
    }

    return fpElement2.createDuplicateWithNewWrappedElement(retElement);
  }
}
