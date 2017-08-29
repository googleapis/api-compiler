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

package com.google.api.tools.framework.aspects.versioning.linters;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;

/** Checks if config_version value is not less than Model.CURRENT_CONFIG_DEFAULT_VERSION. */
public class ConfigVersionRule extends LintRule<Model> {

  public ConfigVersionRule(ConfigAspectBase aspect) {
    super(aspect, "config", Model.class);
  }

  @Override public void run(Model model) {
    if (model.getServiceConfig().hasConfigVersion()
        && model.getConfigVersion() != Model.getDefaultConfigVersion()) {
      warning(
          MessageLocationContext.create(model.getServiceConfig().getConfigVersion(), "value"),
          "Specified config_version value '%s' is not equal to "
              + "the current default value '%s'. Consider changing this value "
              + "to the default config version.",
          model.getConfigVersion(),
          Model.getDefaultConfigVersion());
    }
  }
}
