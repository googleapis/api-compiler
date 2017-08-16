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

import com.google.api.Quota;
import com.google.api.QuotaLimit;
import com.google.api.tools.framework.aspects.superquota.SuperQuotaConfigAspect;
import com.google.api.tools.framework.aspects.superquota.SuperQuotaConstants;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.util.Map;
import java.util.Map.Entry;

/** Validation checks for {@link QuotaLimit} values */
public class QuotaLimitValuesValidator extends ConfigValidator<Model> {

  public QuotaLimitValuesValidator(DiagReporter diagReporter) {
    super(diagReporter, SuperQuotaConfigAspect.NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    Quota quota = model.getServiceConfig().getQuota();
    checkQuotaLimitValuesAreValid(quota);
  }

  private void checkQuotaLimitValuesAreValid(Quota quota) {
    for (QuotaLimit limit : quota.getLimitsList()) {
      Map<String, Long> values = limit.getValues();
      if (values.size() > 1) {
        error(
            MessageLocationContext.create(limit, QuotaLimit.VALUES_FIELD_NUMBER),
            "Limit '%s' has invalid tier values. Only 'STANDARD' is supported but found {%s}",
            limit.getName(),
            Joiner.on(',').join(values.keySet()));
      }
      Entry<String, Long> tierValue = Iterables.getOnlyElement(values.entrySet());
      if (!SuperQuotaConstants.STANDARD.equals(tierValue.getKey())) {
        error(
            MessageLocationContext.create(limit, QuotaLimit.VALUES_FIELD_NUMBER),
            "Limit '%s' has invalid tier value. Only '%s' is supported but found {%s}",
            limit.getName(),
            SuperQuotaConstants.STANDARD,
            tierValue.getKey());
      }
      if (tierValue.getValue() < 0 && tierValue.getValue() != SuperQuotaConstants.UNLIMITED_QUOTA) {
        error(
            MessageLocationContext.create(limit, QuotaLimit.VALUES_FIELD_NUMBER),
            "Limit '%s' specifies a negative limit value that is not %d.",
            limit.getName(),
            SuperQuotaConstants.UNLIMITED_QUOTA);
      }
    }
  }
}
