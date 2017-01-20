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
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.tools.SwaggerToolDriverBase;
import com.google.api.tools.framework.tools.ToolDriverBase;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This tool generate the normalized config for an API service (defined using either proto
 * descriptors or swagger spec) and outputs it in binary proto, text or json format.
 */
public class ConfigGeneratorDriver extends ToolDriverBase {

  ////// Output location flags ///////

  public static final Option<String> TXT_OUT =
      ToolOptions.createOption(
          String.class, "txt_out", "The text output filename of the generated config.", "");

  public static final Option<String> BIN_OUT =
      ToolOptions.createOption(
          String.class, "bin_out", "The protobuf output filename of the generated config.", "");

  public static final Option<String> JSON_OUT =
      ToolOptions.createOption(
          String.class, "json_out", "The json output filename of the generated config.", "");

  ////// Misc Flags ////////
  public static final Option<String> PROJECT_ID =
      ToolOptions.createOption(
          String.class, "project_id", "An API producer project ID to use for the config.", "");

  private final ToolOptions options;
  private final ConfigGenerator configGenerator;
  private Service generatedServiceConfig = null;

  public ConfigGeneratorDriver(ToolOptions options) {
    super(options);
    this.options = Preconditions.checkNotNull(options, "options cannot be null");
    if (hasProtoDescriptorInput()) {
      configGenerator = new ConfigGeneratorFromProtoDescriptor(options);
    } else {
      configGenerator = new ConfigGeneratorFromSwagger(options);
    }
  }

  @Override
  public void process() throws IOException {
    if (!validateInputs()) {
      getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
          "Invalid arguments passed to the tool. Please see usage of this tool"
          + " by passing --help option"));
    }

    generatedServiceConfig = configGenerator.generateServiceConfig();
    if (generatedServiceConfig == null) {
      getDiagCollector().addDiag(
          Diag.error(SimpleLocation.TOPLEVEL, "Service config cannot be generated"));
      return;
    }
    Service.Builder builder = generatedServiceConfig.toBuilder();
    updateProducerProjectId(builder);
    generatedServiceConfig = builder.build();
    generateOutputFiles(generatedServiceConfig);
  }

  @Nullable
  public Service getServiceConfig() {
    return generatedServiceConfig;
  }

  private void updateProducerProjectId(Service.Builder builder) {
    if (!Strings.isNullOrEmpty(options.get(PROJECT_ID))) {
      builder.setProducerProjectId(options.get(PROJECT_ID));
    }
  }

  private boolean validateInputs() {
    if ((hasProtoDescriptorInput() && hasSwaggerSpecInput())
        || (!hasProtoDescriptorInput() && !hasSwaggerSpecInput())) {
      System.out.println(
          String.format(
              "Has to specify either '%s' or '%s' option",
              ToolOptions.DESCRIPTOR_SET.name(), SwaggerToolDriverBase.OPEN_API.name()));
      return false;
    }

    if (hasProtoDescriptorInput() && hasInputSpecificToSwaggerSpec()) {
      String invalidFlags =
          Joiner.on(",")
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
              invalidFlags, ToolOptions.DESCRIPTOR_SET.name()));
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
        SwaggerToolDriverBase.SERVICE_NAME, SwaggerToolDriverBase.TYPE_NAMESPACE);
  }

  private void generateOutputFiles(Service serviceConfig)
      throws FileNotFoundException, IOException {
    // Create normalized service proto, in binary form.
    if (!Strings.isNullOrEmpty(options.get(BIN_OUT))) {
      File outFileBinaryServiceConfig = new File(options.get(BIN_OUT));
      OutputStream normalizedOut = new FileOutputStream(outFileBinaryServiceConfig);
      serviceConfig.writeTo(normalizedOut);
      normalizedOut.close();
    }

    // Create normalized service proto, in text form.
    if (!Strings.isNullOrEmpty(options.get(TXT_OUT))) {
      File outFileTxtServiceConfig = new File(options.get(TXT_OUT));
      try (PrintWriter textPrintWriter =
          new PrintWriter(outFileTxtServiceConfig, StandardCharsets.UTF_8.name())) {
        TextFormat.print(serviceConfig, textPrintWriter);
      }
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
      try (PrintWriter jsonPrintWriter =
          new PrintWriter(outFileJsonServiceConfig, StandardCharsets.UTF_8.name())) {
        jsonPrinter.appendTo(serviceConfig, jsonPrintWriter);
      }
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
    return hasNonDefaultStringOption(SwaggerToolDriverBase.OPEN_API);
  }

  private boolean hasNonDefaultStringOption(Option<String> option) {
    return !Strings.isNullOrEmpty(options.get(option));
  }

  /**
   * An empty method to ensure statics in this class are initialized even if a constructor has not
   * yet been called.
   */
  static void ensureStaticsInitialized() {
    ConfigGeneratorFromProtoDescriptor.ensureStaticsInitialized();
    SwaggerToolDriverBase.ensureStaticsInitialized();
  }

  @Override
  public List<Diag> getDiags() {
    return configGenerator.getDiags();
  }

  @Override
  public boolean hasErrors() {
    return configGenerator.hasErrors();
  }
}
