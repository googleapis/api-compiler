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

package com.google.api.tools.framework.model;

import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.common.base.Preconditions;

/**
 * Base class for validation after merge phase.
 *
 * <p>ConfigValidator is triggered after the Merger process is completed and all the information to
 * be validated is attached to the appropriate Elements in the object model.
 *
 * <p>To create new validators please consider following guidelines:
 *
 * <ul>
 *   <li>If the validator is associated with an aspect, please define them inside a validators
 *       package within the corresponding aspect package, for example http/validators.
 *   <li>Also consider naming the validator file with suffix 'Validator.java' with the format
 *       &lt;WhatThisClassValidates&gt;Validator.java, example HttpCodegenSyntaxValidator.java.
 * </ul>
 */
public abstract class ConfigValidator<E extends Element> {

  public static final String DIAG_PREFIX = "%s: ";
  private final Class<E> elemClass;
  private final DiagReporter diagReporter;
  private final String validatorName;

  /**
   * Constructs a new validator that is tied to a particular Element class.
   *
   * @param diagResolver Diagnostic resolver into which the new warnings/errors will be added.
   * @param validatorName The name to be used when printing the error message as a prefix to the
   *     generated error text.
   * @param elemClass Element class or its sub classes for which this validator needs to be invoked.
   */
  protected ConfigValidator(DiagReporter diagResolver, String validatorName, Class<E> elemClass) {
    this.elemClass = Preconditions.checkNotNull(elemClass, "elemClass");
    this.diagReporter = Preconditions.checkNotNull(diagResolver, "diagResolver");
    this.validatorName = Preconditions.checkNotNull(validatorName, "validatorName");
  }

  /** Runs the validator. */
  public abstract void run(E element);

  /** Gets the element class this validator works on. */
  public Class<E> getElementClass() {
    return elemClass;
  }

  /** Helper to report an error. */
  public void error(LocationContext locationContext, String message, Object... params) {
    String prefix = String.format(DIAG_PREFIX, validatorName);
    diagReporter.reportError(locationContext, prefix + message, params);
  }

  /** Helper to report a warning. */
  public void warning(LocationContext locationContext, String message, Object... params) {
    String prefix = String.format(DIAG_PREFIX, validatorName);
    diagReporter.reportWarning(locationContext, prefix + message, params);
  }
  
}
