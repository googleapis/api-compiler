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

package com.google.api.tools.framework.aspects.authentication;

import com.google.api.AuthProvider;
import com.google.api.AuthRequirement;
import com.google.api.Authentication;
import com.google.api.AuthenticationRule;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.aspects.authentication.model.AuthAttribute;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.snippet.Doc;
import com.google.api.tools.framework.snippet.SnippetSet;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;

/**
 * Configuration aspect for authentication binding.
 */
public class AuthConfigAspect extends RuleBasedConfigAspect<AuthenticationRule, AuthAttribute> {

  /** Splitter for oauth scopes specified in auth rule. */
  private static final Splitter OAUTH_SCOPE_SPLITTER = Splitter.on(",").omitEmptyStrings();

  /**
   * Static instance of empty {@link ImmutableList}.
   */
  private static final ImmutableList<String> EMPTY_STRING_LIST = ImmutableList.of();

  /**
   * Resource root for snippet file.
   */
  private static final String SNIPPET_RESOURCE_ROOT =
      "com/google/api/tools/framework/aspects/authentication/snippet";

  /**
   * Snippet file name.
   */
  private static final String SNIPPET_RESOURCE = "auth_document.snip";

  /**
   * Snippet interface for auth configuration aspect documentation.
   */
  private static final SnippetInterface snippet = SnippetSet.createSnippetInterface(
      SnippetInterface.class, SNIPPET_RESOURCE_ROOT, SNIPPET_RESOURCE);

  /**
   * The service-level authentication configuration.
   */
  private final Authentication authConfig;

  public static AuthConfigAspect create(Model model) {
    // TODO(user): Add DataManager for use with API Management service.
    return new AuthConfigAspect(model);
  }

  /**
   * Creates authentication binding from the given model.
   */
  AuthConfigAspect(Model model) {
    super(model, AuthAttribute.KEY, "auth", AuthenticationRule.getDescriptor(),
        model.getServiceConfig().getAuthentication().getRulesList());
    this.authConfig = model.getServiceConfig().getAuthentication();
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
  public void startMerging() {
    for (AuthenticationRule rule :
        getModel().getServiceConfig().getAuthentication().getRulesList()) {
      validateRequirements(rule.getRequirementsList());
    }
  }

  @Override
  protected AuthAttribute evaluate(
      ProtoElement element, AuthenticationRule rule, boolean isFromIdl) {
    return new AuthAttribute(rule);

  }

  private void validateRequirements(List<AuthRequirement> requirementsList) {
    for (AuthRequirement requirement : requirementsList) {
      AuthProvider authProvider = getAuthProvider(requirement.getProviderId());
      if (authProvider == null) {
        error(
            getLocationInConfig(requirement, AuthRequirement.PROVIDER_ID_FIELD_NUMBER),
            "Cannot find auth provider with id '%s'",
            requirement.getProviderId());
      } else {
        if (!requirement.getAudiences().isEmpty() && !authProvider.getAudiences().isEmpty()) {
          error(
              getLocationInConfig(requirement, AuthRequirement.AUDIENCES_FIELD_NUMBER),
              "Setting 'audiences' field inside both 'requirement' and 'provider' is not allowed. "
                  + "Please set the 'audiences' field only inside the 'provider'.");
        }
      }
    }
  }

  private AuthProvider getAuthProvider(String providerId) {
    for (AuthProvider authProvider :
        getModel().getServiceConfig().getAuthentication().getProvidersList()) {
      if (!authProvider.getId().isEmpty() && authProvider.getId().equals(providerId)) {
        return authProvider;
      }
    }
    return null;
  }

  @Override
  protected void clearRuleBuilder(Service.Builder builder) {
    builder.getAuthenticationBuilder().clearRules();
  }

  @Override
  protected void addToRuleBuilder(Service.Builder serviceBuilder, String selector,
      AuthAttribute binding) {
    // Add AuthenticationRule.

    // Copy the audiences field value from AuthProvider into AuthRequirements.
    AuthenticationRule.Builder authRuleBuilder = binding.getAuthenticationRule().toBuilder();
    for (AuthRequirement.Builder requirement : authRuleBuilder.getRequirementsBuilderList()) {
      String authProviderAudience = getProviderAudience(requirement.getProviderId());
      if (!authProviderAudience.isEmpty()) {
        requirement.setAudiences(authProviderAudience);
      }
    }
    serviceBuilder
        .getAuthenticationBuilder()
        .addRules(authRuleBuilder.setSelector(selector).build());
  }

  private String getProviderAudience(String providerId) {
    AuthProvider authProvider = getAuthProvider(providerId);
    if (authProvider != null) {
      return authProvider.getAudiences();
    }
    return "";
  }

  @Override
  public String getDocumentationTitle(ProtoElement element) {
    ImmutableList<String> scopes = getOauthScopes(element);
    return scopes.isEmpty() ? null : "Authorization";
  }

  @Override
  public String getDocumentation(ProtoElement element) {
    ImmutableList<String> scopes = getOauthScopes(element);
    return scopes.isEmpty()
        ? null : snippet.authDocumentation(scopes, scopes.size() == 1).prettyPrint();
  }

  private ImmutableList<String> getOauthScopes(ProtoElement element) {
    AuthAttribute auth = element.getAttribute(AuthAttribute.KEY);
    return auth != null && auth.getAuthenticationRule().hasOauth()
        ? ImmutableList.copyOf(OAUTH_SCOPE_SPLITTER.split(
            auth.getAuthenticationRule().getOauth().getCanonicalScopes()))
            : EMPTY_STRING_LIST;
  }

  /**
   * Interface for Snippet to generate documentation for the auth configuration aspect.
   */
  private static interface SnippetInterface {
    /**
     * Generates documentation body for given proto element.
     */
    Doc authDocumentation(ImmutableList<String> oauthScopes, boolean singleScope);
  }
}
