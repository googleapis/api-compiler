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

package com.google.api.tools.framework.importers.swagger.aspects.auth.model;

import com.google.api.AuthRequirement;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;

/** Class representing authentication schema in security requirement extension in swagger. */
public class SecurityRequirementModel {

  // List of audiences
  private final List<String> audiences;

  public List<String> getAudiences() {
    return audiences;
  }

  public SecurityRequirementModel(List<String> audiences) {
    this.audiences = ImmutableList.copyOf(audiences);
  }

  public static ImmutableList<AuthRequirement> createAuthRequirements(
      Map<String, SecurityRequirementModel> authRequirements) {
    ImmutableList.Builder<AuthRequirement> authRequirementsList = ImmutableList.builder();
    for (Map.Entry<String, SecurityRequirementModel> authReq : authRequirements.entrySet()) {
      AuthRequirement.Builder authRequirement = AuthRequirement.newBuilder();
      authRequirement.setProviderId(authReq.getKey());
      authRequirement.setAudiences(Joiner.on(",").join(authReq.getValue().getAudiences()));
      authRequirementsList.add(authRequirement.build());
    }
    return authRequirementsList.build();
  }

  public static Map<String, SecurityRequirementModel> flattenSecurityRequirementModel(
      List<Map<String, SecurityRequirementModel>> securityRequirementsResult) {
    Map<String, SecurityRequirementModel> securityRequirementsResultFlattened =
        Maps.newLinkedHashMap();
    for (Map<String, SecurityRequirementModel> schemas : securityRequirementsResult) {
      securityRequirementsResultFlattened.putAll(schemas);
    }
    return securityRequirementsResultFlattened;
  }

  public static Map<String, SecurityRequirementModel> mergeSecurityRequirementModel(
      Map<String, SecurityRequirementModel> securityRequirementsFromCustomExtension,
      Map<String, SecurityRequirementModel> securityRequirementsFromSecurityObject) {
    if (securityRequirementsFromCustomExtension == null
        && securityRequirementsFromSecurityObject == null) {
      return null;
    }

    Map<String, SecurityRequirementModel> result = Maps.newLinkedHashMap();
    if (securityRequirementsFromSecurityObject != null) {
      result.putAll(securityRequirementsFromSecurityObject);
    }
    // Overwrite if the same definition is referenced inside the custom extension.
    if (securityRequirementsFromCustomExtension != null) {
      result.putAll(securityRequirementsFromCustomExtension);
    }

    return result;
  }

  /** Converts Map<String, List<String> to Map<String, SecurityRequirementModel>. */
  public enum SecurityRequirementModelExtractor
      implements Function<Map<String, List<String>>, Map<String, SecurityRequirementModel>> {
    INSTANCE;

    @Override
    public Map<String, SecurityRequirementModel> apply(Map<String, List<String>> reqs) {
      Map<String, SecurityRequirementModel> result = Maps.newLinkedHashMap();
      for (String schemaName : reqs.keySet()) {
        result.put(schemaName, new SecurityRequirementModel(Lists.<String>newArrayList()));
      }
      return result;
    }
  }
}
