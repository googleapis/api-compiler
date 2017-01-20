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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract base class for drivers running tools based on the framework.
 *
 * <p>Uses {@link ToolOptions} to pass arguments to the driver.
 */
public abstract class GenericToolDriverBase {

  protected final ToolOptions options;
  protected DiagCollector diags;

  protected GenericToolDriverBase(ToolOptions options) {
    this(options, new SimpleDiagCollector());
  }

  protected GenericToolDriverBase(ToolOptions options, DiagCollector diags) {
    this.options = Preconditions.checkNotNull(options);
    this.diags = Preconditions.checkNotNull(diags);
    // Link the option with its belonged options, so that we can keep use Option#get() to fetch
    // the value.
    for (ToolOptions.Option<?> option : ToolOptions.allOptions()) {
      option.setToolOptions(options);
    }
  }

  /**
   * Returns options.
   */
  public ToolOptions getOptions() {
    return options;
  }

  /**
   * Sets the DiagCollector for this tool to use.
   *
   * TODO(user): The _ONLY_ reason this is here is for cases where the DiagCollector is
   * instantiated after the tool is instantiated (e.g. using a Model's DiagCollector) that is
   * refactored, this setDiagCollector() should be removed...so prefer passing it into the ctor
   * when needed.
   */
  protected void setDiagCollector(DiagCollector diags) {
    this.diags = diags;
  }

  public DiagCollector getDiagCollector() {
    return diags;
  }

  /**
   * Method implementing the actual tool processing.
   */
  protected abstract void process() throws Exception;

  /**
   * Runs the tool. Returns a non-zero exit code on errors.
   */
  public int run() {
    // Run tool specific code.
    if (!getDiagCollector().hasErrors()) {
      try {
        process();
      } catch (Exception e) {
        getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
            "Unexpected exception:%n%s", Throwables.getStackTraceAsString(e)));
      }
    }
    reportDiag();
    return getDiagCollector().hasErrors() ? 1 : 0;
  }

  /** Check if there are any errors. */
  public boolean hasErrors() {
    return getDiagCollector().hasErrors();
  }

  /** Returns diagnosis, including errors and warnings. */
  public List<Diag> getDiags() {
    return getDiagCollector().getDiags();
  }

  /**
   * Returns the data path, including as specified by options and platform dependent defaults.
   */
  protected String getDataPath() {
    List<String> defaults = new ArrayList<>();
    String joined = Joiner.on(File.pathSeparator).join(defaults);
    String option = options.get(ToolOptions.DATA_PATH);
    return concatenateNonEmptyStrings(File.pathSeparator, option, joined);
  }

  protected String concatenateNonEmptyStrings(String separator, String... parts){
    List<String> nonEmptyParts = new ArrayList<>();
    for (String part : parts){
      if (!Strings.isNullOrEmpty(part)){
        nonEmptyParts.add(part);
      }
    }
    return Joiner.on(separator).join(nonEmptyParts);
  }

  /**
   * Report any errors and exit if there were some.
   */
  protected void onErrorsExit() {
    if (getDiagCollector().hasErrors()) {
      reportDiag();
      System.exit(1);
    }
  }

  /**
   * Report errors and warnings.
   */
  protected void reportDiag() {
    ToolUtil.reportDiags(getDiagCollector(), true);
  }
}
