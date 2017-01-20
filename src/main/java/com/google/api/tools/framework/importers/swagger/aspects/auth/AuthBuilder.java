/*
 * Copyright (C) 2016 Google, Inc.
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

package com.google.api.tools.framework.importers.swagger.aspects.auth;

import com.google.api.AuthProvider;
import com.google.api.Authentication;
import com.google.api.AuthenticationRule;
import com.google.api.Service;
import com.google.api.Usage;
import com.google.api.UsageRule;
import com.google.api.tools.framework.importers.swagger.SwaggerLocations;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.auth.model.SecurityRequirementModel;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.importers.swagger.aspects.utils.SwaggerUtils;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionUtils;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import io.swagger.models.Operation;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.ApiKeyAuthDefinition;
import io.swagger.models.auth.In;
import io.swagger.models.auth.SecuritySchemeDefinition;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Class to create {@link AuthenticationRule} from swagger operations. */
public final class AuthBuilder implements AspectBuilder {

  private final DiagCollector diagCollector;
  private final Set<String> apiKeyDefinitions = new LinkedHashSet<>();
  private boolean requiresApiKeyAtTopLevel = false;
  private final AuthRuleGenerator authRuleGenerator;
  private final String namespacePrefix;

  public AuthBuilder(
      String namespace, DiagCollector diagCollector, AuthRuleGenerator authRuleGenerator) {
    this.namespacePrefix =
        (namespace.isEmpty() || namespace.endsWith(".")) ? namespace : namespace + ".";
    this.diagCollector = diagCollector;
    this.authRuleGenerator = authRuleGenerator;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {
    if (swagger.getSecurityDefinitions() == null) {
      return;
    }
    TreeSet<String> swaggerSecurityDefNames =
        Sets.newTreeSet(swagger.getSecurityDefinitions().keySet());
    for (String swaggerSecurityDefName : swaggerSecurityDefNames) {

      addAuthProvider(
          serviceBuilder,
          swaggerSecurityDefName,
          swagger.getSecurityDefinitions().get(swaggerSecurityDefName));
    }
    addSecurityRequirementForEntireService(serviceBuilder, swagger.getSecurity());
    addSecurityRequirementExtensionForEntireService(serviceBuilder, swagger);
  }

  /** Creates {@link AuthProvider} from Swagger SecuritySchemeDefinition. */
  private void addAuthProvider(
      Service.Builder serviceBuilder,
      String securitySchemaName,
      SecuritySchemeDefinition securitySchema) {
    if (securitySchema == null) {
      return;
    }
    if (securitySchema.getType().equalsIgnoreCase("oauth2")) {
      AuthProvider.Builder authProviderBuilder = AuthProvider.newBuilder();
      authProviderBuilder.setId(securitySchemaName);
      String oauthIssuerSwaggerExtensionUsed =
          VendorExtensionUtils.usedExtension(
              diagCollector,
              securitySchema.getVendorExtensions(),
              ExtensionNames.OAUTH_ISSUER_SWAGGER_EXTENSION,
              ExtensionNames.OAUTH_ISSUER_SWAGGER_EXTENSION_LEGACY);
      if (!Strings.isNullOrEmpty(oauthIssuerSwaggerExtensionUsed)) {
        String oauthIssuerSwaggerExtensionObject =
            VendorExtensionUtils.getExtensionValue(
                securitySchema.getVendorExtensions(),
                String.class,
                diagCollector,
                oauthIssuerSwaggerExtensionUsed);

        if (oauthIssuerSwaggerExtensionObject != null) {
          authProviderBuilder.setIssuer(oauthIssuerSwaggerExtensionObject);
        }
      }

      String jwksSwaggerExtensionUsed =
          VendorExtensionUtils.usedExtension(
              diagCollector,
              securitySchema.getVendorExtensions(),
              ExtensionNames.JWKS_SWAGGER_EXTENSION,
              ExtensionNames.JWKS_SWAGGER_EXTENSION_LEGACY);
      if (!Strings.isNullOrEmpty(jwksSwaggerExtensionUsed)) {
        String jwksSwaggerExtensionValue =
            VendorExtensionUtils.getExtensionValue(
                securitySchema.getVendorExtensions(),
                String.class,
                diagCollector,
                jwksSwaggerExtensionUsed);
        if (jwksSwaggerExtensionValue != null) {
          authProviderBuilder.setJwksUri(jwksSwaggerExtensionValue);
        }
      }

      String audiencesSwaggerExtensionUsed =
          VendorExtensionUtils.usedExtension(
              diagCollector,
              securitySchema.getVendorExtensions(),
              ExtensionNames.AUDIENCES_SWAGGER_EXTENSION);
      if (!Strings.isNullOrEmpty(audiencesSwaggerExtensionUsed)) {
        String audiencesSwaggerExtensionValue =
            VendorExtensionUtils.getExtensionValue(
                securitySchema.getVendorExtensions(),
                String.class,
                diagCollector,
                audiencesSwaggerExtensionUsed);
        if (audiencesSwaggerExtensionValue != null) {
          authProviderBuilder.setAudiences(audiencesSwaggerExtensionValue);
        }
      }

      Authentication.Builder authenticationBuilder = serviceBuilder.getAuthenticationBuilder();
      authenticationBuilder.addProviders(authProviderBuilder.build());
      authRuleGenerator.registerAuthSchemaName(securitySchemaName);
    } else if (securitySchema.getType().equalsIgnoreCase("apiKey")) {
      ApiKeyAuthDefinition apiKeyDef = (ApiKeyAuthDefinition) securitySchema;
      if (isValidApiKeyDefinition(apiKeyDef)) {
        apiKeyDefinitions.add(securitySchemaName);
      }
    } else {
      diagCollector.addDiag(
          Diag.warning(
              SimpleLocation.UNKNOWN,
              "Security Schema '%s' is not supported. Only support schema are OAuth2",
              securitySchemaName));
    }
  }

  public UsageRule createUsageRule(Operation operation, String operationType, String swaggerPath) {
    return createUsageRulePerMethod(
        operation.getSecurity(),
        operationType,
        swaggerPath,
        namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()));
  }

  private UsageRule createUsageRulePerMethod(
      Iterable<Map<String, List<String>>> openApiSecurityObject,
      String operationType,
      String swaggerPath,
      String selector) {
    boolean perMethodApiKeyRequired =
        isApiKeyRequired(openApiSecurityObject, requiresApiKeyAtTopLevel, apiKeyDefinitions);
    if (!perMethodApiKeyRequired) {
      // TODO(): Remove this check once we have warnings suppression implemented.
      if (!swaggerPath.equals(SwaggerUtils.WILDCARD_URL_PATH)) {
        diagCollector.addDiag(
            Diag.warning(
                SwaggerLocations.createOperationLocation(operationType, swaggerPath),
                "Operation does not require an API key; callers may invoke the method "
                    + "without specifying an associated API-consuming project. "
                    + "To enable API key all the SecurityRequirement Objects "
                    + "(https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md#security-requirement-object) "
                    + "inside security definition must reference at least one SecurityDefinition "
                    + "of type : 'apiKey'."));
      }
    }

    return UsageRule.newBuilder()
        .setSelector(selector)
        .setAllowUnregisteredCalls(!perMethodApiKeyRequired)
        .build();
  }

  public static boolean isApiKeyRequired(
      Iterable<Map<String, List<String>>> openApiSecurityObject,
      boolean topLevelApiKeyValue,
      Set<String> apiKeyDefinitions) {
    if (openApiSecurityObject == null) {
      // If there're no specific security requirements, use the API
      // default.
      return topLevelApiKeyValue;
    }

    boolean apiKeyRequired = false;
    for (Map<String, List<String>> securityReqWithLogicalAnd : openApiSecurityObject) {
      if (securityReqWithLogicalAnd == null) {
        // Null requirements: this should never happen, but if it
        // does, assume that it's a set of requirements without an API
        // key definition section, which means an API key is not
        // required (Swagger default).
        return false;
      }

      boolean foundApiKeyDefinition = false;
      for (String schema : apiKeyDefinitions) {
        if (securityReqWithLogicalAnd.containsKey(schema)) {
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
  /**
   * Checks if the defined apiKey is valid or not. Only apiKey definition with name as 'key' and
   * 'in' as 'query' are allowed"
   */
  private boolean isValidApiKeyDefinition(ApiKeyAuthDefinition apiKeydef) {
    if (apiKeydef.getName().equalsIgnoreCase("key") || apiKeydef.getIn() == In.QUERY) {
      return true;
    } else {
      diagCollector.addDiag(
          Diag.warning(
              SimpleLocation.UNKNOWN,
              "apiKey '%s' is ignored. Only apiKey with 'name' as 'key' and 'in' as 'query' are "
                  + "supported",
              apiKeydef.getName()));
      return false;
    }
  }

  /** Adds auth security requirement for the entire service. */
  public void addSecurityRequirementExtensionForEntireService(
      Service.Builder serviceBuilder, Swagger swagger) {
    AuthenticationRule.Builder builder = AuthenticationRule.newBuilder();
    Map<String, SecurityRequirementModel> authRequirements =
        authRuleGenerator.getSecurityRequirements(
            swagger.getSecurity() != null
                ? Iterables.transform(swagger.getSecurity(), SecurityRequirementsExtractor.INSTANCE)
                : null,
            swagger.getVendorExtensions(),
            new SimpleLocation("Swagger Spec"));
    if (authRequirements != null && !authRequirements.isEmpty()) {
      builder.addAllRequirements(SecurityRequirementModel.createAuthRequirements(authRequirements));
      builder.setSelector("*");
      Authentication.Builder authenticationBuilder = serviceBuilder.getAuthenticationBuilder();
      authenticationBuilder.addRules(builder.build());
    }
  }

  public void addSecurityRequirementForEntireService(
      Service.Builder serviceBuilder, Iterable<SecurityRequirement> securityRequirements) {
    if (securityRequirements == null) {
      requiresApiKeyAtTopLevel = false;
    } else {
      requiresApiKeyAtTopLevel =
          isApiKeyRequired(
              Iterables.transform(securityRequirements, SecurityRequirementsExtractor.INSTANCE),
              false,
              apiKeyDefinitions);
    }
    Usage.Builder usageBuilder = serviceBuilder.getUsageBuilder();
    usageBuilder.addRules(
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
}

