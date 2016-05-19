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

package com.google.api.tools.framework.aspects.usage;

import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.Usage;
import com.google.api.UsageRule;
import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;

import java.util.List;

/**
 * Configuration aspect for usage section.
 */
public class UsageConfigAspect extends RuleBasedConfigAspect<UsageRule, UsageRule> {
  public static final String BILLING_REQUIREMENT = "serviceusage.googleapis.com/billing-enabled";
  public static final String TOS_REQUIREMENT_PREFIX = "serviceusage.googleapis.com/tos/";
  public static final String UTOS_REQUIREMENT = TOS_REQUIREMENT_PREFIX + "universal";

  private static final String UNSUPPORTED_REQUIREMENT_RULE = "requirement";

  /**
   * Creates usage config aspect.
   */
  public static ConfigAspectBase create(Model model) {
    return new UsageConfigAspect(model);
  }

  /**
   * Private key to access normalized usage config as attached to model.
   */
  private static final Key<Usage> USAGE_KEY = Key.get(Usage.class);
  private static final Key<UsageRule> UNREGISTERED_CALL_KEY = Key.get(UsageRule.class);

  private UsageConfigAspect(Model model) {
    super(model, UNREGISTERED_CALL_KEY, "usage", UsageRule.getDescriptor(),
        model.getServiceConfig().getUsage().getRulesList());
    registerLintRuleName(UNSUPPORTED_REQUIREMENT_RULE);
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  public void startMerging() {
    getModel().putAttribute(USAGE_KEY, getModel().getServiceConfig().getUsage());
    for (int i = 0; i < getModel().getServiceConfig().getUsage().getRequirementsCount(); ++i) {
      validateRequirement(
          getModel().getLocationOfRepeatedFieldInConfig(
              getModel().getServiceConfig().getUsage(), "requirements", i),
          getModel().getServiceConfig().getUsage().getRequirements(i));
    }
    super.startMerging();
  }

  private void validateRequirement(Object elementOrLocation, String requirement) {
    if (requirement.equals(BILLING_REQUIREMENT)
        || requirement.startsWith(TOS_REQUIREMENT_PREFIX)) {
      return;
    }

   lintWarning(UNSUPPORTED_REQUIREMENT_RULE, elementOrLocation,
       "Unsupported usage requirement: %s", requirement);
  }

  @Override
  public void startNormalization(Service.Builder builder) {
    builder.setUsage(getModel().getAttribute(USAGE_KEY));
    super.startNormalization(builder);
  }

  @Override
  protected boolean isApplicable(ProtoElement element) {
    return element instanceof Method;
  }

  @Override
  protected void clearRuleBuilder(Builder builder) {
    builder.getUsageBuilder().clearRules();
  }

  @Override
  protected void addToRuleBuilder(Builder builder, String selector, UsageRule binding) {
    builder.getUsageBuilder().addRules(
        binding.toBuilder().setSelector(selector).build());
  }

  @Override
  protected UsageRule evaluate(ProtoElement element, UsageRule rule,
      boolean isFromIdl) {
    return rule;
  }
}
