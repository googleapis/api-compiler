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

import com.google.api.MetricDescriptor;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionProtoConverter;
import com.google.api.tools.framework.model.DiagCollector;
import io.swagger.models.Swagger;

/** Builder for the {@link Quota} section of service config, from OpenAPI. */
public class QuotaBuilder implements AspectBuilder {

  private static final String METRIC_DEFINITIONS_SWAGGER_EXTENSION = "x-google-metric-definitions";

  private final DiagCollector diagCollector;

  public QuotaBuilder(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {
    VendorExtensionProtoConverter extensionConverter =
        new VendorExtensionProtoConverter(swagger.getVendorExtensions(), diagCollector);
    addMetricDefinitions(serviceBuilder, extensionConverter);
  }

  private static void addMetricDefinitions(
      Service.Builder serviceBuilder, VendorExtensionProtoConverter extensionConverter) {
    if (extensionConverter.hasExtension(METRIC_DEFINITIONS_SWAGGER_EXTENSION)) {
      serviceBuilder.addAllMetrics(
          extensionConverter.convertExtensionToProtos(
              MetricDescriptor.getDefaultInstance(), METRIC_DEFINITIONS_SWAGGER_EXTENSION));
    }
  }
}
