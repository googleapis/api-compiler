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

package com.google.api.tools.framework.aspects.superquota;

import com.google.api.MetricRule;
import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.aspects.superquota.model.SuperQuotaAttribute;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.annotation.Nullable;

/** Configuration aspect for SuperQuota. */
public class SuperQuotaConfigAspect extends RuleBasedConfigAspect<MetricRule, SuperQuotaAttribute> {

  public static final String NAME = "quota";
  private final Service serviceConfig;

  public static SuperQuotaConfigAspect create(Model model) {
    return new SuperQuotaConfigAspect(model);
  }

  private SuperQuotaConfigAspect(Model model) {
    super(
        model,
        SuperQuotaAttribute.KEY,
        NAME,
        MetricRule.getDescriptor(),
        model.getServiceConfig().getQuota().getMetricRulesList());
    this.serviceConfig = this.getModel().getServiceConfig();
  }

  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  public void startMerging() {
    if (!serviceConfig.hasQuota()) {
      return;
    }

    super.startMerging();
  }

  @Override
  public void startNormalization(Builder builder) {
    super.startNormalization(builder); // startNormalization() clears MetricRules.
  }

  @Override
  protected boolean isApplicable(ProtoElement element) {
    return element instanceof Method;
  }

  @Override
  @Nullable
  protected SuperQuotaAttribute evaluate(ProtoElement element, MetricRule rule, boolean isFromIdl) {
    return new SuperQuotaAttribute(rule);
  }

  @Override
  protected void clearRuleBuilder(Builder builder) {
    builder.getQuotaBuilder().clearMetricRules();
  }

  @Override
  protected void addToRuleBuilder(Builder builder, String selector, SuperQuotaAttribute attribute) {
    builder
        .getQuotaBuilder()
        .addMetricRules(attribute.getRule().toBuilder().setSelector(selector).build());
  }
}
