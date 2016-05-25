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

package com.google.api.tools.framework.tools;

import com.google.api.AnnotationsProto;
import com.google.api.AuthProto;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.ExtensionPool;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Strings;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.ExtensionRegistry;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

/**
 * Abstract base class for drivers running tools based on the framework.
 *
 * <p>Uses {@link ToolOptions} to pass arguments to the driver.
 */
public abstract class ToolDriverBase extends GenericToolDriverBase {

  protected Model model;

  protected ToolDriverBase(ToolOptions options) {
    super(options);
  }

  /**
   * Returns the model.
   */
  @Nullable
  public Model getModel() {
    return model;
  }

  /**
   * Check if there are any errors.
   */
  public boolean hasErrors() {
    return getDiagCollector().hasErrors();
  }

  /**
   * Returns diagnosis, including errors and warnings.
   */
  public List<Diag> getDiags() {
    return getDiagCollector().getDiags();
  }

  /**
   * Get the extension registry to use for processing service config. By default, registers
   * extensions from {@code google/api/annotations.proto}. Can be overridden by sub-classes.
   */
  protected ExtensionRegistry getPlatformExtensions() {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    AnnotationsProto.registerAllExtensions(registry);
    AuthProto.registerAllExtensions(registry);
    return registry;
  }

  /**
   * Registers processors for this tool.
   */
  protected abstract void registerProcessors();

  /**
   * Registers aspects for this tool.
   */
  protected abstract void registerAspects();

  /**
   * Runs the tool. Returns a non-zero exit code on errors.
   */
  @Override
  public int run() {
    setupModel();
    return super.run();
  }

  /**
   * Initializes the model.
   */
  private void setupModel() {

    // Prevent INFO messages from polluting the log.
    Logger.getLogger("").setLevel(Level.WARNING);

    List<String> protoFiles =
        ToolUtil.sanitizeSourceFiles(options.get(ToolOptions.PROTO_SOURCES));

    ExtensionRegistry platformExtensions = getPlatformExtensions();
    FileDescriptorSet descriptor = parseFileDescriptor(platformExtensions);
    ExtensionPool userExtensionPool = parseOptionalExtensionDescriptor(platformExtensions);

    if (getDiagCollector().hasErrors()) {
      return;
    }

    // TODO(user) Once the Model object can accept a DiagCollector on creation, pass in the
    // getDiagCollector() created in this class' base and get ride of that setDiagCollector()
    // method & call.
    model = Model.create(descriptor, protoFiles, options.get(ToolOptions.EXPERIMENTS),
        userExtensionPool);
    setDiagCollector(model.getDiagCollector());

    if (getDiagCollector().hasErrors()) {
      return;
    }

    registerProcessors();

    model.setDataPath(getDataPath());

    // Set service config.
    List<String> configFiles = options.get(ToolOptions.CONFIG_FILES);
    ToolUtil.setupModelConfigs(model, configFiles);
    if (getDiagCollector().hasErrors()) {
      return;
    }

    registerAspects();
  }

  private FileDescriptorSet parseFileDescriptor(ExtensionRegistry platformExtensions) {
    String descriptorName = options.get(ToolOptions.DESCRIPTOR_SET);
    try {
      return FileDescriptorSet.parseFrom(new FileInputStream(descriptorName),
          platformExtensions);
    } catch (IOException e) {
      getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
          "Cannot read descriptor file '%s': %s", descriptorName, e.getMessage()));
      return null;
    }
  }

  private ExtensionPool parseOptionalExtensionDescriptor(ExtensionRegistry platformExtensions) {
    String extensionDescriptorName = options.get(ToolOptions.EXTENSION_DESCRIPTOR_SET);
    if (!Strings.isNullOrEmpty(extensionDescriptorName)) {
      try {
        FileDescriptorSet extensionDescriptor =
            FileDescriptorSet.parseFrom(new FileInputStream(extensionDescriptorName),
                platformExtensions);
        return ExtensionPool.create(extensionDescriptor);
      } catch (IOException e) {
          getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
              "Cannot read extension descriptor file '%s': %s", extensionDescriptorName,
              e.getMessage()));
        }
    }
    return ExtensionPool.EMPTY;
  }

  /**
   * Report any errors and exit if there were some.
   */
  @Override
  protected void onErrorsExit() {
    if (getDiagCollector().hasErrors()) {
      reportDiag();
      System.exit(1);
    }
  }

  /**
   * Report errors and warnings.
   */
  @Override
  protected void reportDiag() {
    ToolUtil.reportDiags(model.getDiagCollector(), true);
  }

}
