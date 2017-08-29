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

package com.google.api.tools.framework.aspects;

import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.api.tools.framework.model.DiagReporter.ResolvedLocation;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Preconditions;

/**
 * Base class of lint rules, which log style violations.
 *
 * <p>Lint rules are managed via {@link ConfigAspectBase} where they are registered during
 * construction time and automatically executed during the linting phase.
 */
public abstract class LintRule<E extends Element> {

  /** A prefix to be used in diag messages representing linter warnings. */
  public static final String DIAG_PREFIX = "(lint) %s-%s: ";

  private final ConfigAspectBase aspect;
  private final String ruleName;
  private final Class<E> elemClass;

  /**
   * Constructs a lint rule applicable to the specified model element class. All lint warnings
   * emitted by the rule use the passed rule name.
   */
  protected LintRule(ConfigAspectBase aspect, String ruleName, Class<E> elemClass) {
    this.aspect = Preconditions.checkNotNull(aspect);
    this.ruleName = Preconditions.checkNotNull(ruleName);
    this.elemClass = Preconditions.checkNotNull(elemClass);
  }

  /** Gets the element class this rule works on. */
  public Class<E> getElementClass() {
    return elemClass;
  }

  /** Gets the name of the rule used in diagnosis. */
  public String getName() {
    return ruleName;
  }

  /**
   * Runs the rule. All issues should be reported using the {@link #warning(Location, String,
   * Object...)} methods.
   */
  public abstract void run(E element);

  /** Logs a lint warning. */
  protected void warning(Location location, String message, Object... params) {
    warning(ResolvedLocation.create(location), message, params);
  }

  /** Logs a lint warning. */
  protected void warning(LocationContext locationContext, String message, Object... params) {
    String prefix = String.format(DIAG_PREFIX, aspect.getAspectName(), ruleName);
    aspect.getDiagReporter().reportWarning(locationContext, prefix + message, params);
  }

  public static String formatLintWarning(
      String message, String ruleName, String aspectName, Object... params) {
    String prefix = String.format(DIAG_PREFIX, aspectName, ruleName);
    return String.format(prefix + message, params);
  }
}
