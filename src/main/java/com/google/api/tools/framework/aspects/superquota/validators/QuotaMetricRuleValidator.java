/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.api.tools.framework.aspects.superquota.validators;

import com.google.api.MetricDescriptor;
import com.google.api.MetricRule;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.superquota.SuperQuotaConfigAspect;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

/** Quota validator for {@link MetricRule} */
public class QuotaMetricRuleValidator extends ConfigValidator<Model> {

  public QuotaMetricRuleValidator(DiagReporter diagReporter) {
    super(diagReporter, SuperQuotaConfigAspect.NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    checkMetricRulesAreValid(model.getServiceConfig());
  }

  /** Checks that the metric names and costs in metric rules are valid. */
  private void checkMetricRulesAreValid(Service service) {
    Set<String> metricNames = new HashSet<>();
    for (MetricDescriptor metric : service.getMetricsList()) {
      metricNames.add(metric.getName());
    }

    for (MetricRule rule : service.getQuota().getMetricRulesList()) {
      for (Entry<String, Long> entry : rule.getMetricCosts().entrySet()) {
        if (!metricNames.contains(entry.getKey())) {
          error(
              MessageLocationContext.createForRepeated(rule, "metric_costs", entry.getKey()),
              "Metric '%s' referenced by metric rule '%s' does not exist.",
              entry.getKey(),
              rule.getSelector());
        }
        if (entry.getValue() < 0) {
          error(
              MessageLocationContext.createForRepeated(rule, "metric_costs", entry.getKey()),
              "Metric cost %d for metric '%s' must not be negative.",
              entry.getValue(),
              entry.getKey());
        }
      }
    }
  }
}
