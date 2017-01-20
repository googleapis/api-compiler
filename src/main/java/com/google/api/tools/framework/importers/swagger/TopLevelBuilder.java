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
import com.google.api.tools.framework.importers.swagger.MultiSwaggerParser.SwaggerFile;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.UInt32Value;
import io.swagger.models.Info;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

/**
 * Sets top level fields such as configVersion in a {@link Service}, based on fields in {@link
 * SwaggerFile}s
 */
public class TopLevelBuilder {

  private static final int TOOLS_CONFIG_VERSION = 3;

  public void setTopLevelFields(
      Service.Builder serviceBuilder, List<SwaggerFile> swaggers, String defaultName)
      throws SwaggerConversionException {
    createServiceInfoFromSwagger(serviceBuilder, swaggers);
    setServiceName(serviceBuilder, swaggers, defaultName);
    //TODO: this and the host validation belong in a merge phase that does not exist yet.
    validateVersions(swaggers);
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
   * @throws SwaggerConversionException
   */
  private void createServiceInfoFromSwagger(
      Service.Builder serviceBuilder, List<SwaggerFile> swaggers)
      throws SwaggerConversionException {
    for (SwaggerFile swagger : swaggers) {
      //TODO(user): need better way to resolve conflicts here
      if (swagger.swagger().getInfo() != null) {
        Info swaggerInfo = swagger.swagger().getInfo();
        if (swaggerInfo.getTitle() != null) {
          serviceBuilder.setTitle(swaggerInfo.getTitle());
        }
        if (swaggerInfo.getDescription() != null) {
          serviceBuilder.getDocumentationBuilder().setSummary(swaggerInfo.getDescription());
        }
      }
    }
  }

  private void setServiceName(
      Service.Builder serviceBuilder, List<SwaggerFile> swaggerFiles, String defaultName)
      throws SwaggerConversionException {
    String serviceName = defaultName; // Try explicitly provided service name first.
    if (Strings.isNullOrEmpty(serviceName)) {
      Set<String> uniqueSwaggerHostNames = getSwaggerHosts(swaggerFiles);
      if (uniqueSwaggerHostNames.isEmpty()) {
        throw new SwaggerConversionException(
            "Service name must be provided either explicitly or in Swagger 'host' value.");
      } else if (uniqueSwaggerHostNames.size() > 1) {
        throw new SwaggerConversionException(
            String.format(
                "Different 'host' values cannot be set in multiple Swagger files, "
                    + "found Hosts: {%s}",
                Joiner.on(", ").join(uniqueSwaggerHostNames)));
      } else {
        serviceName = Iterables.getOnlyElement(uniqueSwaggerHostNames);
      }
    }
    serviceBuilder.setName(serviceName);
  }

  private static Set<String> getSwaggerHosts(List<SwaggerFile> swaggers) {
    ImmutableSet.Builder<String> hostNames = ImmutableSet.builder();
    for (SwaggerFile swagger : swaggers) {
      String hostname = swagger.swagger().getHost();
      if (!StringUtils.isBlank(hostname)) {
        hostNames.add(hostname.trim());
      }
    }
    return hostNames.build();
  }

  private static void validateVersions(List<SwaggerFile> swaggers)
      throws SwaggerConversionException {
    List<String> versions = Lists.newArrayList();
    List<String> versionLocations = Lists.newArrayList();
    for (SwaggerFile swagger : swaggers) {
      String version = swagger.swagger().getInfo().getVersion();
      versions.add(version);
      versionLocations.add(String.format("%s:%s", swagger.filename(), version));
    }
    if (listHasDuplicates(versions)) {
      throw new SwaggerConversionException(
          String.format(
              "Multiple OpenApi files cannot have the same 'info.version' value. "
                  + "Files and Versions found: {%s}",
              Joiner.on(", ").join(versionLocations)));
    }
  }

  private static boolean listHasDuplicates(List<?> elements) {
    return Sets.newHashSet(elements).size() != elements.size();
  }
}
