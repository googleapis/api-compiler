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

package com.google.api.tools.framework.aspects.servicecontrol;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Configuration aspect for service control config validation.
 */
public class ServiceControlConfigAspect extends ConfigAspectBase {
  public static ServiceControlConfigAspect create(Model model) {
    return new ServiceControlConfigAspect(model);
  }

  private ServiceControlConfigAspect(Model model) {
    super(model, "servicecontrol");
    for (String lintRuleName : ServiceControlConfigValidator.getLintRuleNames()) {
      registerLintRuleName(lintRuleName);
    }
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  /**
   * Validates control config section of the service config.
   */
  @Override
  public void endMerging() {
    // Validate service control config.
    ServiceControlConfigValidator.validate(this, getModel().getServiceConfig());
  }
}