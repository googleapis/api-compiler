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

import com.google.api.tools.framework.importers.swagger.aspects.auth.model.SecurityRequirementModel;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Utility class for validating {@link SecurityRequirementModel}s */
public class AuthRequirementValidator {

  private static final String SECURITY_REQUIREMENT_EXTENSION = "x-security";

  private final DiagCollector diagCollector;

  public AuthRequirementValidator(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  public boolean extensionHasErrors(
      Location location,
      Map.Entry<String, SecurityRequirementModel> schema,
      String authSchemaName,
      Set<String> validSchemaNames) {
    if (!validSchemaNames.contains(authSchemaName)) {
      diagCollector.addDiag(
          Diag.error(
              location,
              "Schema '%s' referenced in extension %s does not have a "
                  + "valid value. Please check the documentation for its schema.",
              authSchemaName,
              SECURITY_REQUIREMENT_EXTENSION));
      return true;
    }
    List<String> audiences = schema.getValue().getAudiences();
    if (audiences == null) {
      diagCollector.addDiag(
          Diag.error(
              location,
              "Extension %s does not have a valid value. Please "
                  + "check the documentation for its schema.",
              SECURITY_REQUIREMENT_EXTENSION));
      return true;
    }
    return false;
  }

  public void reportLogicallyAndedSchemaError(
      Location location,
      Map<String, SecurityRequirementModel> securityRequirementsToLogicallyAnd,
      String authSchemaName,
      boolean isFromExtension) {
    Set<String> logicallyAndSchemas =
        Sets.newLinkedHashSet(securityRequirementsToLogicallyAnd.keySet());
    logicallyAndSchemas.add(authSchemaName);
    diagCollector.addDiag(
        Diag.error(
            location,
            "%s section contains multiple security definitions '%s' within the scope (Security "
            + "Requirement Object) that get logically ANDed (both requirements need "
                + "to be satisfied to allow the request). We only support allowing logical OR "
                + "between security definitions. Therefore, please write requirements in "
                + "different objects inside the array (which would mean logical OR, that is, any "
                + "of the requirement should be sufficient to allow the request.)",
            isFromExtension ? SECURITY_REQUIREMENT_EXTENSION : "security",
            Joiner.on(",").join(logicallyAndSchemas)));
  }
}
