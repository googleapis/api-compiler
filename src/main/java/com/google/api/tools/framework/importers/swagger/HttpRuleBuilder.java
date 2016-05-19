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

import com.google.api.HttpRule;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.parameters.Parameter;

import java.util.List;

/**
 * Class to create {@link HttpRule} from swagger operations.
 */
class HttpRuleBuilder {
  private final DiagCollector diagCollector;
  private final List<HttpRule> httpRules = Lists.newArrayList();
  private final String namespacePrefix;
  private final String basePath;

  HttpRuleBuilder(String namespace, String basePath, DiagCollector diagCollector) {
    this.namespacePrefix =
        (namespace.isEmpty() || namespace.endsWith(".")) ? namespace : namespace + ".";
    this.diagCollector = diagCollector;
    this.basePath = (Strings.isNullOrEmpty(basePath) || basePath.equals("/")) ? "" : basePath;
  }

  /**
   * Returns the generated {@link HttpRule}s.
   */
  Iterable<HttpRule> getHttpRules() {
    return httpRules;
  }

  /**
   * Creates {@link HttpRule} from a swagger operation.
   */
  void addHttpRule(Operation operation, Path parentPath, String operationType, String path) {
    HttpRule.Builder httpRuleBuilder = HttpRule.newBuilder();

    ImmutableList<Parameter> allParameters = MethodBuilder.getAllResolvedParameters(operation,
        parentPath, diagCollector, SwaggerToService.createOperationLocation(operationType, path));
    httpRuleBuilder.setSelector(
        namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()));
    for (Parameter parameter : allParameters) {
      String paramNameWithUnderScore = NameConverter.getFieldName(parameter.getName());
      switch (parameter.getIn()) {
        case "body":
          httpRuleBuilder.setBody(paramNameWithUnderScore);
          break;
        case "query":
        case "path":
          path = path.replace("{" + parameter.getName() + "}", "{" + paramNameWithUnderScore + "}");
          break;
        default:
          /* TODO(user): Make this an error once we want to start supporting json to proto
           * transformation for APIs imported from Swagger spec.*/
          diagCollector.addDiag(Diag.warning(
              SwaggerToService.createParameterLocation(parameter, operationType, path),
              "Parameter is ignored because its location (in=%s) is not supported. Supported values"
              + " for 'in' are 'body', 'query', and 'path'.",
              parameter.getIn()));
      }
    }

    path = basePath + path;
    switch (operationType) {
      case "put":
        httpRuleBuilder.setPut(path);
        break;
      case "get":
        httpRuleBuilder.setGet(path);
        break;
      case "delete":
        httpRuleBuilder.setDelete(path);
        break;
      case "patch":
        httpRuleBuilder.setPatch(path);
        break;
      case "post":
        httpRuleBuilder.setPost(path);
        break;
      default:
        diagCollector.addDiag(Diag.warning(
            SwaggerToService.createOperationLocation(operationType, path),
            "Operation is invalid. Default to 'post' operation"));
        httpRuleBuilder.setPost(path);
    }
    httpRules.add(httpRuleBuilder.build());
  }
}
