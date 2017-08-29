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
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.List;
import java.util.regex.Pattern;

/** Validation checks for {@link QuotaLimit} names */
public class QuotaLimitNameValidator extends ConfigValidator<Model> {

  private static final int MAX_LIMIT_NAME_LENGTH = 64;
  // Checks that limit name contains only alphanumeric characters and "-".
  private static final Pattern LIMIT_NAME_PATTERN = Pattern.compile("^[-a-zA-Z0-9]+$");

  public QuotaLimitNameValidator(DiagReporter diagReporter) {
    super(diagReporter, SuperQuotaConfigAspect.NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    Quota quota = model.getServiceConfig().getQuota();
    checkQuotaLimitNamesAreValid(quota);
    checkQuotaLimitNamesAreUnique(quota);
  }

  private void checkQuotaLimitNamesAreValid(Quota quota) {
    for (QuotaLimit limit : quota.getLimitsList()) {
      String limitName = limit.getName();
      if (!LIMIT_NAME_PATTERN.matcher(limitName).matches()) {
        error(
            MessageLocationContext.create(limit, "name"),
            "Invalid quota limit name '%s'. Quota limit name can contain only alphanumeric "
                + "characters plus '-'.",
            limitName);
      }
      if (limitName.length() > MAX_LIMIT_NAME_LENGTH) {
        error(
            MessageLocationContext.create(limit, "name"),
            "Invalid quota limit name '%s'. Quota limit name must not be over %d bytes long.",
            limitName,
            MAX_LIMIT_NAME_LENGTH);
      }
    }
  }

  private void checkQuotaLimitNamesAreUnique(Quota quota) {
    ListMultimap<String, QuotaLimit> limitNameCounts = LinkedListMultimap.create();
    for (QuotaLimit limit : quota.getLimitsList()) {
      limitNameCounts.put(limit.getName(), limit);
    }
    for (String limitName : limitNameCounts.keySet()) {
      List<QuotaLimit> limits = limitNameCounts.get(limitName);
      if (limits.size() > 1) {
        error(
            MessageLocationContext.create(Iterables.getLast(limits), "name"),
            "There are %d quota limits with name '%s'.",
            limits.size(),
            limitName);
      }
    }
  }
}
