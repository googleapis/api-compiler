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
package com.google.api.tools.framework.importers.swagger.aspects.quota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.MetricDescriptor;
import com.google.api.Quota;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionProtoConverter;
import com.google.api.tools.framework.importers.swagger.extensions.ServiceManagementExtension;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import io.swagger.models.Swagger;
import java.io.IOException;
import java.util.List;

/** Builder for Quota and Quota related Metric Descriptors of service config, from OpenAPI. */
public class QuotaBuilder implements AspectBuilder {

  private final DiagCollector diagCollector;

  public QuotaBuilder(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {
    VendorExtensionProtoConverter extensionConverter =
        new VendorExtensionProtoConverter(swagger.getVendorExtensions(), diagCollector);
    if (extensionConverter.hasExtension(ExtensionNames.MANAGEMENT_SWAGGER_EXTENSION)) {
      ServiceManagementExtension serviceManagementExtension = readExtension(swagger);
      serviceBuilder.addAllMetrics(parseMetrics(serviceManagementExtension, extensionConverter));
    }
  }

  //TODO(user): Refactor this out when we add more fields under 'x-google-management'
  private ServiceManagementExtension readExtension(Swagger swagger) {
    return new Gson()
        .fromJson(
            swagger
                .getVendorExtensions()
                .get(ExtensionNames.MANAGEMENT_SWAGGER_EXTENSION)
                .toString(),
            ServiceManagementExtension.class);
  }

  private List<MetricDescriptor> parseMetrics(
      ServiceManagementExtension extension, VendorExtensionProtoConverter extensionConverter) {
    if (extension.getMetrics() == null) {
      return ImmutableList.of();
    }
    try {
      String extensionJson = new ObjectMapper().writer().writeValueAsString(extension.getMetrics());
      return extensionConverter.convertJsonArrayToProto(
          MetricDescriptor.getDefaultInstance(),
          new ObjectMapper().readTree(extensionJson),
          "metrics");
    } catch (IOException ex) {
      diagCollector.addDiag(
          Diag.error(
              new SimpleLocation("metrics"),
              "Extension %s cannot be converted into proto type %s. Details: %s",
              "quota",
              MetricDescriptor.getDescriptor().getFullName(),
              ex.getMessage()));
      return ImmutableList.of();
    }
  }

}
