/*
 * Copyright (C) 2017 Google, Inc.
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

package com.google.api.tools.framework.importers.swagger.aspects.backend;

import com.google.api.Backend;
import com.google.api.BackendRule;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionUtils;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.common.base.Strings;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import java.util.Map;

/**
 * Builder for backend rules.
 */
public class BackendBuilder implements AspectBuilder {

  private final String namespacePrefix;
  private final DiagCollector diagCollector;

  public BackendBuilder(String namespace, DiagCollector diagCollector) {
    this.namespacePrefix = namespace.endsWith(".") ? namespace : namespace + ".";
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {
    final Backend.Builder backendBuilder = serviceBuilder.getBackendBuilder();

    for (final Map.Entry<String, Path> entry : swagger.getPaths().entrySet()) {
      final Path path = entry.getValue();
      for (final Operation operation : path.getOperations()) {
        if (Strings.isNullOrEmpty(operation.getOperationId())) {
          // Silently skip if the operation is invalid
          continue;
        }
        final String selector =
            namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId());

        final String backendUrl =
            VendorExtensionUtils.getExtensionValueOrNull(
                operation.getVendorExtensions(),
                String.class,
                this.diagCollector,
                ExtensionNames.BACKEND_URL_EXTENSION);
        final Integer backendDeadline =
            VendorExtensionUtils.getExtensionValueOrNull(
                operation.getVendorExtensions(),
                Integer.class,
                this.diagCollector,
                ExtensionNames.BACKEND_DEADLINE_EXTENSION);
        if (backendUrl != null) {
          final BackendRule.Builder ruleBuilder = BackendRule.newBuilder()
              .setSelector(selector)
              .setAddress(backendUrl);
          if (backendDeadline != null) {
            ruleBuilder.setDeadline(backendDeadline);
          }
          backendBuilder.addRules(ruleBuilder);
        }
      }
    }

    if (backendBuilder.getRulesList().isEmpty()) {
      serviceBuilder.clearBackend();
    }
  }

}
