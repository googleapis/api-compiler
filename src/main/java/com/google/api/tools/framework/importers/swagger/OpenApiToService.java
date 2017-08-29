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
import com.google.api.tools.framework.importers.swagger.MultiOpenApiParser.OpenApiFile;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.merger.ServiceMerger;
import com.google.api.tools.framework.model.BoundedDiagCollector;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.tools.FileWrapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import io.swagger.models.Swagger;
import java.util.List;

/** Class to create a {@link Service} instance from a group of {@link Swagger} objects. */
public class OpenApiToService {

  static final String WILDCARD_URL_PATH = "/**";

  private final List<OpenApiFile> openApis;
  private final String serviceName;

  private final List<FileWrapper> additionalConfigs;
  private final DiagCollector diagCollector = new BoundedDiagCollector();

  /**
   * Initializes OpenAPI to Service config converter.
   *
   * @param openApiFiles OpenAPI files.
   * @param serviceName A service name to use for generated service config. If empty, service name
   *     is extracted from the `host` field of the OpenAPI spec.
   * @param typeNamespace A namespace prefix used for all types in service config.
   * @param additionalConfigs
   */
  public OpenApiToService(
      List<FileWrapper> openApiFiles,
      String serviceName,
      String typeNamespace,
      List<FileWrapper> additionalConfigs)
      throws OpenApiConversionException {
    Preconditions.checkState(
        openApiFiles != null && !openApiFiles.isEmpty(), "openApiFiles cannot be null or empty");

    String actualTypeNamespace = typeNamespace == null ? "" : typeNamespace.trim();
    if (actualTypeNamespace.endsWith(".")) {
      actualTypeNamespace = actualTypeNamespace.substring(0, typeNamespace.length() - 1);
    }

    this.openApis = MultiOpenApiParser.convert(openApiFiles, actualTypeNamespace);
    this.serviceName = serviceName == null ? "" : serviceName.trim();
    this.additionalConfigs = additionalConfigs;
  }

  /** Creates {@link com.google.api.Service} from Swagger Objects, and returns it. */
  public Service createServiceConfig() throws OpenApiConversionException {
    new TopLevelBuilder()
        .setTopLevelFields(openApis.get(0).serviceBuilder(), openApis, serviceName);
    buildService(openApis);
    aggregateAllDiagnostics(openApis);
    if (diagCollector.hasErrors()) {
      return null;
    }
    List<Service.Builder> serviceBuilders = Lists.newArrayList();
    for (OpenApiFile openApiFile : openApis) {
      serviceBuilders.add(openApiFile.serviceBuilder());
    }
    return ServiceNormalizer.normalizeService(
        new ServiceMerger().merge(serviceBuilders), diagCollector, additionalConfigs);
  }

  private void buildService(List<OpenApiFile> openApiFiles) {
    for (OpenApiFile openApiFile : openApiFiles) {
      for (AspectBuilder aspectBuilder : openApiFile.conversionResources().aspectBuilders()) {
        aspectBuilder.addFromSwagger(openApiFile.serviceBuilder(), openApiFile.swagger());
      }
      openApiFile
          .conversionResources()
          .apiFromSwagger()
          .addFromSwagger(openApiFile.serviceBuilder(), openApiFile.swagger());
    }
  }

  private void aggregateAllDiagnostics(List<OpenApiFile> openApiFiles) {
    for (OpenApiFile openApiFile : openApiFiles) {
      for (Diag diag : openApiFile.conversionResources().diagCollector().getDiags()) {
        diagCollector.addDiag(diag);
      }
    }
  }

  public DiagCollector getDiagCollector() {
    return diagCollector;
  }
}
