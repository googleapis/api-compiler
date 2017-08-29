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

import com.google.api.QuotaLimit;
import com.google.api.tools.framework.aspects.superquota.SuperQuotaConfigAspect;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * Validates that for all {@link QuotaLimit}s, the {@link QuotaLimit#getUnit()} contains an allowed
 * unit string.
 */
public class QuotaUnitValidator extends ConfigValidator<Model> {

  private static final ImmutableList<String> ALLOWED_UNITS = ImmutableList.of("1/min/{project}");

  public QuotaUnitValidator(DiagReporter diagReporter) {
    super(diagReporter, SuperQuotaConfigAspect.NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    for (QuotaLimit limit : model.getServiceConfig().getQuota().getLimitsList()) {
      if (!ALLOWED_UNITS.contains(limit.getUnit())) {
        error(
            MessageLocationContext.create(limit, QuotaLimit.NAME_FIELD_NUMBER),
            "Config specified QuotaLimit.Unit of '%s' in QuotaLimit '%s', "
                + "but QuotaLimit currently only supports the unit(s) '%s'",
            limit.getUnit(),
            limit.getName(),
            Joiner.on(",").join(ALLOWED_UNITS));
      }
    }
  }
}
