/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cfa.ast.js;

import org.sosy_lab.cpachecker.cfa.ast.AFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;

public final class JSFunctionCallAssignmentStatement extends AFunctionCallAssignmentStatement
    implements JSStatement, JSAssignment, JSFunctionCall {

  public JSFunctionCallAssignmentStatement(
      FileLocation pFileLocation,
      JSLeftHandSide pLeftHandSide,
      JSFunctionCallExpression pRightHandSide) {
    super(pFileLocation, pLeftHandSide, pRightHandSide);
  }

  @Override
  public JSLeftHandSide getLeftHandSide() {
    return (JSLeftHandSide) super.getLeftHandSide();
  }

  @Override
  public JSFunctionCallExpression getRightHandSide() {
    return (JSFunctionCallExpression) super.getRightHandSide();
  }

  @Override
  public JSFunctionCallExpression getFunctionCallExpression() {
    return (JSFunctionCallExpression) super.getFunctionCallExpression();
  }

  @Override
  public <R, X extends Exception> R accept(JSStatementVisitor<R, X> v) throws X {
    return v.visit(this);
  }

  @Override
  public <R, X extends Exception> R accept(JSAstNodeVisitor<R, X> pV) throws X {
    return pV.visit(this);
  }

  @Override
  public String toASTString() {
    return getLeftHandSide().toASTString() + " = " + getRightHandSide().toASTString() + ";";
  }

  @Override
  public int hashCode() {
    int prime = 31;
    int result = 7;
    return prime * result + super.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof JSFunctionCallAssignmentStatement)) {
      return false;
    }

    return super.equals(obj);
  }
}
