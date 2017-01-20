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

import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.MultiSwaggerParser.SwaggerFile;
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
public class SwaggerToService {

  static final String WILDCARD_URL_PATH = "/**";

  private final List<SwaggerFile> swaggers;
  private final String serviceName;

  private final List<FileWrapper> additionalConfigs;
  private final DiagCollector diagCollector = new BoundedDiagCollector();

  /**
   * Initializes Swagger to Service config converter.
   *
   * @param swaggerFiles Swagger files.
   * @param serviceName A service name to use for generated service config. If empty, service name
   *     is extracted from the `host` field of the Swagger spec.
   * @param typeNamespace A namespace prefix used for all types in service config.
   * @param additionalConfigs
   */
  public SwaggerToService(
      List<FileWrapper> swaggerFiles,
      String serviceName,
      String typeNamespace,
      List<FileWrapper> additionalConfigs)
      throws SwaggerConversionException {
    Preconditions.checkState(
        swaggerFiles != null && !swaggerFiles.isEmpty(),
        "swaggerFilePathToContentMap cannot be null or empty");

    String actualTypeNamespace = typeNamespace == null ? "" : typeNamespace.trim();
    if (actualTypeNamespace.endsWith(".")) {
      actualTypeNamespace = actualTypeNamespace.substring(0, typeNamespace.length() - 1);
    }

    this.swaggers = MultiSwaggerParser.convert(swaggerFiles, actualTypeNamespace);
    this.serviceName = serviceName == null ? "" : serviceName.trim();
    this.additionalConfigs = additionalConfigs;
  }

  /** Creates {@link com.google.api.Service} from Swagger Objects, and returns it. */
  public Service createServiceConfig() throws SwaggerConversionException {
    new TopLevelBuilder()
        .setTopLevelFields(swaggers.get(0).serviceBuilder(), swaggers, serviceName);
    buildService(swaggers);
    aggregateAllDiagnostics(swaggers);
   List<Service.Builder> serviceBuilders = Lists.newArrayList();
   for (SwaggerFile swaggerFile : swaggers){
     serviceBuilders.add(swaggerFile.serviceBuilder());
   }
    return ServiceNormalizer.normalizeService(
        new ServiceMerger().merge(serviceBuilders), diagCollector, additionalConfigs);
  }

  private void buildService(List<SwaggerFile> swaggerFiles) {
    for (SwaggerFile swaggerFile : swaggerFiles) {
      for (AspectBuilder aspectBuilder : swaggerFile.conversionResources().aspectBuilders()) {
        aspectBuilder.addFromSwagger(swaggerFile.serviceBuilder(), swaggerFile.swagger());
      }
      swaggerFile
          .conversionResources()
          .apiFromSwagger()
          .addFromSwagger(swaggerFile.serviceBuilder(), swaggerFile.swagger());
    }
  }

  private void aggregateAllDiagnostics(List<SwaggerFile> swaggerFiles) {
    for (SwaggerFile swaggerFile : swaggerFiles) {
      for (Diag diag : swaggerFile.conversionResources().diagCollector().getDiags()) {
        diagCollector.addDiag(diag);
      }
    }
  }

  public DiagCollector getDiagCollector() {
    return diagCollector;
  }
}
