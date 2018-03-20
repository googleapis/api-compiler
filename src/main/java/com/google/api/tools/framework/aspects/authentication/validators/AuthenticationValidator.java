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

package com.google.api.tools.framework.aspects.authentication.validators;

import com.google.api.AuthProvider;
import com.google.api.AuthRequirement;
import com.google.api.Authentication;
import com.google.api.AuthenticationRule;
import com.google.api.tools.framework.aspects.authentication.AuthConfigAspect;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Model;

/** Validation checks for Authentication configuration */
public class AuthenticationValidator extends ConfigValidator<Model> {

  public AuthenticationValidator(DiagReporter diagReporter) {
    super(diagReporter, AuthConfigAspect.ASPECT_NAME, Model.class);
  }

  @Override
  public void run(Model model) {
    Authentication authConfig = model.getServiceConfig().getAuthentication();
    for (AuthenticationRule authRule : authConfig.getRulesList()) {
      validateRequirements(authRule, model);
    }
  }

  private void validateRequirements(AuthenticationRule authRule, Model model) {
    for (AuthRequirement requirement : authRule.getRequirementsList()) {
      AuthProvider authProvider =
          AuthConfigAspect.getAuthProvider(requirement.getProviderId(), model);
      if (authProvider == null) {
        error(
            MessageLocationContext.create(requirement, AuthRequirement.PROVIDER_ID_FIELD_NUMBER),
            "Cannot find auth provider with id '%s'",
            requirement.getProviderId());
      } else {
        if (!requirement.getAudiences().isEmpty() && !authProvider.getAudiences().isEmpty()) {
          if (!requirement.getAudiences().equalsIgnoreCase(authProvider.getAudiences())) {
            error(
                MessageLocationContext.create(requirement, AuthRequirement.AUDIENCES_FIELD_NUMBER),
                "Setting 'audiences' field inside both 'requirement' and provider '%s' is not"
                    + " allowed. Please set the 'audiences' field only inside the 'provider'.",
                authProvider.getId());
          }
        }
      }
    }
  }
}
