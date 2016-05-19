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

package com.google.api.tools.framework.processors.resolver;

import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.Processor;
import com.google.api.tools.framework.model.SymbolTable;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;

/**
 * A processor which establishes the {@link Resolved} stage.
 *
 * <p>
 * The resolver does a proper context check on the model constructed from a protocol descriptor.
 * There is not assumption that the protocol compiler would have run before us, allowing tools
 * building the model from descriptors which are inconsistent, and getting proper error messages
 * instead of crashes.
 */
public class Resolver implements Processor {

  @Override
  public ImmutableList<Key<?>> requires() {
    return ImmutableList.of();
  }

  @Override
  public Key<?> establishes() {
    return Resolved.KEY;
  }

  @Override
  public boolean run(Model model) {
    int oldErrorCount = model.getDiagCollector().getErrorCount();
    SymbolTable symbolTable = new SymbolTableBuilder(model).run();
    model.setSymbolTable(symbolTable);
    new ReferenceResolver(model, symbolTable).run();
    if (oldErrorCount == model.getDiagCollector().getErrorCount()) {
      // No new errors produced -- success.
      model.putAttribute(Resolved.KEY, new Resolved());
      return true;
    }
    return false;
  }
}
