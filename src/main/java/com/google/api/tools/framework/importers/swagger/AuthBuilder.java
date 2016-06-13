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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.AuthProvider;
import com.google.api.AuthRequirement;
import com.google.api.Authentication;
import com.google.api.AuthenticationRule;
import com.google.api.Usage;
import com.google.api.UsageRule;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import io.swagger.models.Operation;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.SecuritySchemeDefinition;

import java.lang.reflect.Type;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to create {@link AuthenticationRule} from swagger operations.
 */
final class AuthBuilder {
  private static final String JWKS_SWAGGER_EXTENSION = "x-jwks_uri";
  private static final String OAUTH_ISSUER_SWAGGER_EXTENSION = "x-issuer";
  private static final String SECURITY_REQUIREMENT_EXTENSION = "x-security";

  private final DiagCollector diagCollector;
  private final List<AuthenticationRule> authRules = Lists.newArrayList();
  private final List<UsageRule> usageRules = Lists.newArrayList();
  private final Set<String> apiKeyDefinitions = new LinkedHashSet<>();
  private boolean requiresApiKeyAtTopLevel = false;
  private final Map<String, AuthProvider> authProviders = Maps.newLinkedHashMap();
  private final String namespacePrefix;

  AuthBuilder(String namespace, DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
    this.namespacePrefix =
        (namespace.isEmpty() || namespace.endsWith(".")) ? namespace : namespace + ".";
  }

  /**
   * Returns the generated {@link AuthenticationRule}s.
   */
  Authentication getAuthentication() {
    Authentication.Builder authenticationBuilder = Authentication.newBuilder();
    authenticationBuilder.addAllProviders(authProviders.values());
    authenticationBuilder.addAllRules(authRules);
    return authenticationBuilder.build();
  }

  Usage getUsage() {
    Usage.Builder usageBuilder = Usage.newBuilder();
    usageBuilder.addAllRules(usageRules);
    return usageBuilder.build();
  }

  /**
   * Creates {@link AuthProvider} from Swagger SecuritySchemeDefinition.
   */
  void addAuthProvider(String securitySchemaName, SecuritySchemeDefinition securitySchema) {
    if (securitySchema == null) {
      return;
    }
    if (securitySchema.getType().equalsIgnoreCase("oauth2")) {
      AuthProvider.Builder authProviderBuilder = AuthProvider.newBuilder();
      authProviderBuilder.setId(securitySchemaName);
      if (VendorExtensionUtils.hasExtension(
              securitySchema.getVendorExtensions(), OAUTH_ISSUER_SWAGGER_EXTENSION, String.class,
              diagCollector)) {
        authProviderBuilder.setIssuer(
            (String) securitySchema.getVendorExtensions().get(OAUTH_ISSUER_SWAGGER_EXTENSION));
      }
      if (VendorExtensionUtils.hasExtension(
              securitySchema.getVendorExtensions(), JWKS_SWAGGER_EXTENSION, String.class,
              diagCollector)) {
        authProviderBuilder.setJwksUri(
            (String) securitySchema.getVendorExtensions().get(JWKS_SWAGGER_EXTENSION));
      }
      authProviders.put(securitySchemaName, authProviderBuilder.build());
    } else if (securitySchema.getType().equalsIgnoreCase("apiKey")) {
      ApiKeyAuthDefinition apiKeyDef = (ApiKeyAuthDefinition) securitySchema;
      if (isValidApiKeyDefinition(apiKeyDef)) {
        apiKeyDefinitions.add(securitySchemaName);
      }
    } else {
      diagCollector.addDiag(Diag.warning(
          SimpleLocation.UNKNOWN,
          "Security Schema '%s' is not supported. Only support schema are OAuth2",
          securitySchemaName));
    }
  }

  /**
   * Checks if the defined apiKey is valid or not. Only apiKey definition with name as 'key' and
   * 'in' as 'query' are allowed"
   */
  private boolean isValidApiKeyDefinition(ApiKeyAuthDefinition apiKeydef) {
    if (apiKeydef.getName().equalsIgnoreCase("key") || apiKeydef.getIn() == In.QUERY) {
      return true;
    } else {
      diagCollector.addDiag(Diag.warning(
          SimpleLocation.UNKNOWN,
          "apiKey '%s' is ignored. Only apiKey with 'name' as 'key' and 'in' as 'query' are "
          + "supported",
          apiKeydef.getName()));
      return false;
    }
  }

  /**
   * Creates {@link AuthRequirement} from Swagger requirements per operations.
   */
  public void addAuthRule(Operation operation, String operationType, String swaggerPath) {
    addOAuthRules(operation, operationType, swaggerPath);
    addApiKeyRules(operation, operationType, swaggerPath);
  }

  private void addApiKeyRules(Operation operation, String operationType, String swaggerPath) {
    addUsageRulePerMethod(operation.getSecurity(), operationType, swaggerPath,
        namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()));
  }

  /**
   * Creates {@link AuthRequirement} from Swagger requirements per operations.
   */
  private void addOAuthRules(Operation operation, String operationType, String swaggerPath) {
    AuthenticationRule.Builder builder = AuthenticationRule.newBuilder();
    if (convertSecurityRequirementExtension(operation.getVendorExtensions(), builder,
            SwaggerToService.createOperationLocation(operationType, swaggerPath))) {
      builder.setSelector(
          namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()));
      authRules.add(builder.build());
    }
  }

  /**
   * Adds auth security requirement for the entire service.
   */
  public void addSecurityRequirementExtensionForEntireService(Swagger swagger) {
    AuthenticationRule.Builder builder = AuthenticationRule.newBuilder();
    if (convertSecurityRequirementExtension(
            swagger.getVendorExtensions(), builder, new SimpleLocation("Swagger Spec"))) {
      builder.setSelector("*");
      authRules.add(builder.build());
    }
  }

  /**
   * Creates a AuthenticationRule from security requirement extension.
   */
  private boolean convertSecurityRequirementExtension(Map<String, Object> extensions,
      AuthenticationRule.Builder authenticationRuleBuilder, Location location) {
    if (VendorExtensionUtils.hasExtension(
        extensions, SECURITY_REQUIREMENT_EXTENSION, List.class, diagCollector)) {
      List<Map<String, SecurityReq>> securityRequirements =
          getSecurityRequirements(extensions.get(SECURITY_REQUIREMENT_EXTENSION));
      if (securityRequirements == null) {
        diagCollector.addDiag(Diag.error(location,
            "Extension %s does not have the valid value. Please check "
            + "the documentation for its schema",
            SECURITY_REQUIREMENT_EXTENSION));
        return false;
      }

      for (Map<String, SecurityReq> schemas : securityRequirements) {
        for (Map.Entry<String, SecurityReq> schema : schemas.entrySet()) {
          String authSchemaName = schema.getKey();
          if (!authProviders.containsKey(authSchemaName)) {
            diagCollector.addDiag(Diag.error(location,
                "Schema '%s' referenced in extension %s does not have the "
                + "valid value. Please check the documentation for its schema.",
                authSchemaName, SECURITY_REQUIREMENT_EXTENSION));
            return false;
          }
          List<String> audiences = schema.getValue().getAudiences();
          if (audiences == null) {
            diagCollector.addDiag(Diag.error(location,
                "Extension %s does not have the valid value. Please "
                + "check the documentation for its schema",
                SECURITY_REQUIREMENT_EXTENSION));
            return false;
          }
          AuthRequirement.Builder authRequirement = AuthRequirement.newBuilder();
          authRequirement.setProviderId(authSchemaName);
          authRequirement.setAudiences(Joiner.on(",").join(audiences));
          authenticationRuleBuilder.addRequirements(authRequirement);
        }
      }
      return true;
    }
    return false;
  }

  /**
   * Verified if the extension schema is correct.
   */
  private List<Map<String, SecurityReq>> getSecurityRequirements(Object securityReqExt) {
    Gson gson = new GsonBuilder().create();
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    Type typeSecurityReqExtension = new TypeToken<List<Map<String, SecurityReq>>>() {}.getType();
    try {
      String jsonString = ow.writeValueAsString(securityReqExt);
      return gson.fromJson(jsonString, typeSecurityReqExtension);
    } catch (JsonProcessingException | JsonParseException ex) {
      return null;
    }
  }

  /**
   * Class representing authentication schema in security requirement extension in swagger.
   */
  private static final class SecurityReq {
    // List of audiences
    private List<String> audiences;

    public void setAudiences(List<String> audience) {
      this.audiences = audience;
    }

    public List<String> getAudiences() {
      return audiences;
    }
  }

  private void addUsageRulePerMethod(
      Iterable<Map<String, List<String>>> securityRequirements, String operationType,
      String swaggerPath, String selector) {
    boolean perMethodApiKeyRequired =
        isApiKeyRequired(securityRequirements, requiresApiKeyAtTopLevel);
    if (!perMethodApiKeyRequired) {
      // TODO(): Remove this check once we have warnings suppression implemented.
      if (!swaggerPath.equals(SwaggerToService.WILDCARD_URL_PATH)) {
        diagCollector.addDiag(
            Diag.warning(
                SwaggerToService.createOperationLocation(operationType, swaggerPath),
                "Operation does not require an API key; callers may invoke the method "
                    + "without specifying an associated API-consuming project."));
      }
    }
    usageRules.add(
        UsageRule.newBuilder()
            .setSelector(selector)
            .setAllowUnregisteredCalls(!perMethodApiKeyRequired)
            .build());
  }

  public void addSecurityRequirementForEntireService(
      Iterable<SecurityRequirement> securityRequirements) {
    if (securityRequirements == null) {
      requiresApiKeyAtTopLevel = false;
    } else {
      requiresApiKeyAtTopLevel = isApiKeyRequired(
          Iterables.transform(securityRequirements, SecurityRequirementsExtractor.INSTANCE), false);
    }
    usageRules.add(
        UsageRule.newBuilder()
            .setSelector("*")
            .setAllowUnregisteredCalls(!requiresApiKeyAtTopLevel)
            .build());
  }

  private enum SecurityRequirementsExtractor
      implements Function<SecurityRequirement, Map<String, List<String>>> {
    INSTANCE;

    @Override
    public Map<String, List<String>> apply(SecurityRequirement reqs) {
      return reqs.getRequirements();
    }
  }

  private boolean isApiKeyRequired(
      Iterable<Map<String, List<String>>> securityRequirements, boolean topLevelApiKeyValue) {
    if (securityRequirements == null) {
      // If there're no specific security requirements, use the API
      // default.
      return topLevelApiKeyValue;
    }

    boolean apiKeyRequired = false;
    for (Map<String, List<String>> securityReq : securityRequirements) {
      if (securityReq == null) {
        // Null requirements: this should never happen, but if it
        // does, assume that it's a set of requirements without an API
        // key definition section, which means an API key is not
        // required (Swagger default).
        return false;
      }

      boolean foundApiKeyDefinition = false;
      for (String schema : apiKeyDefinitions) {
        if (securityReq.containsKey(schema)) {
          // The security requirement contains a requirement.
          foundApiKeyDefinition = true;
          break;
        }
      }
      if (!foundApiKeyDefinition) {
        // We found at least one security requirement with no API key
        // section, so an API key is not required.
        return false;
      }

      // We've found at least one security requirement with an API key
      // definition; if we don't find a security requirement without
      // an API key definition (i.e. a security requirement which
      // causes an early return from this function), an API key is
      // required.
      apiKeyRequired = true;
    }

    return apiKeyRequired;
  }
}
