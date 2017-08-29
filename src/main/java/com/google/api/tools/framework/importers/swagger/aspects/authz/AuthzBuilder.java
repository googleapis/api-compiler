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

package com.google.api.tools.framework.importers.swagger.aspects.authz;

import com.google.api.AuthorizationConfig;
import com.google.api.Experimental;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionProtoConverter;
import com.google.api.tools.framework.model.DiagCollector;
import io.swagger.models.Swagger;

/**
 * Class to parse "x-google-experimental-authorization" field and build
 * service.experimental.authorization.
 */
public final class AuthzBuilder implements AspectBuilder {
  private final DiagCollector diagCollector;

  public AuthzBuilder(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {
    VendorExtensionProtoConverter extensionConverter =
        new VendorExtensionProtoConverter(swagger.getVendorExtensions(), diagCollector);

    if (extensionConverter.hasExtension(ExtensionNames.AUTHORIZATION_EXTENSION_NAME)) {
      Experimental.Builder eBuilder = serviceBuilder.getExperimentalBuilder();

      AuthorizationConfig config =
          extensionConverter.convertExtensionToProto(
              AuthorizationConfig.getDefaultInstance(),
              ExtensionNames.AUTHORIZATION_EXTENSION_NAME);
      serviceBuilder.setExperimental(eBuilder.setAuthorization(config));
    }
  }
}
