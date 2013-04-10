/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.z3;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sosy_lab.cpachecker.util.predicates.z3.Z3NativeApi.*;
import static org.sosy_lab.cpachecker.util.predicates.z3.Z3NativeApiConstants.*;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import org.sosy_lab.common.NestedTimer;
import org.sosy_lab.common.Timer;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.AbstractionManager.RegionCreator;
import org.sosy_lab.cpachecker.util.predicates.Model;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.ProverEnvironment;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;

import com.google.common.base.Preconditions;

public class Z3TheoremProver implements ProverEnvironment {

  private static int counter = 0;
  private int level = 0;

  private Z3FormulaManager mgr;
  long z3context;
  long z3solver;

  public Z3TheoremProver(Z3FormulaManager mgr) {
    System.out.println("TP INIT in");

    this.mgr = mgr;
    this.z3context = mgr.getContext();
    this.z3solver = mk_solver(z3context);
    solver_inc_ref(z3context, z3solver);
    System.out.println("TP INIT out");
  }

  @Override
  public void push(BooleanFormula pF) {
    counter++;
    level++;
    System.out.println("        TP PUSH in, to LEVEL" + level);

    Preconditions.checkArgument(z3context != 0);

    solver_push(z3context, z3solver);
    long e = Z3FormulaManager.getZ3Expr(pF);
    System.out.println("        TP ASSERT " + counter); //+ " " + ast_to_string(z3context, e));

    solver_assert(z3context, z3solver, e);
    System.out.println("        TP PUSH out");
  }

  @Override
  public void pop() {
    level--;
    System.out.println("        TP POP in, from LEVEL" + level);

    assert (solver_get_num_scopes(z3context, z3solver) >= 1);
    solver_pop(z3context, z3solver, 1);
    System.out.println("        TP POP out");
  }

  @Override
  public boolean isUnsat() {
    System.out.println("        TP CHECK in");

    int result = solver_check(z3context, z3solver);
    assert (result != Z3_L_UNDEF);
    System.out.println("        TP CHECK out");

    return result == Z3_L_FALSE;
  }

  @Override
  public Model getModel() throws SolverException {
    System.out.println("        TP MODEL in");

    Z3Model model = new Z3Model(mgr, z3context, z3solver);
    Model m = model.createZ3Model();

    System.out.println("        TP MODEL out");

    return m;
  }

  @Override
  public void close() {
    System.out.println("TP CLOSE in");

    assert (z3context != 0);
    assert (z3solver != 0);
    //solver_reset(z3context, z3solver);
    solver_dec_ref(z3context, z3solver);
    // del_context(z3context); //TODO delete context? is it used somewhere else?
    z3context = 0;
    z3solver = 0;
    System.out.println("TP CLOSE out");
  }

  @Override
  public AllSatResult allSat(Collection<BooleanFormula> formulas,
      RegionCreator rmgr, Timer solveTime, NestedTimer enumTime) {
    System.out.println("        TP ALLSAT in");

    checkNotNull(rmgr);
    checkNotNull(solveTime);
    checkNotNull(enumTime);
    Preconditions.checkArgument(z3context != 0);

    long allSatZ3solver = z3solver; // mk_solver(allSatZ3context);
    solver_inc_ref(z3context, allSatZ3solver);

    // create new allSatResult
    Z3AllSatResult result = new Z3AllSatResult(rmgr, solveTime, enumTime);

    // unpack formulas to terms
    long[] importantTerms = new long[formulas.size()];
    int i = 0;
    for (BooleanFormula impF : formulas) {
      importantTerms[i++] = Z3FormulaManager.getZ3Expr(impF);
    }

    solveTime.start();
    int numModels = 0;
    while (solver_check(z3context, allSatZ3solver) == Z3_L_TRUE) {
      long[] model = new long[importantTerms.length];

      long z3model = solver_get_model(z3context, allSatZ3solver);
      for (int j = 0; j < importantTerms.length; j++) {
        long funcDecl = get_app_decl(z3context, importantTerms[j]);
        inc_ref(z3context, funcDecl);
        //        System.out.println(ast_to_string(z3context, funcDecl));
        //        System.out.println("app");

        long valueOfExpr = model_get_const_interp(z3context, z3model, funcDecl);
        //        System.out.println("val");
        if (valueOfExpr == Z3_OP_FALSE) {
          model[j] = mk_not(z3context, importantTerms[j]);
        } else {
          model[j] = importantTerms[j];
        }
      }

      // add model to BDD
      result.callback(model);

      long notTerm = mk_not(z3context, mk_and(z3context, model));

      numModels++;
      solver_push(z3context, allSatZ3solver);
      solver_assert(z3context, allSatZ3solver, notTerm);
    }

    if (solveTime.isRunning()) {
      solveTime.stop();
    } else {
      enumTime.stopOuter();
    }

    // we pushed some levels on assertionStack, remove them and delete solver
    solver_pop(z3context, allSatZ3solver, numModels);
    solver_dec_ref(z3context, allSatZ3solver);

    System.out.println("        TP ALLSAT out");

    return result;
  }


  /**
   * this class is used to build the predicate abstraction of a formula
   */
  class Z3AllSatResult implements AllSatResult {

    private final RegionCreator rmgr;

    private final Timer solveTime;
    private final NestedTimer enumTime;
    private Timer regionTime = null;

    private int count = 0;

    private Region formula;
    private final Deque<Region> cubes = new ArrayDeque<>();

    public Z3AllSatResult(RegionCreator rmgr, Timer pSolveTime, NestedTimer pEnumTime) {
      this.rmgr = rmgr;
      this.formula = rmgr.makeFalse();
      this.solveTime = pSolveTime;
      this.enumTime = pEnumTime;
    }

    /*
         public void setInfiniteNumberOfModels() {
          count = Integer.MAX_VALUE;
          cubes.clear();
          formula = rmgr.makeTrue();
        }
    */

    @Override
    public int getCount() {
      return count;
    }

    @Override
    public Region getResult() {
      if (cubes.size() > 0) {
        buildBalancedOr();
      }
      return formula;
    }

    private void buildBalancedOr() {
      cubes.add(formula);
      while (cubes.size() > 1) {
        Region b1 = cubes.remove();
        Region b2 = cubes.remove();
        cubes.add(rmgr.makeOr(b1, b2));
      }
      assert (cubes.size() == 1);
      formula = cubes.remove();
    }

    public void callback(long[] model) {
      if (count == 0) {
        solveTime.stop();
        enumTime.startOuter();
        regionTime = enumTime.getInnerTimer();
      }

      regionTime.start();

      // the abstraction is created simply by taking the disjunction
      // of all the models found by the all-sat-loop, and storing them in a BDD
      // first, let's create the BDD corresponding to the model
      Deque<Region> curCube = new ArrayDeque<>(model.length + 1);
      Region m = rmgr.makeTrue();
      for (long t : model) {
        Region region;
        if (isOP(z3context, t, Z3_OP_NOT)) {
          t = get_app_arg(z3context, t, 0);
          region = rmgr.getPredicate(encapsulate(t));
          region = rmgr.makeNot(region);
        } else {
          region = rmgr.getPredicate(encapsulate(t));
        }
        curCube.add(region);
      }
      // now, add the model to the bdd
      curCube.add(m);
      while (curCube.size() > 1) {
        Region v1 = curCube.remove();
        Region v2 = curCube.remove();
        curCube.add(rmgr.makeAnd(v1, v2));
      }
      assert (curCube.size() == 1);
      m = curCube.remove();
      cubes.add(m);

      count++;

      regionTime.stop();
    }

    private BooleanFormula encapsulate(long pT) {
      return mgr.encapsulate(BooleanFormula.class, pT);
    }
  }
}
