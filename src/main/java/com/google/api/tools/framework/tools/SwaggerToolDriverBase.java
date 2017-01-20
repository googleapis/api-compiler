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

package com.google.api.tools.framework.tools;

import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.SwaggerConversionException;
import com.google.api.tools.framework.importers.swagger.SwaggerToService;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Abstract base class for drivers based on swagger doc.
 */
public abstract class SwaggerToolDriverBase extends GenericToolDriverBase {

  /**
   * An empty method to ensure statics in this class are initialized if it hasn't been accessed
   * otherwise.
   */
  public static void ensureStaticsInitialized() {}

  public static final Option<String> TYPE_NAMESPACE =
      ToolOptions.createOption(
          String.class,
          "type_namespace",
          "(For Swagger Input Only) A namespace to use in generated "
              + "service config for all types. If provided, all type names will be prefixed by this"
              + " value and a dot ('.') separator.",
          "");

  public static final Option<String> METHOD_NAMESPACE =
      ToolOptions.createOption(
          String.class,
          "method_namespace",
          "(For Swagger Input Only) A namespace to use in generated service config "
              + "for all methods. If provided, all method names will be prefixed by this value "
              + "and a dot ('.') separator.",
          "");

  public static final Option<String> SERVICE_NAME =
      ToolOptions.createOption(
          String.class,
          "service_name",
          "(For Swagger Input Only) Service name to be used in the converted service " + "config.",
          "");

  public static final Option<String> OPEN_API =
      ToolOptions.createOption(
          String.class,
          "openapi",
          "Path to the OpenAPI spec to be converted into service config",
          "");

  private Service serviceConfig = null;
  private SwaggerToService tool;

  protected Model model;

  protected SwaggerToolDriverBase(ToolOptions options) {
    super(options);
  }

  @Override
  public int run() {
    this.model = setupModel();
    return super.run();
  }

  private Model setupModel() {
    // Prevent INFO messages from polluting the log.
    Logger.getLogger("").setLevel(Level.WARNING);

    try {
      serviceConfig = generateServiceConfig();
    } catch (IOException e) {
      getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
          "Unexpected exception:%n%s", Throwables.getStackTraceAsString(e)));
    }

    model = Model.create(serviceConfig);
    onErrorsExit();

    // Register standard processors.
    StandardSetup.registerStandardProcessors(model);

    // Register standard config aspects.
    StandardSetup.registerStandardConfigAspects(model);

    return model;
  }

  @Nullable
  public Service generateServiceConfig() throws IOException {
    ImmutableList.Builder<FileWrapper> fileContentMap = new ImmutableList.Builder<>();
    fileContentMap.add(FileWrapper.from(options.get(OPEN_API)));

    ImmutableList.Builder<FileWrapper> additionalConfigsMap = ImmutableList.builder();

    List<String> configFiles = options.get(ToolOptions.CONFIG_FILES);
    for (String additionalConfig : configFiles) {
      additionalConfigsMap.add(FileWrapper.from(additionalConfig));
    }

    try {
      tool =
          new SwaggerToService(
              fileContentMap.build(),
              options.get(SERVICE_NAME),
              options.get(TYPE_NAMESPACE),
              additionalConfigsMap.build());

      serviceConfig = tool.createServiceConfig();
    } catch (SwaggerConversionException e) {
      System.out.printf("\nSwagger Spec conversion failed:\n  %s\n", e.getMessage());
      return null;
    }

    if (!tool.getDiagCollector().getDiags().isEmpty()) {
      printDiagnostics(tool.getDiagCollector().getDiags());
    }

    return serviceConfig;
  }

  @Nullable
  public Service getServiceConfig() {
    return serviceConfig;
  }

  public Model getModel() {
    return model;
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
      System.out.printf(
          "\nSwagger/service config conversion encountered ERRORS:\n  %s\n", errors);
    }
    if (!warnings.isEmpty()) {
      System.out.printf(
          "\nSwagger/service config conversion encountered warnings:\n  %s\n", warnings);
    }
  }
}
