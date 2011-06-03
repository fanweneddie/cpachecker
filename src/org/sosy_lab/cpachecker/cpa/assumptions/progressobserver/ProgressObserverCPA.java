/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.assumptions.progressobserver;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.sosy_lab.common.Classes;
import org.sosy_lab.common.Classes.ClassInstantiationException;
import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFANode;
import org.sosy_lab.cpachecker.core.defaults.AutomaticCPAFactory;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.CPAFactory;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

@Options(prefix="cpa.assumptions.progressobserver")
public class ProgressObserverCPA implements ConfigurableProgramAnalysis {

  public static CPAFactory factory() {
    return AutomaticCPAFactory.forType(ProgressObserverCPA.class);
  }

  @Option(name="heuristics", required=true,
      description="which heuristics should be used to track progress?")
  private String[] heuristicsNames = {};

  private final ProgressObserverDomain abstractDomain;
  private final StopOperator stopOperator;
  private final TransferRelation transferRelation;
  private final PrecisionAdjustment precisionAdjustment;
  private final LogManager logger;

  private final ImmutableList<StopHeuristics<?>> enabledHeuristics;

  /** Return the immutable list of enables heuristics */
  public ImmutableList<StopHeuristics<?>> getEnabledHeuristics()
  {
    return enabledHeuristics;
  }

  private ImmutableList<StopHeuristics<?>> createEnabledHeuristics(Configuration config)
              throws InvalidConfigurationException {
    ImmutableList.Builder<StopHeuristics<?>> builder = ImmutableList.builder();

    Class<?>[] argsTypes = {Configuration.class, LogManager.class};
    for (String heuristicsName : heuristicsNames) {
      if (!heuristicsName.contains(".")) {
        heuristicsName = "org.sosy_lab.cpachecker.cpa.assumptions.progressobserver.heuristics." + heuristicsName;
      }
      try {
        Class<?> cls = Class.forName(heuristicsName);
        Object[] localArgs = {config, logger};
        StopHeuristics<?> newHeuristics = Classes.createInstance(cls, argsTypes, localArgs, StopHeuristics.class);
        builder.add(newHeuristics);
      } catch (ClassNotFoundException e) {
        throw new InvalidConfigurationException("Heuristic " + heuristicsName + " does not exist");

      } catch (InvocationTargetException e) {
        Throwable t = e.getCause();
        Throwables.propagateIfPossible(t, InvalidConfigurationException.class);
        throw new InvalidConfigurationException("Heuristic " + heuristicsName + " could not be instantiated (" + t.getMessage() + ")");

      } catch (ClassInstantiationException e) {
        throw new InvalidConfigurationException("Invalid heuristic " + heuristicsName + " (" + e.getMessage() + ")");
      }
    }

    return builder.build();
  }

  private ProgressObserverCPA(Configuration cfg, LogManager mgr) throws InvalidConfigurationException
  {
    logger = mgr;
    cfg.inject(this);

    enabledHeuristics = createEnabledHeuristics(cfg);

    abstractDomain = new ProgressObserverDomain();
    stopOperator = new ProgressObserverStop();
    transferRelation = new ProgressObserverTransferRelation(this);
    precisionAdjustment = new ProgressObserverPrecisionAdjustment(this);
  }

  @Override
  public ProgressObserverDomain getAbstractDomain() {
    return abstractDomain;
  }

  @Override
  public ProgressObserverElement getInitialElement(CFANode node) {
    List<StopHeuristicsData> data = new ArrayList<StopHeuristicsData>(enabledHeuristics.size());
    for (StopHeuristics<? extends StopHeuristicsData> h : enabledHeuristics) {
      data.add(h.getInitialData(node));
    }
    return new ProgressObserverElement(data);
  }

  @Override
  public Precision getInitialPrecision(CFANode pNode) {
    return SingletonPrecision.getInstance();
  }

  @Override
  public MergeOperator getMergeOperator() {
    return MergeSepOperator.getInstance();
  }

  @Override
  public PrecisionAdjustment getPrecisionAdjustment() {
    return precisionAdjustment;
  }

  @Override
  public StopOperator getStopOperator() {
    return stopOperator;
  }

  @Override
  public TransferRelation getTransferRelation() {
    return transferRelation;
  }

  public LogManager getLogger() {
    return logger;
  }

}
