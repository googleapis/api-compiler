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
import com.google.api.tools.framework.importers.swagger.SwaggerImportTool;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.api.tools.framework.tools.ToolOptions.Option;
import com.google.api.tools.framework.tools.ToolProtoUtil;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.TextFormat;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.TypeRegistry;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * This tool generate the normalized config for an API service (defined using either proto
 * descriptors or swagger spec) and outputs it in binary proto, text or json format.
 */
public class ConfigGeneratorDriver {

  ////// Output location flags ///////

  public static final Option<String> TXT_OUT = ToolOptions.createOption(
      String.class, "txt_out", "The text output filename of the generated config.", "");

  public static final Option<String> BIN_OUT = ToolOptions.createOption(
      String.class, "bin_out", "The protobuf output filename of the generated config.", "");

  public static final Option<String> JSON_OUT = ToolOptions.createOption(
      String.class, "json_out", "The json output filename of the generated config.", "");

  ////// Misc Flags ////////
  public static final Option<String> PROJECT_ID =
      ToolOptions.createOption(
          String.class, "project_id", "An API producer project ID to use for the config.", "");

  private final ToolOptions options;
  private final ConfigGenerator configGenerator;
  private Service generatedServiceConfig = null;
  public ConfigGeneratorDriver(ToolOptions options) {
    this.options = Preconditions.checkNotNull(options, "options cannot be null");
    if (hasProtoDescriptorInput()) {
      configGenerator = new ConfigGeneratorFromProtoDescriptor(options);
    } else {
      configGenerator = new SwaggerImportTool(options);
    }
  }

  public int run() throws IOException {
    if (!validateInputs()) {
      System.out.println("Invalid arguments passed to the tool. Please see usage of this tool"
          + " by passing --help option");
      return 1;
    }

      generatedServiceConfig = configGenerator.generateServiceConfig();
      if (generatedServiceConfig == null) {
        return 1;
      }

    generatedServiceConfig = updateProducerProjectId(generatedServiceConfig);
    generateOutputFiles(generatedServiceConfig);

    return 0;
  }

  @Nullable
  public Service getServiceConfig() {
    return generatedServiceConfig;
  }

  private Service updateProducerProjectId(Service service) {
    if (!Strings.isNullOrEmpty(options.get(PROJECT_ID))) {
      return service.toBuilder().setProducerProjectId(options.get(PROJECT_ID)).build();
    }
    return service;
  }

  private boolean validateInputs() {
    if ((hasProtoDescriptorInput() && hasSwaggerSpecInput())
        || (!hasProtoDescriptorInput() && !hasSwaggerSpecInput())) {
      System.out.println(
          String.format(
              "Has to specify either '%s' or '%s' option",
              ToolOptions.DESCRIPTOR_SET.name(),
              SwaggerImportTool.OPEN_API.name()));
      return false;
    }

    if (hasProtoDescriptorInput() && hasInputSpecificToSwaggerSpec()) {
      String invalidFlags = Joiner.on(",")
          .join(
              FluentIterable.from(getOptionsSpecificToSwaggerSpec())
                  .transform(
                      new Function<Option<String>, String>() {
                        @Override
                        public String apply(Option<String> option) {
                          return option.name();
                        }
                      }));
      System.out.println(
          String.format(
              "Options '%s' are only applicable when input has swagger spec (specified by "
                  + "swagger_spec option). They are not applicable when specifying proto descriptor"
                  + "flag '%s'",
              invalidFlags,
              ToolOptions.DESCRIPTOR_SET.name()));
      return false;
    }

    return true;
  }

  private boolean hasInputSpecificToSwaggerSpec() {
    for (Option<String> option : getOptionsSpecificToSwaggerSpec()) {
      if (hasNonDefaultStringOption(option)) {
        return true;
      }
    }
    return false;
  }

  private static ImmutableList<Option<String>> getOptionsSpecificToSwaggerSpec() {
    return ImmutableList.of(
        SwaggerImportTool.SERVICE_NAME,
        SwaggerImportTool.TYPE_NAMESPACE,
        SwaggerImportTool.METHOD_NAMESPACE);
  }

  private void generateOutputFiles(Service serviceConfig)
      throws FileNotFoundException, IOException {
    // Create normalized service proto, in binary form.
    if (!Strings.isNullOrEmpty(options.get(BIN_OUT))) {
      File outFileBinaryServiceConfig =
          new File(options.get(BIN_OUT));
      OutputStream normalizedOut = new FileOutputStream(outFileBinaryServiceConfig);
      serviceConfig.writeTo(normalizedOut);
      normalizedOut.close();
    }

    // Create normalized service proto, in text form.
    if (!Strings.isNullOrEmpty(options.get(TXT_OUT))) {
      File outFileTxtServiceConfig = new File(options.get(TXT_OUT));
      PrintWriter textPrintWriter = new PrintWriter(outFileTxtServiceConfig);
      TextFormat.print(serviceConfig, textPrintWriter);
      textPrintWriter.close();
    }

    // Create normalized service proto, in json form.
    if (!Strings.isNullOrEmpty(options.get(JSON_OUT))) {
      File outFileJsonServiceConfig = new File(options.get(JSON_OUT));
      TypeRegistry registry =
          addPlatformExtensions(TypeRegistry.newBuilder())
              .add(Service.getDescriptor())
              .add(com.google.protobuf.BoolValue.getDescriptor())
              .add(com.google.protobuf.BytesValue.getDescriptor())
              .add(com.google.protobuf.DoubleValue.getDescriptor())
              .add(com.google.protobuf.FloatValue.getDescriptor())
              .add(com.google.protobuf.Int32Value.getDescriptor())
              .add(com.google.protobuf.Int64Value.getDescriptor())
              .add(com.google.protobuf.StringValue.getDescriptor())
              .add(com.google.protobuf.UInt32Value.getDescriptor())
              .add(com.google.protobuf.UInt64Value.getDescriptor())
              .build();
      JsonFormat.Printer jsonPrinter = JsonFormat.printer().usingTypeRegistry(registry);
      PrintWriter jsonPrintWriter = new PrintWriter(outFileJsonServiceConfig);
      jsonPrinter.appendTo(serviceConfig, jsonPrintWriter);
      jsonPrintWriter.close();
    }
  }

  private static final Set<String> EXTENDED_ELEMENTS =
      ImmutableSet.of(
          "proto2.FileOptions",
          "proto2.ServiceOptions",
          "proto2.MethodOptions",
          "proto2.MessageOptions",
          "proto2.EnumOptions",
          "proto2.EnumValueOptions",
          "proto2.FieldOptions");

  private TypeRegistry.Builder addPlatformExtensions(TypeRegistry.Builder registryBuilder) {
    ExtensionRegistry extensions = ToolProtoUtil.getStandardPlatformExtensions();
    for (String extendedType : EXTENDED_ELEMENTS) {
      for (ExtensionRegistry.ExtensionInfo info :
          extensions.getAllImmutableExtensionsByExtendedType(extendedType)) {

        if (null != info.defaultInstance) {
          registryBuilder.add(info.defaultInstance.getDescriptorForType());
        }
      }
    }
    return registryBuilder;
  }

  private boolean hasProtoDescriptorInput() {
    return hasNonDefaultStringOption(ToolOptions.DESCRIPTOR_SET);
  }

  private boolean hasSwaggerSpecInput() {
    return hasNonDefaultStringOption(SwaggerImportTool.OPEN_API);
  }

  private boolean hasNonDefaultStringOption(Option<String> option) {
    return !Strings.isNullOrEmpty(options.get(option));
  }

  /**
   * An empty method to ensure statics in this class are initialized even if a constructor
   * has not yet been called.
   */
  static void ensureStaticsInitialized() {
    ConfigGeneratorFromProtoDescriptor.ensureStaticsInitialized();
    SwaggerImportTool.ensureStaticsInitialized();
  }

  public List<Diag> getDiags() {
    return configGenerator.getDiags();
  }

  public boolean hasErrors() {
    return configGenerator.hasErrors();
  }
}
