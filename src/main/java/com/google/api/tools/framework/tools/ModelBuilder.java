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

import com.google.api.tools.framework.model.BoundedDiagCollector;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.ExperimentsImpl;
import com.google.api.tools.framework.model.ExtensionPool;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** Used for setting up a {@link Model} object. */
public class ModelBuilder {

  /**
   * Builds an {@link Model} object, using the settings from {@link ToolOptions} and functional
   * overrides from {@link ModelBuildOverrides}. If the build fails, will return a 'null' model and
   * a {@link DiagCollector} containing all warnings and errors up to the failure point.
   */
  public ModelBuildResult setup(
      ToolOptions options, ModelBuildOverrides registry, String builtDataPath) {

    DiagCollector diagCollector = new BoundedDiagCollector();
    Set<FileWrapper> protoFiles = parseConfigFiles(options, builtDataPath, diagCollector);
    List<String> protoFileNames = Lists.newArrayList();
    for (FileWrapper protoFile : protoFiles) {
      protoFileNames.add(protoFile.getFilename());
    }
    FileDescriptorSet descriptor = parseFileDescriptors(options, registry, diagCollector);
    ExtensionPool userExtensionPool = parseExtensionDescriptor(options, registry, diagCollector);

    if (diagCollector.hasErrors()) {
      return ModelBuildResult.create(null, diagCollector);
    }

    Model model =
        Model.create(
            descriptor,
            protoFileNames,
            new ExperimentsImpl(options.get(ToolOptions.EXPERIMENTS)),
            userExtensionPool,
            diagCollector);
    if (diagCollector.hasErrors()) {
      return ModelBuildResult.create(null, diagCollector);
    }

    registry.registerProcessors(model);

    model.setDataPath(builtDataPath);

    ToolUtil.setupModelConfigs(model, protoFiles);
    if (diagCollector.hasErrors()) {
      return ModelBuildResult.create(null, diagCollector);
    }

    registry.registerAspects(model);
    return ModelBuildResult.create(model, diagCollector);
  }

  private FileDescriptorSet parseFileDescriptors(
      ToolOptions options, ModelBuildOverrides registry, DiagCollector diagCollector) {
    String fileDescriptor = options.get(ToolOptions.DESCRIPTOR_SET);
    if (!Strings.isNullOrEmpty(fileDescriptor)) {
      try {
        return parseFileAsDescriptorSet(FileWrapper.from(fileDescriptor), registry, diagCollector);
      } catch (IOException ex) {
        diagCollector.addDiag(
            Diag.error(
                SimpleLocation.TOPLEVEL,
                "Cannot read FileDescriptorSet file '%s': %s",
                fileDescriptor,
                ex.getMessage()));
        return null;
      }
    } else {
      return parseFileAsDescriptorSet(
          options.get(ToolOptions.DESCRIPTOR_SET_CONTENTS), registry, diagCollector);
    }
  }

  private ExtensionPool parseExtensionDescriptor(
      ToolOptions options, ModelBuildOverrides registry, DiagCollector diagCollector) {
    String extensionDescriptorName = options.get(ToolOptions.EXTENSION_DESCRIPTOR_SET);
    if (!Strings.isNullOrEmpty(extensionDescriptorName)) {
      try {
        FileDescriptorSet extensionDescriptor =
            parseFileAsDescriptorSet(
                FileWrapper.from(extensionDescriptorName), registry, diagCollector);
        return ExtensionPool.create(extensionDescriptor);
      } catch (IOException ex) {
        diagCollector.addDiag(
            Diag.error(
                SimpleLocation.TOPLEVEL,
                "Cannot read ExtensionFileDescriptorSet file '%s': %s",
                extensionDescriptorName,
                ex.getMessage()));
        return null;
      }
    } else if (options.get(ToolOptions.EXTENSION_DESCRIPTOR_SET_CONTENTS) != null) {
      FileDescriptorSet extensionDescriptor =
          parseFileAsDescriptorSet(
              options.get(ToolOptions.EXTENSION_DESCRIPTOR_SET_CONTENTS), registry, diagCollector);
      return ExtensionPool.create(extensionDescriptor);
    }
    return ExtensionPool.EMPTY;
  }

  private FileDescriptorSet parseFileAsDescriptorSet(
      FileWrapper inputFile, ModelBuildOverrides registry, DiagCollector diagCollector) {
    ByteString extensionFile = inputFile.getFileContents();
    try {
      return FileDescriptorSet.parseFrom(extensionFile, registry.getPlatformExtensions());
    } catch (InvalidProtocolBufferException e) {

      diagCollector.addDiag(
          Diag.error(
              SimpleLocation.TOPLEVEL,
              "Cannot read file descriptor file '%s': %s",
              inputFile.getFilename(),
              e.getMessage()));
      return null;
    }
  }

  private Set<FileWrapper> parseConfigFiles(
      ToolOptions options, String builtDataPath, DiagCollector diagCollector) {
    List<FileWrapper> unsanitizedFiles;
    if (!options.get(ToolOptions.CONFIG_FILES).isEmpty()) {
      unsanitizedFiles =
          ToolUtil.readModelConfigs(
              builtDataPath, options.get(ToolOptions.CONFIG_FILES), diagCollector);
    } else {
      unsanitizedFiles = options.get(ToolOptions.CONFIG_FILE_CONTENTS);
    }
    return ToolUtil.sanitizeSourceFiles(unsanitizedFiles);
  }

  /**
   * Return value for Model builder, includes a {@link Model} and an {@link DiagCollector} with all
   * build diagnostics.
   */
  @AutoValue
  public abstract static class ModelBuildResult {

    public static ModelBuildResult create(Model model, DiagCollector diagCollector) {
      return new AutoValue_ModelBuilder_ModelBuildResult(model, diagCollector);
    }

    @Nullable
    public abstract Model getModel();

    public abstract DiagCollector getDiagCollector();
  }
}
