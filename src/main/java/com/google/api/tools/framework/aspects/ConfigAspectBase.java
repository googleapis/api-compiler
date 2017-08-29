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

import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.api.tools.framework.model.DiagReporter.ResolvedLocation;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

/**
 * Base class for implementing config aspects.
 *
 * <p>All aspect implementations must extend this class or it's descendants instead of directly
 * implementing the interface {@link ConfigAspect}. Errors and warnings must be reported using the
 * methods on this class instead of via the model.
 *
 * <p>Please observe that according to rules described in {@link ConfigAspect}, during the merging
 * state, only essential validation issues should be reported as errors, but not style violations.
 * It is recommended to use the {@link LintRule} mechanism for style violations.
 */
public abstract class ConfigAspectBase implements ConfigAspect {

  /** A prefix to be used in diag messages for general errors and warnings. */
  public static final String ASPECT_DIAG_PREFIX = "%s: ";

  // The model this aspect is attached to.
  private final Model model;

  // The display name of the aspect. Used e.g. in error messages.
  private final String aspectName;

  // The validation rules registered for this aspect.
  private final Multimap<Class<? extends Element>, LintRule<? extends Element>> lintRules =
      LinkedHashMultimap.create();

  // The set of lint rule names used by this aspect.
  private final Set<String> lintRuleNames = Sets.newLinkedHashSet();

  /**
   * Initializes this base class. Takes a string which is used to identify the aspect in diagnosis.
   */
  protected ConfigAspectBase(Model model, String aspectName) {
    this.model = Preconditions.checkNotNull(model);
    this.aspectName = Preconditions.checkNotNull(aspectName);
  }

  /** Returns the model to which the aspect is linked. */
  public Model getModel() {
    return model;
  }

  public DiagReporter getDiagReporter() {
    return model.getDiagReporter();
  }

  @Override
  public String getAspectName() {
    return aspectName;
  }

  @Override
  public Set<String> getLintRuleNames() {
    return lintRuleNames;
  }

  /**
   * Registers a linting rule with this aspect. The type of the rule can be either a proto element
   * or, for top-level scoped issues, the model itself. All registered rules are run automatically
   * by this class.
   */
  public <E extends Element> void registerLintRule(LintRule<E> rule) {
    lintRules.put(rule.getElementClass(), rule);
    registerLintRuleName(rule.getName());
  }

  /**
   * Registers name of a lint rule. Should be used for all rule names used in Lint rules that do not
   * extend {@link LintRule} to enable suppression. Does not need to be used for rules using the
   * {@link #registerLintRule(LintRule)} mechanism.
   */
  public void registerLintRuleName(String... names) {
    Preconditions.checkArgument(modelProcessingHasNotStarted(), "[Internal Error]Linters should be "
        + "registered during aspect construction and not during processing of the model.");
    lintRuleNames.addAll(Lists.newArrayList(names));
  }

  private boolean modelProcessingHasNotStarted() {
    // If resolved stage is established, it would mean the
    // model processing has started.
    return !this.model.hasAttribute(Resolved.KEY);
  }

  /**
   * Default implementation of starting merge; does nothing.
   */
  @Override
  public void startMerging() {}

  /** Default implementation for merging an element; does nothing. */
  @Override
  public void merge(ProtoElement elem) {}

  /** Default implementation ending merging; does nothing. */
  @Override
  public void endMerging() {}

  /** Default implementation of start linting; does nothing. */
  @Override
  public void startLinting() {}

  /**
   * Default implementation of linting an element. This runs all registered style rules for the
   * element's type.
   *
   * <p>If you override this method, be sure to call {@code super}.
   */
  @Override
  public void lint(ProtoElement elem) {
    runRules(elem);
  }

  /**
   * Default implementation ending linting. This runs all style rules registered for the model
   * (top-level scope).
   *
   * <p>If you override this method, be sure to call {@code super}.
   */
  @Override
  public void endLinting() {
    runRules(model);
  }

  /** Runs all rules for the given element. */
  @SuppressWarnings("unchecked")
  private void runRules(Element elem) {
    Class<?> type = elem.getClass();
    while (Element.class.isAssignableFrom(type)) {
      for (LintRule<? extends Element> rule : lintRules.get((Class<? extends Element>) type)) {
        @SuppressWarnings("unchecked")
        LintRule<Element> castedRule = (LintRule<Element>) rule;
        castedRule.run(elem);
      }
      type = type.getSuperclass();
    }
  }

  /** Default implementation of starting normalization; does nothing. */
  @Override
  public void startNormalization(Service.Builder builder) {}

  /** Default implementation to normalize an element; does nothing. */
  @Override
  public void normalize(ProtoElement element, Builder builder) {}

  /** Default implementation of ending normalization; does nothing. */
  @Override
  public void endNormalization(Service.Builder builder) {}

  /**
   * Default implementation of aspect documentation title. Returns null, indicating the aspect is
   * not documented.
   */
  @Override
  public String getDocumentationTitle(ProtoElement element) {
    return null;
  }

  /**
   * Default implementation of aspect documentation. Returns null, indicating the aspect is not
   * documented.
   */
  @Override
  public String getDocumentation(ProtoElement element) {
    return null;
  }

  /** Helper for subclasses to report an error. */
  public void error(Location location, String message, Object... params) {
    error(ResolvedLocation.create(location), message, params);
  }

  public void error(LocationContext locationContext, String message, Object... params) {
    String prefix = String.format(ASPECT_DIAG_PREFIX, aspectName);
    getDiagReporter().reportError(locationContext, prefix + message, params);
  }

  /** Helper for subclasses to report a warning. */
  public void warning(Location location, String message, Object... params) {
    warning(ResolvedLocation.create(location), message, params);
  }

  public void warning(LocationContext locationContext, String message, Object... params) {
    String prefix = String.format(ASPECT_DIAG_PREFIX, aspectName);
    getDiagReporter().reportWarning(locationContext, prefix + message, params);
  }

  /**
   * Return a view of this aspect as a diag collector. This allows for abstracting the aspect to
   * make code better testable.
   */
  public DiagCollector asDiagCollector() {
    return new DiagCollector() {

      @Override
      public void addDiag(Diag diag) {
        switch (diag.getKind()) {
          case ERROR:
            error(diag.getLocation(), diag.getMessage());
            break;
          case WARNING:
            warning(diag.getLocation(), diag.getMessage());
            break;
        }
      }

      @Override
      public int getErrorCount() {
        return model.getDiagReporter().getDiagCollector().getErrorCount();
      }

      @Override
      public boolean hasErrors() {
        return getErrorCount() > 0;
      }

      @Override
      public List<Diag> getDiags() {
        return model.getDiagReporter().getDiagCollector().getDiags();
      }
    };
  }

}
