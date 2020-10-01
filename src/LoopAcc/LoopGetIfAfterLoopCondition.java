/*CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2020  Dirk Beyer
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
 */
package LoopAcc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

/**
 * This class looks for if cases directly after loops since they aren't any different in the cfa to
 * the loop condition, so this class will look for it in the file itself
 */
public class LoopGetIfAfterLoopCondition {

  private FileLocation fileLocation;
  private String content;
  private int smallestLineNumber;
  private int biggestLineNumber;
  private ArrayList<Integer> linesWithIf;
  private int smallestIf;
  private LogManager logger;

  public LoopGetIfAfterLoopCondition(ArrayList<CFANode> nodes, LogManager pLogger) {
    logger = pLogger;
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).getNumLeavingEdges() > 0) {
    fileLocation = nodes.get(0).getLeavingEdge(0).getFileLocation();
    break;
  }
}
    content = "";
    smallestLineNumber = getSmallestLineNumber(nodes);
    biggestLineNumber = getBiggestLineNumber(nodes);
    linesWithIf = new ArrayList<>();
    readFile();
    smallestIf = findSmallestIfLineNumber();
  }

  private String readFile() {
    try {
      FileReader freader = new FileReader(fileLocation.getFileName());
      BufferedReader reader = new BufferedReader(freader);
      String line = "";
      int lineNumber = 1;
      while (line != null) {
        line = reader.readLine();
        if (lineNumber >= smallestLineNumber && lineNumber <= biggestLineNumber && line != null) {
          findIf(line, lineNumber);
        content = content + line + System.lineSeparator();
        }
        lineNumber++;
      }
    } catch (IOException e) {
      logger.logUserException(
          Level.WARNING,
          e,
          "Something is not working with the file you try to import");
    }
    return content;
  }

  private int findSmallestIfLineNumber() {
    if (!linesWithIf.isEmpty()) {
      int small = linesWithIf.get(0);
      for (Integer v : linesWithIf) {
        if (v < small) {
          small = v;
        }
      }
      return small;
    } else {
      return -1;
    }
  }

  private void findIf(String text, int lineNumber) {
    String temp = text.split("\\(")[0];
    if (temp.contains("if")) {
      linesWithIf.add(lineNumber);
    }
  }

  private int getSmallestLineNumber(ArrayList<CFANode> nodes) {
    int small = -1;
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).getNumLeavingEdges() > 0) {
        small = nodes.get(i).getLeavingEdge(0).getFileLocation().getStartingLineInOrigin();
        break;
      }
    }
    if (small != -1) {
    for (CFANode node : nodes) {
      if (node.getLeavingEdge(0).getFileLocation().getStartingLineInOrigin() < small) {
        small = node.getLeavingEdge(0).getFileLocation().getStartingLineInOrigin();
      }
    }
  }
    return small;
  }

  private int getBiggestLineNumber(ArrayList<CFANode> nodes) {
    int big = -1;
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).getNumLeavingEdges() > 0) {
        big = nodes.get(i).getLeavingEdge(0).getFileLocation().getStartingLineInOrigin();
        break;
      }
    }
    if (big != -1) {
    for (CFANode node : nodes) {
      if (node.getLeavingEdge(0).getFileLocation().getStartingLineInOrigin() > big) {
        big = node.getLeavingEdge(0).getFileLocation().getStartingLineInOrigin();
      }
    }
  }
    return big;
  }

  public int getSmallestIf() {
    return smallestIf;
  }

}