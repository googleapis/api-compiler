/*
 * Copyright (C) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.api.tools.framework.aspects;

import com.google.api.Service;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.stages.Linted;
import com.google.api.tools.framework.model.testing.ConfigBaselineTestCase;
import com.google.api.tools.framework.processors.normalizer.Normalizer;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A test base class for config aspect testing. Runs all phases of an aspect and outputs only
 * information to the baseline relevant for the given aspect.
 */
public abstract class ConfigAspectBaselineTestCase extends ConfigBaselineTestCase {

  private final Class<? extends ConfigAspectBase> aspectType;
  protected ConfigAspectBase testedAspect;
  private final List<Class<? extends ConfigAspectBase>> baselineAspectTypes = Lists.newArrayList();
  protected boolean printExistingYamlConfigInOutput = false;
  protected ConfigAspectBaselineTestCase(Class<? extends ConfigAspectBase> type) {
    this.aspectType = type;
    this.showDiagLocation = false;
  }

  @Override
  protected boolean suppressDiagnosis() {
    // Suppress diagnosis output to the baseline from the base class, we do it ourselves.
    return true;
  }

  /**
   * Adds an aspect for which, in addition to the tested aspect, we want to see baseline
   * output of normalization results.
   */
  public void addBaselineAspect(Class<? extends ConfigAspectBase> type) {
    baselineAspectTypes.add(type);
  }

  @Override
  protected Object run() throws Exception {

    // Find the aspect we are testing.
    testedAspect = findAspect(aspectType);

    // Create the list of aspects we want to see baseline output for.
    List<ConfigAspect> baselineAspects = Lists.newArrayList();
    baselineAspects.add(testedAspect);
    for (Class<?> type : baselineAspectTypes) {
      baselineAspects.add(findAspect(type));
    }

    // Establish linted stage. This will run merge and lint on all registered aspects, as we
    // can't merge aspects independently.
    model.establishStage(Linted.KEY);

    // Write out any diagnosis messages produced by the tested aspects, plus any errors.
    // We need to write errors even if they don't belong to the aspect, because
    // we will stop processing on errors, and the baseline should contain clues why.
    Pattern messagePattern = Pattern.compile(
        Joiner.on('|').join(FluentIterable.from(baselineAspects)
            .transform(new Function<ConfigAspect, String>() {
              @Override public String apply(ConfigAspect aspect) {
                return "(" + getMessagePattern(aspect) + ")";
              }
            })), Pattern.DOTALL);
    int processedIndex = 0;
    for (final Diag diag : model.getDiagCollector().getDiags()) {
      ++processedIndex;
      if (diag.getKind() != Diag.Kind.ERROR
          && !messagePattern.matcher(diag.getMessage()).matches()) {
        continue;
      }
      printDiag(diag);
    }
    if (model.getDiagCollector().hasErrors()) {
      // Don't continue with normalization because that could crash.
      return null;
    }

    // Run normalization for all aspects we want to see output for.
    // We don't need to run the other normalizers because they are independent.
    // Also note we work with an empty config in the builder instead of a copy of
    // the original one, so baseline output is more compact.
    final Service.Builder config =
        printExistingYamlConfigInOutput
            ? model.getServiceConfig().toBuilder()
            : Service.newBuilder();

    new Normalizer().normalizeAspects(model, baselineAspects, config);

    // Start from processedIndex so that we don't print the warnings that we already printed out.
    List<Diag> diags = model.getDiagCollector().getDiags();
    for (int i = processedIndex; i < diags.size(); ++i) {
      final Diag diag = diags.get(i);
      if (diag.getKind() != Diag.Kind.ERROR
          && !messagePattern.matcher(diag.getMessage()).matches()) {
        continue;
      }
     printDiag(diag);
    }
    if (model.getDiagCollector().hasErrors()) {
      // Don't continue because that could crash.
      return null;
    }

    return toBaselineString(config.build());
  }

  // Returns a pattern to match messages of given aspect.
  private String getMessagePattern(ConfigAspect aspect) {
    return String.format(".*%s[-:].*", aspect.getAspectName());
  }

  private ConfigAspectBase findAspect(Class<?> type) {
    for (ConfigAspect aspect : model.getConfigAspects()) {
      if (aspect.getClass() == type) {
        return (ConfigAspectBase) aspect;
      }
    }
    throw new IllegalStateException("aspect not found: " + type.getName());
  }
}
