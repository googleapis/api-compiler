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
package com.google.api.tools.framework.importers.swagger.aspects.endpoint;

import com.google.api.Endpoint;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionProtoConverter;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.collect.Iterables;
import io.swagger.models.Swagger;
import java.util.List;

/** Builder for the {@link Endpoint} section of service config, from OpenAPI. */
public class EndpointBuilder implements AspectBuilder {
  private final DiagCollector diagCollector;

  public EndpointBuilder(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {

    VendorExtensionProtoConverter extensionConverter =
        new VendorExtensionProtoConverter(swagger.getVendorExtensions(), diagCollector);
    if (extensionConverter.hasExtension(ExtensionNames.ENDPOINTS_EXTENSION_NAME)) {
      serviceBuilder.addAllEndpoints(
          extensionConverter.convertExtensionToProtos(
              Endpoint.getDefaultInstance(), ExtensionNames.ENDPOINTS_EXTENSION_NAME));
      validEndpointExtension(serviceBuilder, swagger.getHost());
    }
  }

  private void validEndpointExtension(Service.Builder serviceBuilder, String host) {
    List<Endpoint> endpoints = serviceBuilder.getEndpointsList();

    if (endpoints.isEmpty()) {
      return;
    }

    SimpleLocation endpointLocation = new SimpleLocation(ExtensionNames.ENDPOINTS_EXTENSION_NAME);
    // At most one Endpoint entry is allowed.
    if (endpoints.size() > 1) {
      String errorMessage =
          String.format(
              "OpenAPI spec is invalid. Multiple endpoint entries are defined in the "
                  + "extension '%s'. At most one entry is allowed.",
              ExtensionNames.ENDPOINTS_EXTENSION_NAME);
      diagCollector.addDiag(
          Diag.error(endpointLocation, errorMessage, ExtensionNames.ENDPOINTS_EXTENSION_NAME));
      return;
    }

    // The endpoint name must be as same as the serivce name.
    if (!host.equalsIgnoreCase(Iterables.getOnlyElement(endpoints).getName())) {
      String errorMessage =
          String.format(
              "OpenAPI spec is invalid. The endpoint name: '%s' "
                  + "defined in the extension '%s' is different than the host name: '%s'. Please "
                  + "use the host name as the endpoint name.",
              endpoints.get(0).getName(), ExtensionNames.ENDPOINTS_EXTENSION_NAME, host);

      diagCollector.addDiag(
          Diag.error(endpointLocation, errorMessage, ExtensionNames.ENDPOINTS_EXTENSION_NAME));
      return;
    }
  }
}
