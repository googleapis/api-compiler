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

package com.google.api.tools.framework.importers.swagger.aspects;

import com.google.api.HttpRule;
import com.google.api.tools.framework.importers.swagger.SwaggerLocations;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.parameters.Parameter;

/** Class to create {@link HttpRule} from swagger operations. */
public class HttpRuleGenerator{
  private final DiagCollector diagCollector;
  private final String namespacePrefix;
  private final String basePath;

  public HttpRuleGenerator(
      String namespace,
      String basePath,
      DiagCollector diagCollector) {
    this.namespacePrefix =
        (namespace.isEmpty() || namespace.endsWith(".")) ? namespace : namespace + ".";
    this.diagCollector = diagCollector;
    this.basePath = (Strings.isNullOrEmpty(basePath) || basePath.equals("/")) ? "" : basePath;
  }

  /** Creates {@link HttpRule} from a swagger operation. */
  HttpRule createHttpRule(Operation operation, Path parentPath, String operationType, String path) {
    HttpRule.Builder httpRuleBuilder = HttpRule.newBuilder();

    ImmutableList<Parameter> allParameters =
        ApiFromSwagger.getAllResolvedParameters(
            operation,
            parentPath,
            diagCollector,
            SwaggerLocations.createOperationLocation(operationType, path));
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
          /* TODO(user): Support other In types.*/
      }
    }

    String httpRulePath = basePath + path;
    switch (operationType) {
      case "put":
        httpRuleBuilder.setPut(httpRulePath);
        break;
      case "get":
        httpRuleBuilder.setGet(httpRulePath);
        break;
      case "delete":
        httpRuleBuilder.setDelete(httpRulePath);
        break;
      case "patch":
        httpRuleBuilder.setPatch(httpRulePath);
        break;
      case "post":
        httpRuleBuilder.setPost(httpRulePath);
        break;
      default:
        diagCollector.addDiag(
            Diag.warning(
                SwaggerLocations.createOperationLocation(operationType, httpRulePath),
                "Operation is invalid. Default to 'post' operation"));
        httpRuleBuilder.setPost(httpRulePath);
    }
    return httpRuleBuilder.build();
  }
}
