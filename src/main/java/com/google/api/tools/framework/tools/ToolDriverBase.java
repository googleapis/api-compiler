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

import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.tools.ModelBuilder.ModelBuildResult;
import com.google.protobuf.ExtensionRegistry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Abstract base class for drivers running tools based on the framework.
 *
 * <p>Uses {@link ToolOptions} to pass arguments to the driver.
 */
public abstract class ToolDriverBase extends GenericToolDriverBase implements ModelBuildOverrides {

  protected Model model;

  protected ToolDriverBase(ToolOptions options) {
    super(options);
  }

  /** Returns the model. */
  @Nullable
  public Model getModel() {
    return model;
  }

  @Override
  public void registerProcessors(Model model) {
    StandardSetup.registerStandardProcessors(model);
  }

  @Override
  public void registerAspects(Model model) {
    StandardSetup.registerStandardConfigAspects(model);
  }
  /**
   * Get the extension registry to use for processing service config. By default, registers
   * extensions from {@code google/api/annotations.proto}.
   */
  @Override
  public ExtensionRegistry getPlatformExtensions() {
    return ToolProtoUtil.getStandardPlatformExtensions();
  }

  @Override
  public boolean includeDiscovery() {
    return true;
  }

  /** Runs the tool. Returns a non-zero exit code on errors. */
  @Override
  public int run() {
    this.model = setupModel();
    return super.run();
  }

  /** Initializes the model. */
  private Model setupModel() {
    // Prevent INFO messages from polluting the log.
    Logger.getLogger("").setLevel(Level.WARNING);

    ModelBuildResult buildResult = new ModelBuilder().setup(options, this, getDataPath());
    this.diags = buildResult.getDiagCollector();
    return buildResult.getModel();
  }
}
