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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.Service;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import com.google.api.tools.framework.tools.configgen.ConfigGenerator;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Main entry point for the Swagger Importer.
 */
public class SwaggerImportTool implements ConfigGenerator {
  public static final Option<String> TYPE_NAMESPACE = ToolOptions.createOption(
      String.class, "type_namespace", "(For Swagger Input Only) A namespace to use in generated "
          + "service config for all types. If provided, all type names will be prefixed by this"
          + " value and a dot ('.') separator.", "");

  public static final Option<String> METHOD_NAMESPACE = ToolOptions.createOption(String.class,
      "method_namespace", "(For Swagger Input Only) A namespace to use in generated service config "
          + "for all methods. If provided, all method names will be prefixed by this value and a "
          + "dot ('.') separator.", "");

  public static final Option<String> SERVICE_NAME = ToolOptions.createOption(String.class,
      "service_name", "(For Swagger Input Only) Service name to be used in the converted service "
          + "config.", "");

  public static final Option<String> OPEN_API = ToolOptions.createOption(String.class,
      "openapi", "Path to the OpenAPI spec to be converted into service config", "");

  private final ToolOptions options;

  private Service serviceConfig = null;
  private SwaggerToService tool;

  public SwaggerImportTool(ToolOptions options) {
    this.options = Preconditions.checkNotNull(options, "options cannot be null");
  }

  @Nullable
  public Service getServiceConfig() {
    return serviceConfig;
  }

  /*
   * Print diagnostic information.
   */
  private void printDiagnostics(List<Diag> list) {
    Predicate<Diag> error = new Predicate<Diag>() {
      @Override
      public boolean apply(Diag input) {
        return input.getKind() == Kind.ERROR;
      }
    };

    String errors = Joiner.on("\n  ").join(Iterables.filter(list, error));
    String warnings = Joiner.on("\n  ").join(Iterables.filter(list, Predicates.not(error)));

    if (!errors.isEmpty()) {
      System.out.printf("\nConversion encountered ERRORS:\n  %s\n", errors);
    }
    if (!warnings.isEmpty()) {
      System.out.printf("\nConversion encountered warnings:\n  %s\n", warnings);
    }
  }

  /**
   * An empty method to ensure statics in this class are initialized even if a constructor
   * has not yet been called.
   */
  public static void ensureStaticsInitialized() {
  }

  @Override
  public List<Diag> getDiags() {
    return tool.getDiags();
  }

  @Override
  public boolean hasErrors() {
    return tool.hasErrors();
  }

  @Override
  @Nullable
  public Service generateServiceConfig() throws IOException {
    ImmutableMap.Builder<String, String> fileContentMap = new ImmutableMap.Builder<>();
    File file = new File(options.get(OPEN_API));
    String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    fileContentMap.put(file.getName(), fileContent);

    ImmutableMap.Builder<String, String> additionalConfigsMap = ImmutableMap.builder();

    List<String> configFiles = options.get(ToolOptions.CONFIG_FILES);
    for (String additionalConfig : configFiles) {
      File config = new File(additionalConfig);
      String configContent = FileUtils.readFileToString(config, StandardCharsets.UTF_8);
      additionalConfigsMap.put(additionalConfig, configContent);
    }

    try {
      tool =
          new SwaggerToService(
              fileContentMap.build(),
              options.get(SERVICE_NAME),
              options.get(TYPE_NAMESPACE),
              options.get(METHOD_NAMESPACE),
              additionalConfigsMap.build());

      serviceConfig = tool.createServiceConfig();
    } catch (SwaggerConversionException e) {
      System.out.printf("\nSwagger Spec conversion failed:\n  %s\n", e.getMessage());
      return null;
    }

    if (!tool.getDiags().isEmpty()) {
      printDiagnostics(tool.getDiags());
    }

    return serviceConfig;
  }
}
