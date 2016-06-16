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

package com.google.api.tools.framework.aspects.systemparameter;

import com.google.api.Service;
import com.google.api.SystemParameterRule;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;

import java.util.List;

/**
 * Configuration aspect for system parameter section.
 */
public class SystemParameterConfigAspect
    extends RuleBasedConfigAspect<SystemParameterRule, SystemParameterRule> {

  /**
   * A private key to access system parameter rule attributes.
   */
  private static final Key<SystemParameterRule> KEY = Key.get(SystemParameterRule.class);

  /**
   * Creates new system parameter config aspect.
   */
  public static SystemParameterConfigAspect create(Model model) {
    return new SystemParameterConfigAspect(model);
  }

  SystemParameterConfigAspect(Model model) {
    super(model, KEY, "systemParameter", SystemParameterRule.getDescriptor(),
        model.getServiceConfig().getSystemParameters().getRulesList());
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  protected boolean isApplicable(ProtoElement element) {
    return element instanceof Method;
  }

  @Override
  protected SystemParameterRule evaluate(
      ProtoElement element, SystemParameterRule rule, boolean isFromIdl) {
    return rule;
  }

  @Override
  protected void clearRuleBuilder(Service.Builder builder) {
    builder.getSystemParametersBuilder().clearRules();
  }

  @Override
  protected void addToRuleBuilder(Service.Builder serviceBuilder, String selector,
      SystemParameterRule attribute) {
    serviceBuilder.getSystemParametersBuilder()
      .addRules(attribute.toBuilder().setSelector(selector).build());
  }
}
