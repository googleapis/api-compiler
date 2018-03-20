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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.Service;
import com.google.api.tools.framework.aspects.control.model.ControlConfigUtil;
import com.google.api.tools.framework.importers.swagger.MultiOpenApiParser.OpenApiFile;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimaps;
import com.google.protobuf.UInt32Value;
import io.swagger.models.Info;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Sets top level fields such as configVersion in a {@link Service}, based on fields in {@link
 * OpenApiFile}s
 */
public class TopLevelBuilder {

  private static final int TOOLS_CONFIG_VERSION = 3;

  public void setTopLevelFields(
      Service.Builder serviceBuilder, List<OpenApiFile> openApiFiles, String defaultName)
      throws OpenApiConversionException {
    createServiceInfoFromOpenApi(serviceBuilder, openApiFiles);
    setServiceName(serviceBuilder, openApiFiles, defaultName);
    //TODO: this and the host validation belong in a merge phase that does not exist yet.
    validateVersions(openApiFiles);
    applyThirdPartyApiSettings(serviceBuilder);
  }

  /** Sets special configuration needed for 3rd party Endpoints APIs. */
  private void applyThirdPartyApiSettings(Service.Builder serviceBuilder) {
    serviceBuilder.getControlBuilder().setEnvironment(ControlConfigUtil.PROD_SERVICE_CONTROL);

    // Set the config version to 3.
    serviceBuilder.setConfigVersion(
        UInt32Value.newBuilder().setValue(TOOLS_CONFIG_VERSION).build());
  }

  /**
   * Adds additional information to {@link Service} object.
   *
   * @throws OpenApiConversionException
   */
  private void createServiceInfoFromOpenApi(
      Service.Builder serviceBuilder, List<OpenApiFile> openApiFiles)
      throws OpenApiConversionException {
    for (OpenApiFile openApiFile : openApiFiles) {
      //TODO(user): need better way to resolve conflicts here
      if (openApiFile.swagger().getInfo() != null) {
        Info info = openApiFile.swagger().getInfo();
        if (info.getTitle() != null) {
          serviceBuilder.setTitle(info.getTitle());
        }
        if (info.getDescription() != null) {
          serviceBuilder.getDocumentationBuilder().setSummary(info.getDescription());
        }
      }
    }
  }

  private void setServiceName(
      Service.Builder serviceBuilder, List<OpenApiFile> openApiFiles, String defaultName)
      throws OpenApiConversionException {
    String serviceName = defaultName; // Try explicitly provided service name first.
    if (Strings.isNullOrEmpty(serviceName)) {
      Set<String> definedHostNames = getHosts(openApiFiles);
      if (definedHostNames.isEmpty()) {
        throw new OpenApiConversionException(
            "Service name must be provided either explicitly or in OpenAPI 'host' value.");
      } else if (definedHostNames.size() > 1) {
        throw new OpenApiConversionException(
            String.format(
                "Different 'host' values cannot be set in multiple OpenAPI files. "
                    + "Found Hosts: {%s}",
                Joiner.on(", ").join(definedHostNames)));
      } else {
        serviceName = Iterables.getOnlyElement(definedHostNames);
      }
    }
    serviceBuilder.setName(serviceName);
  }

  private static Set<String> getHosts(List<OpenApiFile> openApiFiles) {
    ImmutableSet.Builder<String> hostNames = ImmutableSet.builder();
    for (OpenApiFile openApiFile : openApiFiles) {
      String hostname = openApiFile.swagger().getHost();
      if (!StringUtils.isBlank(hostname)) {
        hostNames.add(hostname.trim());
      }
    }
    return hostNames.build();
  }

  private static void validateVersions(List<OpenApiFile> openApiFiles)
      throws OpenApiConversionException {
    final ListMultimap<String, OpenApiFile> apis = Multimaps.index(openApiFiles, f -> f.apiName());
    for (String apiName : apis.keySet()) {
      final List<OpenApiFile> files = apis.get(apiName);
      if (files.size() > 1) {
        final List<String> locations = Lists.newArrayList();
        for (OpenApiFile file : files) {
          locations.add(
              String.format("%s:%s", file.filename(), file.swagger().getInfo().getVersion()));
        }
        throw new OpenApiConversionException(
            String.format(
                "OpenAPI files includes conflicting API versions. Files and Versions found: {%s}",
                Joiner.on(", ").join(locations)));
      }
    }
  }
}
