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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.api.AuthRequirement;
import com.google.api.AuthenticationRule;
import com.google.api.tools.framework.importers.swagger.SwaggerLocations;
import com.google.api.tools.framework.importers.swagger.aspects.auth.model.SecurityRequirementModel;
import com.google.api.tools.framework.importers.swagger.aspects.auth.model.SecurityRequirementModel.SecurityRequirementModelExtractor;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionUtils;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.swagger.models.Operation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utility class for creating {@link AuthenticationRule}s */
public class AuthRuleGenerator {

  private static final String SECURITY_REQUIREMENT_EXTENSION = "x-security";

  private final String namespacePrefix;
  private final DiagCollector diagCollector;
  private final AuthRequirementValidator authReqValidator;
  private final Set<String> authSchemaNames = Sets.newHashSet();

  public AuthRuleGenerator(String namespacePrefix, DiagCollector diagCollector) {
    this.namespacePrefix =
        (namespacePrefix.isEmpty() || namespacePrefix.endsWith("."))
            ? namespacePrefix
            : namespacePrefix + ".";
    this.diagCollector = diagCollector;
    authReqValidator = new AuthRequirementValidator(diagCollector);
  }

  public void registerAuthSchemaName(String authSchemaName) {
    authSchemaNames.add(authSchemaName);
  }

  /** Creates {@link AuthRequirement} from Swagger requirements per operations. */
  public AuthenticationRule createAuthRule(
      Operation operation, String operationType, String swaggerPath) {
    return createOAuthRule(operation, operationType, swaggerPath);
  }

  /** Creates {@link AuthRequirement} from Swagger requirements per operations. */
  private AuthenticationRule createOAuthRule(
      Operation operation, String operationType, String swaggerPath) {
    AuthenticationRule.Builder builder = AuthenticationRule.newBuilder();
    Map<String, SecurityRequirementModel> authRequirements =
        getSecurityRequirements(
            operation.getSecurity(),
            operation.getVendorExtensions(),
            SwaggerLocations.createOperationLocation(operationType, swaggerPath));
    if (authRequirements != null) {
      builder.addAllRequirements(SecurityRequirementModel.createAuthRequirements(authRequirements));
      builder.setSelector(
          namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()));
      return builder.build();
    }
    return null;
  }

  /**
   * Creates SecurityRequirementModel objects from security object and vendor extensions inside
   * openapi spec.
   */
  public Map<String, SecurityRequirementModel> getSecurityRequirements(
      Iterable<Map<String, List<String>>> securityObjectInSwagger,
      Map<String, Object> extensions,
      Location location) {
    Map<String, SecurityRequirementModel> securityRequirementsFromCustomExtension = null;
    Map<String, SecurityRequirementModel> securityRequirementsFromSecurityObject = null;
    String securityRequirementExtensionUsed =
        VendorExtensionUtils.usedExtension(
            diagCollector, extensions, SECURITY_REQUIREMENT_EXTENSION);
    if (!Strings.isNullOrEmpty(securityRequirementExtensionUsed)) {
      List<Map<String, SecurityRequirementModel>> securityRequirements =
          loadFromSwaggerExtension(extensions.get(SECURITY_REQUIREMENT_EXTENSION));
      if (securityRequirements == null) {
        diagCollector.addDiag(
            Diag.error(
                location,
                "Extension %s does not have the valid value. Please check "
                    + "the documentation for its schema",
                SECURITY_REQUIREMENT_EXTENSION));
        return null;
      }
      securityRequirementsFromCustomExtension =
          validateAndFlattenSecurityRequirements(securityRequirements, location, true);
    }

    // Get oAuth security requirements from security object of swagger (not our custom
    // x-security extension)
    if (securityObjectInSwagger != null) {
      securityRequirementsFromSecurityObject =
          validateAndFlattenSecurityRequirements(
              Iterables.transform(
                  securityObjectInSwagger, SecurityRequirementModelExtractor.INSTANCE),
              location,
              false);
    }

    return SecurityRequirementModel.mergeSecurityRequirementModel(
        securityRequirementsFromCustomExtension, securityRequirementsFromSecurityObject);
  }

  private Map<String, SecurityRequirementModel> validateAndFlattenSecurityRequirements(
      Iterable<Map<String, SecurityRequirementModel>> securityRequirements,
      Location location,
      boolean isFromExtension) {
    List<Map<String, SecurityRequirementModel>> securityRequirementsResult = Lists.newArrayList();
    for (Map<String, SecurityRequirementModel> schemas : securityRequirements) {
      Map<String, SecurityRequirementModel> securityRequirementsToLogicallyAnd =
          Maps.newLinkedHashMap();
      for (Map.Entry<String, SecurityRequirementModel> schema : schemas.entrySet()) {
        String authSchemaName = schema.getKey();
        if (isFromExtension
            && authReqValidator.extensionHasErrors(
                location, schema, authSchemaName, authSchemaNames)) {
          return null;
        }
        if (authSchemaNames.contains(authSchemaName)) {
          if (!securityRequirementsToLogicallyAnd.isEmpty()) {
            authReqValidator.reportLogicallyAndedSchemaError(
                location, securityRequirementsToLogicallyAnd, authSchemaName, isFromExtension);
            return null;
          }
          securityRequirementsToLogicallyAnd.put(authSchemaName, schema.getValue());
        }
      }
      if (!securityRequirementsToLogicallyAnd.isEmpty()) {
        securityRequirementsResult.add(securityRequirementsToLogicallyAnd);
      }
    }
    return SecurityRequirementModel.flattenSecurityRequirementModel(securityRequirementsResult);
  }

  /** Verified if the extension schema is correct. */
  private static List<Map<String, SecurityRequirementModel>> loadFromSwaggerExtension(
      Object jsonObject) {
    Gson gson = new GsonBuilder().create();
    ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
    Type typeSecurityReqExtension =
        new TypeToken<List<Map<String, SecurityRequirementModel>>>() {}.getType();
    try {
      String jsonString = ow.writeValueAsString(jsonObject);
      return gson.fromJson(jsonString, typeSecurityReqExtension);
    } catch (JsonProcessingException | JsonParseException ex) {
      return null;
    }
  }
}
