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
import com.google.api.tools.framework.tools.SwaggerToolDriverBase;
import com.google.api.tools.framework.tools.ToolOptions;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * This driver generates the normalized config based on swagger doc.
 */
public class ConfigGeneratorFromSwagger extends SwaggerToolDriverBase implements ConfigGenerator {

  public ConfigGeneratorFromSwagger(ToolOptions options) {
    super(options);
  }

  @Override
  @Nullable
  public Service generateServiceConfig() throws IOException {
    int exitCode = super.run();
    if (exitCode == 1) {
      return null;
    } else {
      return super.getServiceConfig();
    }
  }

  @Override
  public void process() throws Exception {
    // Do nothing as SwaggerToolDriverBase#run() has already generated the service config.
  }
}
