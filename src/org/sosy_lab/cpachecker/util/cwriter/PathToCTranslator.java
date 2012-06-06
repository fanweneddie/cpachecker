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
package org.sosy_lab.cpachecker.util.cwriter;

import static com.google.common.collect.Iterables.concat;
import static org.sosy_lab.cpachecker.util.AbstractStates.extractLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.IASTNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAFunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.MultiEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.AssumeEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.CallToReturnEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.DeclarationEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionDefinitionNode;
import org.sosy_lab.cpachecker.cfa.objectmodel.c.FunctionReturnEdge;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.Path;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class PathToCTranslator {

  private static Function<IASTNode, String> RAW_SIGNATURE_FUNCTION = new Function<IASTNode, String>() {
    @Override
    public String apply(IASTNode pArg0) {
      return pArg0.toASTString();
    }
  };

  private final List<String> mGlobalDefinitionsList = new ArrayList<String>();
  private final List<String> mFunctionDecls = new ArrayList<String>();
  private int mFunctionIndex = 0;

  // list of functions
  private final List<FunctionBody> mFunctionBodies = new ArrayList<FunctionBody>();

  private PathToCTranslator() { }

  public static String translatePaths(ARGState argRoot, Set<ARGState> elementsOnErrorPath) {
    PathToCTranslator translator = new PathToCTranslator();

    translator.translatePaths0(argRoot, elementsOnErrorPath);

    return translator.generateCCode();
  }

  public static String translateSinglePath(Path pPath) {
    PathToCTranslator translator = new PathToCTranslator();

    translator.translateSinglePath0(pPath);

    return translator.generateCCode();
  }

  private String generateCCode() {
    List<String> includeList = new ArrayList<String>();

    // do not include stdlib.h, as some examples (ntdrivers) define
    // "typedef unsigned short wchar_t;" that is also defined in stdlib.h
    // as "typedef int wchar_t;" - these contradicting definitions make cbmc fail
//  includeList.add("#include<stdlib.h>");
    includeList.add("#include<stdio.h>");

    return Joiner.on('\n').join(concat(includeList,
                                       mGlobalDefinitionsList,
                                       mFunctionDecls,
                                       mFunctionBodies));
  }


  private void translatePaths0(final ARGState firstElement, Set<ARGState> elementsOnPath) {
    // waitlist for the edges to be processed
    List<Edge> waitlist = new ArrayList<Edge>();

    // map of nodes to check end of a condition
    Map<Integer, MergeNode> mergeNodes = new HashMap<Integer, MergeNode>();

    // create initial element
    {
      Stack<FunctionBody> newStack = new Stack<FunctionBody>();

      // create the first function and put in into newStack
      startFunction(firstElement, newStack);

      waitlist.addAll(getRelevantChildrenOfState(firstElement, newStack, elementsOnPath));
    }

    while (!waitlist.isEmpty()) {
      // we need to sort the list based on arg element id because we have to process
      // the edges in topological sort
      Collections.sort(waitlist);

      // get the first element in the list (this is the smallest element when topologically sorted)
      Edge nextEdge = waitlist.remove(0);

      waitlist.addAll(handleEdge(nextEdge, mergeNodes, elementsOnPath));
    }
  }

  private String startFunction(ARGState firstFunctionElement, Stack<FunctionBody> functionStack) {
    // create the first stack element using the first element of the function
    FunctionDefinitionNode functionStartNode = (FunctionDefinitionNode)extractLocation(firstFunctionElement);
    String freshFunctionName = getFreshFunctionName(functionStartNode);

    String lFunctionHeader = functionStartNode.getFunctionDefinition().getDeclSpecifier().toASTString(freshFunctionName);
    // lFunctionHeader is for example "void foo_99(int a)"

    // create a new function
    FunctionBody newFunction = new FunctionBody(firstFunctionElement.getStateId(),
        lFunctionHeader);

    // register function
    mFunctionDecls.add(lFunctionHeader + ";");
    mFunctionBodies.add(newFunction);
    functionStack.push(newFunction); // add function to current stack
    return freshFunctionName;
  }

  private void translateSinglePath0(Path pPath) {
    assert pPath.size() >= 1;
    Iterator<Pair<ARGState, CFAEdge>> pathIt = pPath.iterator();
    Pair<ARGState, CFAEdge> parentPair = pathIt.next();
    ARGState firstElement = parentPair.getFirst();

    Stack<FunctionBody> functionStack = new Stack<FunctionBody>();

    // create the first function and put in into the stack
    startFunction(firstElement, functionStack);

    while (pathIt.hasNext()) {
      Pair<ARGState, CFAEdge> nextPair = pathIt.next();

      CFAEdge currentCFAEdge = parentPair.getSecond();
      ARGState childElement = nextPair.getFirst();

      processEdge(childElement, currentCFAEdge, functionStack);

      parentPair = nextPair;
    }
  }

  private Collection<Edge> handleEdge(Edge nextEdge, Map<Integer, MergeNode> mergeNodes, Set<ARGState> elementsOnPath) {
    ARGState childElement = nextEdge.getChildState();
    CFAEdge edge = nextEdge.getEdge();
    Stack<FunctionBody> functionStack = nextEdge.getStack();

    // clone stack to have a different representation of the function calls and conditions
    // for every element
    functionStack = cloneStack(functionStack);

    processEdge(childElement, edge, functionStack);

    // how many parents does the child have?
    // ignore parents not on the error path
    int noOfParents = Sets.intersection(childElement.getParents(), elementsOnPath).size();
    assert noOfParents >= 1;

    // handle merging if necessary
    if (noOfParents > 1) {
      assert !(   (edge instanceof FunctionCallEdge)
               || (childElement.isTarget()));

      // this is the end of a condition, determine whether we should continue or backtrack

      int elemId = childElement.getStateId();
      FunctionBody currentFunction = functionStack.peek();
      currentFunction.write("goto label_" + elemId + ";");

      // get the merge node for that node
      MergeNode mergeNode = mergeNodes.get(elemId);
      // if null create new and put in the map
      if (mergeNode == null) {
        mergeNode = new MergeNode(elemId);
        mergeNodes.put(elemId, mergeNode);
      }

      // this tells us the number of edges (entering that node) processed so far
      int noOfProcessedBranches = mergeNode.addBranch(currentFunction);

      // if all edges are processed
      if (noOfParents == noOfProcessedBranches) {
        // all branches are processed, now decide which nodes to remove from the stack
        List<FunctionBody> incomingStacks = mergeNode.getIncomingStates();

        FunctionBody newFunction = processIncomingStacks(incomingStacks);

        // replace the current function body with the right one
        functionStack.pop();
        functionStack.push(newFunction);

        newFunction.write("label_" + elemId + ": ;");

      } else {
        return Collections.emptySet();
      }
    }

    return getRelevantChildrenOfState(childElement, functionStack, elementsOnPath);
  }

  private Collection<Edge> getRelevantChildrenOfState(
      ARGState currentElement, Stack<FunctionBody> functionStack,
      Set<ARGState> elementsOnPath) {
    // find the next elements to add to the waitlist

    Set<ARGState> relevantChildrenOfElement = Sets.intersection(currentElement.getChildren(), elementsOnPath).immutableCopy();

    // if there is only one child on the path
    if (relevantChildrenOfElement.size() == 1) {
      // get the next ARG state, create a new edge using the same stack and add it to the waitlist
      ARGState elem = Iterables.getOnlyElement(relevantChildrenOfElement);
      CFAEdge e = currentElement.getEdgeToChild(elem);
      Edge newEdge = new Edge(elem, e, functionStack);
      return Collections.singleton(newEdge);

    } else if (relevantChildrenOfElement.size() > 1) {
      // if there are more than one relevant child, then this is a condition
      // we need to update the stack
      assert relevantChildrenOfElement.size() == 2;
      Collection<Edge> result = new ArrayList<Edge>(2);
      int ind = 0;
      for (ARGState elem: relevantChildrenOfElement) {
        Stack<FunctionBody> newStack = cloneStack(functionStack);
        CFAEdge e = currentElement.getEdgeToChild(elem);
        FunctionBody currentFunction = newStack.peek();
        assert e instanceof AssumeEdge;
        AssumeEdge assumeEdge = (AssumeEdge)e;
        boolean truthAssumption = assumeEdge.getTruthAssumption();

        String cond = "";

        if (ind == 0) {
          cond = "if ";
        } else if (ind == 1) {
          cond = "else if ";
        } else {
          assert false;
        }
        ind++;

        if (truthAssumption) {
          cond += "(" + assumeEdge.getExpression().toASTString() + ")";
        } else {
          cond += "(!(" + assumeEdge.getExpression().toASTString() + "))";
        }

        // create a new block starting with this condition
        currentFunction.enterBlock(currentElement.getStateId(), assumeEdge, cond);

        Edge newEdge = new Edge(elem, e, newStack);
        result.add(newEdge);
      }
      return result;
    }
    return Collections.emptyList();
  }

  private static FunctionBody processIncomingStacks(
      List<FunctionBody> pIncomingStacks) {

    FunctionBody maxStack = null;
    int maxSizeOfStack = 0;

    for (FunctionBody stack: pIncomingStacks) {
      while (true) {
        if (stack.getCurrentBlock().isClosedBefore()) {
          stack.leaveBlock();
        } else {
          break;
        }
      }
      if (stack.size() > maxSizeOfStack) {
        maxStack = stack;
        maxSizeOfStack = maxStack.size();
      }
    }

    return maxStack;

  }


  private void processEdge(ARGState childElement, CFAEdge edge, Stack<FunctionBody> functionStack) {
    FunctionBody currentFunction = functionStack.peek();

    if (childElement.isTarget()) {
      currentFunction.write("assert(0); // target state ");
    }

    // handle the edge

    if (edge instanceof FunctionCallEdge) {
      // if this is a function call edge we need to create a new state and push
      // it to the topmost stack to represent the function

      // create function and put in onto stack
      String freshFunctionName = startFunction(childElement, functionStack);

      // write summary edge to the caller site (with the new unique function name)
      currentFunction.write(processFunctionCall(edge, freshFunctionName));

    } else if (edge instanceof FunctionReturnEdge) {
      functionStack.pop();

    } else {
      currentFunction.write(processSimpleEdge(edge));
    }
  }

  private String processSimpleEdge(CFAEdge pCFAEdge) {

    switch (pCFAEdge.getEdgeType()) {

    case BlankEdge:
    case StatementEdge:
    case ReturnStatementEdge:
      return pCFAEdge.getCode();

    case AssumeEdge: {
      AssumeEdge lAssumeEdge = (AssumeEdge)pCFAEdge;
      return ("__CPROVER_assume(" + lAssumeEdge.getCode() + ");");
//    return ("if(! (" + lAssumptionString + ")) { return (0); }");
    }

    case DeclarationEdge: {
      DeclarationEdge lDeclarationEdge = (DeclarationEdge)pCFAEdge;

      if (lDeclarationEdge.getDeclaration().isGlobal()) {
        mGlobalDefinitionsList.add(lDeclarationEdge.getCode());
        return "";
      }

      return lDeclarationEdge.getCode();
    }

    case MultiEdge: {
      StringBuilder sb = new StringBuilder();

      for (CFAEdge edge : (MultiEdge)pCFAEdge) {
        sb.append(processSimpleEdge(edge));
      }
      return sb.toString();
    }

    default:
      throw new AssertionError("Unexpected edge " + pCFAEdge + " of type " + pCFAEdge.getEdgeType());
    }
  }

  private String processFunctionCall(CFAEdge pCFAEdge, String functionName) {

    FunctionCallEdge lFunctionCallEdge = (FunctionCallEdge)pCFAEdge;

    List<String> lArguments = Lists.transform(lFunctionCallEdge.getArguments(), RAW_SIGNATURE_FUNCTION);
    String lArgumentString = "(" + Joiner.on(", ").join(lArguments) + ")";

    CallToReturnEdge summaryEdge = lFunctionCallEdge.getPredecessor().getLeavingSummaryEdge();
    if (summaryEdge == null) {
      // no summary edge, i.e., no return to this function (CFA was pruned)
      // we don't need to care whether this was an assignment or just a function call
      return functionName + lArgumentString + ";";
    }

    IASTFunctionCall expressionOnSummaryEdge = summaryEdge.getExpression();
    if (expressionOnSummaryEdge instanceof IASTFunctionCallAssignmentStatement) {
      IASTFunctionCallAssignmentStatement assignExp = (IASTFunctionCallAssignmentStatement)expressionOnSummaryEdge;
      String assignedVarName = assignExp.getLeftHandSide().toASTString();
      return assignedVarName + " = " + functionName + lArgumentString + ";";

    } else {
      assert expressionOnSummaryEdge instanceof IASTFunctionCallStatement;
      return functionName + lArgumentString + ";";
    }
  }

  private String getFreshFunctionName(CFAFunctionDefinitionNode functionStartNode) {
    return functionStartNode.getFunctionName() + "_" + mFunctionIndex++;
  }

  private Stack<FunctionBody> cloneStack(Stack<FunctionBody> pStack) {

    Stack<FunctionBody>  ret = new Stack<FunctionBody>();
    for (FunctionBody functionBody : pStack) {
      ret.push(new FunctionBody(functionBody));
    }
    return ret;
  }
}