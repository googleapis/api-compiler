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

package com.google.api.tools.framework.aspects.context;

import com.google.api.ContextRule;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Key;
import java.util.List;

/** Configuration aspect for context section. */
public class ContextConfigAspect extends RuleBasedConfigAspect<ContextRule, ContextRule> {

  private static final ImmutableSet<String> REQUEST_CONTEXTS =
      // TODO(user): consider instead of hard-coding this, retrieving it from
      // the file descriptor or other metadata.
      ImmutableSet.of(
          "google.rpc.context.AbuseContext",
          "google.rpc.context.ConditionRequestContext",
          "google.rpc.context.FieldMaskContext",
          "google.rpc.context.OriginContext",
          "google.rpc.context.ProjectContext",
          "google.rpc.context.QosContext",
          "google.rpc.context.HttpHeaderContext",
          "google.rpc.context.SystemParameterContext",
          "google.rpc.context.VisibilityContext");

  private static final ImmutableSet<String> RESPONSE_CONTEXTS =
      ImmutableSet.of(
          "google.rpc.context.ConditionResponseContext", "google.rpc.context.HttpHeaderContext");

  /** A private key to access context rule attributes. */
  private static final Key<ContextRule> KEY = Key.get(ContextRule.class);
  /** Creates new context config aspect. */
  public static ContextConfigAspect create(Model model) {
    return new ContextConfigAspect(model);
  }

  ContextConfigAspect(Model model) {
    super(
        model,
        KEY,
        "context",
        ContextRule.getDescriptor(),
        model.getServiceConfig().getContext().getRulesList());
  }

  /** Returns an empty list since this aspect does not depend on any other aspects. */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  protected boolean isApplicable(ProtoElement element) {
    return element instanceof Method;
  }

  @Override
  protected ContextRule evaluate(ProtoElement element, ContextRule rule, boolean isFromIdl) {
    for (String context : rule.getRequestedList()) {
      if (!REQUEST_CONTEXTS.contains(context)) {
        error(element.getLocation(), "Requested context header '%s' is unknown.", context);
      }
    }
    for (String context : rule.getProvidedList()) {
      if (!RESPONSE_CONTEXTS.contains(context)) {
        error(element.getLocation(), "Provided context header '%s' is unknown.", context);
      }
    }
    return rule;
  }

  @Override
  protected void clearRuleBuilder(Service.Builder builder) {
    builder.getContextBuilder().clearRules();
  }

  @Override
  protected void addToRuleBuilder(
      Service.Builder serviceBuilder, String selector, ContextRule attribute) {
    serviceBuilder
        .getContextBuilder()
        .addRules(attribute.toBuilder().setSelector(selector).build());
  }
}
