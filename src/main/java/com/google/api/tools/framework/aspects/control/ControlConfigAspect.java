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

package com.google.api.tools.framework.aspects.control;

import com.google.api.Service;
import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.control.model.ControlConfigUtil;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * Configuration aspect for Control plane.
 */
public class ControlConfigAspect extends ConfigAspectBase {

  private static final String NO_CONTROL_ENV = "presence";

  private static final List<String> SUPPORTED_ENVS = Lists.newArrayList(
      ControlConfigUtil.ENDPOINTS_SERVICE_CONTROL,
      ControlConfigUtil.PROD_SERVICE_CONTROL);

  private final String environment;

  public static ControlConfigAspect create(Model model) {
    return new ControlConfigAspect(model);
  }

  private ControlConfigAspect(Model model) {
    super(model, "control");
    environment = model.getControlEnvironment();
    registerLintRuleName(NO_CONTROL_ENV);
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  public void startMerging() {
    if (!Strings.isNullOrEmpty(environment)) {
      if (!SUPPORTED_ENVS.contains(environment)) {
        error(getModel(), "Control environment '%s' is not one of the supported environments: %s",
            environment, SUPPORTED_ENVS);
      }
    } else {
      lintWarning(NO_CONTROL_ENV, getModel(),
          "Service %s does not have control environment configured.",
          getModel().getServiceConfig().getName());
    }
  }

}
