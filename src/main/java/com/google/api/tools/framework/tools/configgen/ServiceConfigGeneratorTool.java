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

import com.google.api.tools.framework.tools.SwaggerToolDriverBase;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Main entry point for the service config generator.
 */
public class ServiceConfigGeneratorTool {
  public static void main(String[] args) throws ParseException, IOException {
    ConfigGeneratorDriver.ensureStaticsInitialized();
    ImmutableList<Option<?>> frameworkOptions =
        buildFrameworkOptions();
    Options apacheCliOptions = ToolOptions.convertToApacheCliOptions(frameworkOptions);
    CommandLine cmd = (new BasicParser()).parse(apacheCliOptions, args);
    if (ToolOptions.isHelpFlagSet(cmd)) {
      ToolOptions.printUsage("ServiceConfigGeneratorTool", apacheCliOptions);
      System.exit(0);
    }
    ToolOptions toolOptions = ToolOptions.getToolOptionsFromCommandLine(cmd, frameworkOptions);
    System.exit(new ConfigGeneratorDriver(toolOptions).run());
  }

  /**
   * Add all options that will be accepted on the command line.
   */
  private static ImmutableList<Option<?>> buildFrameworkOptions() {
    ImmutableList.Builder<Option<?>> frameworkOptions = new ImmutableList.Builder<Option<?>>();
    frameworkOptions.add(ToolOptions.DESCRIPTOR_SET);
    frameworkOptions.add(ToolOptions.CONFIG_FILES);
    frameworkOptions.add(ConfigGeneratorDriver.BIN_OUT);
    frameworkOptions.add(ConfigGeneratorDriver.TXT_OUT);
    frameworkOptions.add(ConfigGeneratorDriver.JSON_OUT);
    frameworkOptions.add(ConfigGeneratorFromProtoDescriptor.SUPPRESS_WARNINGS);
    frameworkOptions.add(SwaggerToolDriverBase.OPEN_API);
    frameworkOptions.add(SwaggerToolDriverBase.SERVICE_NAME);
    frameworkOptions.add(SwaggerToolDriverBase.TYPE_NAMESPACE);

    return frameworkOptions.build();
  }
}
