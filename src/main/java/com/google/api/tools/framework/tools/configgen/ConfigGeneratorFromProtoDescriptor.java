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

package com.google.api.tools.framework.tools.configgen;

import com.google.api.Service;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.tools.ToolDriverBase;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * This tool generate the normalized config for an API service and output it in both binary and text
 * proto form.
 */
public class ConfigGeneratorFromProtoDescriptor extends ToolDriverBase implements ConfigGenerator {
  public static final Option<String> NAME =
      ToolOptions.createOption(
          String.class,
          "name",
          "The name of the output normalized config files, without suffix.",
          "");

  public static final Option<Boolean> SUPPRESS_WARNINGS =
      ToolOptions.createOption(
          boolean.class,
          "suppress_warnings",
          "True if lint warnings need to be suppressed when building the service config.",
          false);

  public ConfigGeneratorFromProtoDescriptor(ToolOptions options) {
    super(options);
  }

  @Nullable
  public Service getServiceConfig() {
    if (model != null) {
      return model.getNormalizedConfig();
    }
    return null;
  }

  @Override
  protected void process() throws IOException {

    // Suppress all lint warnings if the flag is true.
    if (options.get(SUPPRESS_WARNINGS)) {
      model.suppressAllWarnings();
    }

    // Generate the normalized config.
    model.establishStage(Normalized.KEY);
  }

  /**
   * An empty method to ensure statics in this class are initialized even if a constructor has not
   * yet been called.
   */
  static void ensureStaticsInitialized() {}

  @Override
  @Nullable
  public Service generateServiceConfig() throws IOException {
    int exitCode = super.run();
    if (exitCode == 1) {
      return null;
    } else {
      return super.getModel().getNormalizedConfig();
    }
  }

}
