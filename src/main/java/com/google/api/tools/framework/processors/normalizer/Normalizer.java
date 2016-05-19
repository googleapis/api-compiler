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

package com.google.api.tools.framework.processors.normalizer;

import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.Processor;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.Visitor;
import com.google.api.tools.framework.model.stages.Linted;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;

/**
 * A processor that establishes the {@link Normalized} stage.
 * After {@link Normalized} stage, all wildcards in configuration
 * rules have been expanded, and proto elements (service, method, message, etc.)
 * will be propagated to individual elements in //tech/api/proto/service.proto.
 */
public class Normalizer implements Processor {

  @Override
  public ImmutableList<Key<?>> requires() {
    return ImmutableList.<Key<?>>of(Linted.KEY);
  }

  @Override
  public Key<?> establishes() {
    return Normalized.KEY;
  }

  @Override
  public boolean run(Model model) {
    Service.Builder normalizedConfig = model.getServiceConfig().toBuilder();

    // Normalize descriptor.
    new DescriptorNormalizer(model).run(normalizedConfig);

    // Run aspect normalizers.
    for (ConfigAspect aspect : model.getConfigAspects()) {
      aspect.startNormalization(normalizedConfig);
    }
    new AspectNormalizer(model, normalizedConfig).visit(model);
    for (ConfigAspect aspect : model.getConfigAspects()) {
      aspect.endNormalization(normalizedConfig);
    }

    // Set result.
    model.setNormalizedConfig(normalizedConfig.build());
    model.putAttribute(Normalized.KEY, new Normalized());
    return true;
  }

  private static class AspectNormalizer extends Visitor {

    private final Model model;
    private final Service.Builder normalizedConfig;

    private AspectNormalizer(Model model, Builder normalizedConfig) {
      super(model.getScoper(), false/*ignoreMapEntry*/);
      this.model = model;
      this.normalizedConfig = normalizedConfig;
    }

    @VisitsBefore void normalize(ProtoElement element) {
      for (ConfigAspect aspect : model.getConfigAspects()) {
        aspect.normalize(element, normalizedConfig);
      }
    }
  }
}
