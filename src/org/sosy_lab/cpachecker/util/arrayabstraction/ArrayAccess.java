// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.arrayabstraction;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.sosy_lab.cpachecker.cfa.ast.AAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAddressOfLabelExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArrayRangeDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CArraySubscriptExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNode;
import org.sosy_lab.cpachecker.cfa.ast.c.CAstNodeVisitor;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CCharLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexCastExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CComplexTypeDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldReference;
import org.sosy_lab.cpachecker.cfa.ast.c.CFloatLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CImaginaryLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CPointerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CReturnStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CStringLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeDefDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CTypeIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CUnaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CEnumType.CEnumerator;
import org.sosy_lab.cpachecker.exceptions.NoException;

/**
 * Represents an array access via a subscript expression.
 *
 * <p>Array accesses can either be reading accesses (e.g., {@code int value = array[index + 1];}) or
 * a writing accesses (e.g., {@code array[index + 1] = value;}).
 */
final class ArrayAccess {

  private final ArrayAccess.Type type;
  private final CExpression expression;

  private ArrayAccess(ArrayAccess.Type pType, CExpression pExpression) {
    type = pType;
    expression = pExpression;
  }

  private static Optional<CArraySubscriptExpression> toArraySubscriptExpression(
      CExpression pExpression) {

    if (pExpression instanceof CArraySubscriptExpression) {
      return Optional.of((CArraySubscriptExpression) pExpression);
    }

    if (pExpression instanceof CPointerExpression) {

      CPointerExpression pointerExpression = (CPointerExpression) pExpression;
      CExpression operand = pointerExpression.getOperand();

      if (operand instanceof CIdExpression) {
        CArraySubscriptExpression arraySubscriptExpression =
            new CArraySubscriptExpression(
                pExpression.getFileLocation(),
                pExpression.getExpressionType(),
                operand,
                CIntegerLiteralExpression.ZERO);
        return Optional.of(arraySubscriptExpression);
      }

      if (operand instanceof CBinaryExpression) {

        CBinaryExpression binaryExpression = (CBinaryExpression) operand;
        CBinaryExpression.BinaryOperator operator = binaryExpression.getOperator();
        CExpression operand1 = binaryExpression.getOperand1();
        CExpression operand2 = binaryExpression.getOperand2();

        if (operator == CBinaryExpression.BinaryOperator.PLUS) {
          CArraySubscriptExpression arraySubscriptExpression =
              new CArraySubscriptExpression(
                  pExpression.getFileLocation(),
                  pExpression.getExpressionType(),
                  operand1,
                  operand2);
          return Optional.of(arraySubscriptExpression);
        }
      }
    }

    return Optional.empty();
  }

  /**
   * Returns a set of all array accesses contained in the specified AST node.
   *
   * @param pCAstNode AST node to search for array accesses
   * @return a set of all array accesses contained in the specified AST node
   * @throws NullPointerException if {@code pCAstNode == null}
   */
  public static ImmutableSet<ArrayAccess> findArrayAccesses(CAstNode pCAstNode) {

    ImmutableSet.Builder<ArrayAccess> builder = ImmutableSet.builder();

    pCAstNode.accept(
        new ArrayAccessFinder(
            (type, expression) -> builder.add(new ArrayAccess(type, expression))));

    return builder.build();
  }

  /**
   * Returns a set of all array accesses contained in the specified CFA edge.
   *
   * @param pEdge CFA edge to search for array accesses
   * @return a set of all array accesses contained in the specified CFA edge
   * @throws NullPointerException if {@code pEdge == null}
   */
  public static ImmutableSet<ArrayAccess> findArrayAccesses(CFAEdge pEdge) {

    if (pEdge instanceof CFunctionSummaryEdge) {
      return findArrayAccesses(((CFunctionSummaryEdge) pEdge).getExpression());
    }

    Optional<? extends AAstNode> optAstNode = pEdge.getRawAST();
    if (optAstNode.isPresent()) {
      AAstNode astNode = optAstNode.get();
      if (astNode instanceof CAstNode) {
        return findArrayAccesses((CAstNode) astNode);
      }
    }

    return ImmutableSet.of();
  }

  /**
   * Returns the type of this array access (read/write).
   *
   * @return the type of this array access (read/write).
   */
  public ArrayAccess.Type getType() {
    return type;
  }

  /**
   * Returns whether this array access is a reading array access.
   *
   * @return If this array access is a reading access, {@code true} is returned. Otherwise, if this
   *     array access is a writing array access, {@code false} is returned.
   */
  public boolean isRead() {
    return type == ArrayAccess.Type.READ;
  }

  /**
   * Returns whether this array access is a writing array access.
   *
   * @return If this array access is a writing access, {@code true} is returned. Otherwise, if this
   *     array access is a reading array access, {@code false} is returned.
   */
  public boolean isWrite() {
    return type == ArrayAccess.Type.WRITE;
  }

  /**
   * Returns the expression of this array access (e.g., {@code array[index + 1]}).
   *
   * @return expression of this array access
   */
  public CExpression getExpression() {
    return expression;
  }

  /**
   * Returns the array expression of this array access.
   *
   * <p>Example: the expression of {@code array} from the array access {@code array[index + 1]} is
   * returned.
   *
   * @return the array expression of this array access
   */
  public CExpression getArrayExpression() {
    return toArraySubscriptExpression(expression).orElseThrow().getArrayExpression();
  }

  /**
   * Returns the subscript expression of this array access.
   *
   * <p>Example: the expression of {@code index + 1} from the array access {@code array[index + 1]}
   * is returned.
   *
   * @return the subscript expression of this array access
   */
  public CExpression getSubscriptExpression() {
    return toArraySubscriptExpression(expression).orElseThrow().getSubscriptExpression();
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, expression);
  }

  @Override
  public boolean equals(Object pObject) {

    if (this == pObject) {
      return true;
    }

    if (!(pObject instanceof ArrayAccess)) {
      return false;
    }

    ArrayAccess other = (ArrayAccess) pObject;
    return type == other.type && Objects.equals(expression, other.expression);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("type", type)
        .add("expression", expression)
        .toString();
  }

  /** Represents the type of an array access (read/write). */
  public enum Type {
    WRITE,
    READ
  }

  /** AST node visitor for finding array accesses. */
  private static final class ArrayAccessFinder implements CAstNodeVisitor<Void, NoException> {

    private final BiConsumer<ArrayAccess.Type, CExpression> consumer;
    private ArrayAccess.Type mode;

    private ArrayAccessFinder(BiConsumer<ArrayAccess.Type, CExpression> pConsumer) {
      consumer = pConsumer;
      mode = ArrayAccess.Type.READ;
    }

    @Override
    public Void visit(CArraySubscriptExpression pIastArraySubscriptExpression) {

      consumer.accept(mode, pIastArraySubscriptExpression);

      ArrayAccess.Type prev = mode;

      mode = ArrayAccess.Type.READ;

      pIastArraySubscriptExpression.getArrayExpression().accept(this);
      pIastArraySubscriptExpression.getSubscriptExpression().accept(this);

      mode = prev;

      return null;
    }

    @Override
    public Void visit(CArrayDesignator pArrayDesignator) {

      pArrayDesignator.getSubscriptExpression().accept(this);

      return null;
    }

    @Override
    public Void visit(CArrayRangeDesignator pArrayRangeDesignator) {

      pArrayRangeDesignator.getFloorExpression().accept(this);
      pArrayRangeDesignator.getCeilExpression().accept(this);

      return null;
    }

    @Override
    public Void visit(CFieldDesignator pFieldDesignator) {
      return null;
    }

    @Override
    public Void visit(CInitializerExpression pInitializerExpression) {

      pInitializerExpression.getExpression().accept(this);

      return null;
    }

    @Override
    public Void visit(CInitializerList pInitializerList) {

      for (CInitializer initializer : pInitializerList.getInitializers()) {
        initializer.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CDesignatedInitializer pCStructInitializerPart) {

      pCStructInitializerPart.getRightHandSide().accept(this);

      for (CDesignator designator : pCStructInitializerPart.getDesignators()) {
        designator.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CFunctionCallExpression pIastFunctionCallExpression) {

      pIastFunctionCallExpression.getFunctionNameExpression().accept(this);

      for (CExpression expression : pIastFunctionCallExpression.getParameterExpressions()) {
        expression.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CBinaryExpression pIastBinaryExpression) {

      pIastBinaryExpression.getOperand1().accept(this);
      pIastBinaryExpression.getOperand2().accept(this);

      return null;
    }

    @Override
    public Void visit(CCastExpression pIastCastExpression) {

      pIastCastExpression.getOperand().accept(this);

      return null;
    }

    @Override
    public Void visit(CCharLiteralExpression pIastCharLiteralExpression) {
      return null;
    }

    @Override
    public Void visit(CFloatLiteralExpression pIastFloatLiteralExpression) {
      return null;
    }

    @Override
    public Void visit(CIntegerLiteralExpression pIastIntegerLiteralExpression) {
      return null;
    }

    @Override
    public Void visit(CStringLiteralExpression pIastStringLiteralExpression) {
      return null;
    }

    @Override
    public Void visit(CTypeIdExpression pIastTypeIdExpression) {
      return null;
    }

    @Override
    public Void visit(CUnaryExpression pIastUnaryExpression) {

      pIastUnaryExpression.getOperand().accept(this);

      return null;
    }

    @Override
    public Void visit(CImaginaryLiteralExpression PIastLiteralExpression) {
      return null;
    }

    @Override
    public Void visit(CAddressOfLabelExpression pAddressOfLabelExpression) {
      return null;
    }

    @Override
    public Void visit(CFieldReference pIastFieldReference) {

      if (pIastFieldReference.isPointerDereference()) {

        ArrayAccess.Type prev = mode;

        mode = ArrayAccess.Type.READ;
        pIastFieldReference.getFieldOwner().accept(this);

        mode = prev;

      } else {
        pIastFieldReference.getFieldOwner().accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CIdExpression pIastIdExpression) {
      return null;
    }

    @Override
    public Void visit(CPointerExpression pPointerExpression) {

      if (toArraySubscriptExpression(pPointerExpression).isPresent()) {
        consumer.accept(mode, pPointerExpression);
      }

      ArrayAccess.Type prev = mode;

      mode = ArrayAccess.Type.READ;
      pPointerExpression.getOperand().accept(this);

      mode = prev;

      return null;
    }

    @Override
    public Void visit(CComplexCastExpression pComplexCastExpression) {

      pComplexCastExpression.getOperand().accept(this);

      return null;
    }

    @Override
    public Void visit(CFunctionDeclaration pDecl) {

      for (CParameterDeclaration declaration : pDecl.getParameters()) {
        declaration.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CComplexTypeDeclaration pDecl) {
      return null;
    }

    @Override
    public Void visit(CTypeDefDeclaration pDecl) {
      return null;
    }

    @Override
    public Void visit(CVariableDeclaration pDecl) {

      CInitializer initializer = pDecl.getInitializer();
      if (initializer != null) {
        mode = ArrayAccess.Type.READ;
        initializer.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CParameterDeclaration pDecl) {

      pDecl.asVariableDeclaration().accept(this);

      return null;
    }

    @Override
    public Void visit(CEnumerator pDecl) {
      return null;
    }

    @Override
    public Void visit(CExpressionStatement pIastExpressionStatement) {

      pIastExpressionStatement.getExpression().accept(this);

      return null;
    }

    @Override
    public Void visit(CExpressionAssignmentStatement pIastExpressionAssignmentStatement) {

      mode = ArrayAccess.Type.WRITE;
      pIastExpressionAssignmentStatement.getLeftHandSide().accept(this);

      mode = ArrayAccess.Type.READ;
      pIastExpressionAssignmentStatement.getRightHandSide().accept(this);

      return null;
    }

    @Override
    public Void visit(CFunctionCallAssignmentStatement pIastFunctionCallAssignmentStatement) {

      mode = ArrayAccess.Type.WRITE;
      pIastFunctionCallAssignmentStatement.getLeftHandSide().accept(this);

      mode = ArrayAccess.Type.READ;
      pIastFunctionCallAssignmentStatement.getRightHandSide().accept(this);

      return null;
    }

    @Override
    public Void visit(CFunctionCallStatement pIastFunctionCallStatement) {

      List<CExpression> paramExpressions =
          pIastFunctionCallStatement.getFunctionCallExpression().getParameterExpressions();

      for (CExpression expression : paramExpressions) {
        expression.accept(this);
      }

      CFunctionDeclaration declaration =
          pIastFunctionCallStatement.getFunctionCallExpression().getDeclaration();
      if (declaration != null) {
        declaration.accept(this);
      }

      return null;
    }

    @Override
    public Void visit(CReturnStatement pNode) {

      Optional<CExpression> optExpression = pNode.getReturnValue();

      if (optExpression.isPresent()) {
        return optExpression.orElseThrow().accept(this);
      } else {
        return null;
      }
    }
  }
}