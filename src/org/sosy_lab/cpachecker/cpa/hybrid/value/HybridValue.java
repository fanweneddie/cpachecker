/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.hybrid.value;

import javax.annotation.Nullable;

import com.google.common.base.Objects;

import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.hybrid.util.ExpressionUtils;
import org.sosy_lab.cpachecker.cpa.value.type.Value;

/**
 * This class defines a wrapper for assumption tracking.
 * The implementation of methods solverGenerated() and setAssumptions()
 * make it 'one-time mutable'. 
 */
public class HybridValue {

  private final Value value;
  private boolean solverGenerated;
  private final CType type;
  private CBinaryExpression assumption;

  /**
   * Constructs a new instance of this class
   * @param pValue The internal value to represent
   * @param pType The type for which to create the hybrid value
   */
  public HybridValue(
      Value pValue,
      CType pType) {

    value = pValue;
    type = pType;
  }

  /**
   * Constructs a new instance of this class
   * @param pValue The internal value
   * @param pType The type for which to create the hybrid value
   * @param pBinaryExpression The assumption defined for this hybrid value
   */
  public HybridValue(
      Value pValue,
      CType pType,
      CBinaryExpression pBinaryExpression) {

    this(pValue, pType);
    assumption = pBinaryExpression;
  }

  /**
   * Constructs a new instance of this class.
   * Uses the given binaty expression to generate the missing information.
   * @param pBinaryExpression The binary expression to create the Hybrid Value for
   */
  @Nullable
  public static HybridValue createHybridValueForAssumption(CBinaryExpression pBinaryExpression) {

    Value value = CExpressionToValueTransformer.transform(pBinaryExpression.getOperand2());

    if(value == null) {
      return null;
    }

    return new HybridValue(
      value, 
      pBinaryExpression.getExpressionType(), 
      pBinaryExpression);
  }

  /**
   * @return The represented Value instance
   */
  public Value getValue() {
    return value; // no need for a copy, implementations of Value are immutable
  }

  /**
   * Determines whether the hybrid value was generated by the HybridAnalysisCPA
   * or is parsed from an SMT-Solver Formula
   * @return true, if generated, else false
   */
  public boolean isSolverGenerated() {
    return solverGenerated;
  }

  public void solverGenerated() {
    solverGenerated = true;
  }

  /**
   * @return The respective type correlated to the value
   */
  public CType getType() {
    return type;
  }

  /**
   * @return The assumption represented by this hybrid value
   */
  public CBinaryExpression getAssumption() {
    return assumption;
  }

  /**
   * Sets the assumption for this hybrid value.
   * Does nothing, if the assumption was already set.
   * @param pAssumption The respective assumption for this hybrid value.
   */
  public HybridValue setAssumption(CBinaryExpression pAssumption) {
    if(assumption == null) {
      assumption = pAssumption;
    }
    return this;
  }

  /**
   * Rather check expression than retrieving the nullable variable expression
   * from this hybrid value. 
   */
  @Nullable
  public CIdExpression trackedVariable(){
    return ExpressionUtils.extractIdExpression(assumption);
  }

  public boolean tracksVariable(CExpression pExpression) {

    @Nullable CIdExpression trackedVariable = trackedVariable();
    
    if(trackedVariable == null) return false;

    return trackedVariable().equals(pExpression)
        || ExpressionUtils.haveTheSameVariable(pExpression, trackedVariable);
    
  }

  @Override 
  public String toString() {
    return assumption != null 
      ? String.format("[%s %s %s]", assumption.getOperand1(), assumption.getOperator(), assumption.getOperand2())
      : "";
  }

  @Override
  public boolean equals(Object obj) {
    if(obj == null || !(obj instanceof HybridValue)) {
      return false;
    }

    HybridValue other = (HybridValue) obj;

    return this.value.equals(other.value)
      && this.type.equals(other.type)
      && Objects.equal(this.assumption, other.assumption); // avoid possible null pointer
  } 

  @Override
  public int hashCode() {
    return Objects.hashCode(type, assumption);
  }
}
