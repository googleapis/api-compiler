/*
 * Copyright 2017 Google Inc.
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
import com.google.api.Quota;
import com.google.api.QuotaLimit;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.superquota.SuperQuotaConfigAspect;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;
import java.util.HashSet;
import java.util.Set;

/**
 * Validation check for 'All {@link QuotaLimit} names reference an existing {@link MetricDescriptor}
 * name'
 */
public class QuotaMetricsExistValidator extends ConfigValidator<Model> {

  public QuotaMetricsExistValidator(DiagReporter diagReporter) {
    super(diagReporter, SuperQuotaConfigAspect.NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    checkMetricReferencesExist(model.getServiceConfig());
  }

  private void checkMetricReferencesExist(Service service) {
    Set<String> metricNames = new HashSet<>();
    for (MetricDescriptor metric : service.getMetricsList()) {
      metricNames.add(metric.getName());
    }
    Quota quotaConfig = service.getQuota();
    for (QuotaLimit limit : quotaConfig.getLimitsList()) {
      if (!metricNames.contains(limit.getMetric())) {
        error(
            MessageLocationContext.create(limit, "metric"),
            "Metric '%s' referenced by quota limit '%s' does not exist.",
            limit.getMetric(),
            limit.getName());
      }
    }
  }
}
