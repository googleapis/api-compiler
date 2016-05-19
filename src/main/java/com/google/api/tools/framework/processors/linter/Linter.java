/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.api.tools.framework.processors.linter;

import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.Processor;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.Visitor;
import com.google.api.tools.framework.model.stages.Linted;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;

/**
 * Linter for IDL and service config. Delegates work to config aspects.
 */
public class Linter implements Processor {

  @Override
  public ImmutableList<Key<?>> requires() {
    return ImmutableList.<Key<?>>of(Merged.KEY);
  }

  @Override
  public Key<?> establishes() {
    return Linted.KEY;
  }

  @Override
  public boolean run(final Model model) {
    int oldErrorCount = model.getDiagCollector().getErrorCount();

    for (ConfigAspect aspect : model.getConfigAspects()) {
      aspect.startLinting();
    }
    new Visitor(model.getScoper()) {
      @VisitsBefore void validate(ProtoElement element) {
        for (ConfigAspect aspect : model.getConfigAspects()) {
          aspect.lint(element);
        }
      }
    }.visit(model);

    for (ConfigAspect aspect : model.getConfigAspects()) {
      aspect.endLinting();
    }

    if (oldErrorCount == model.getDiagCollector().getErrorCount()) {
      // No new errors produced -- success.
      model.putAttribute(Linted.KEY, new Linted());
      return true;
    }
    return false;
  }
}
