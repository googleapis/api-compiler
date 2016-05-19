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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.Method;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.RefParameter;

import java.util.ArrayList;
import java.util.List;
/**
 * Class to create {@link Method} from swagger operations.
 */
class MethodBuilder {
  private final TypeBuilder typeBuilder;
  private final DiagCollector diagCollector;
  private final List<Method> methods = Lists.newArrayList();

  MethodBuilder(DiagCollector diagCollector, TypeBuilder typeBuilder) {
    this.typeBuilder = typeBuilder;
    this.diagCollector = diagCollector;
  }

  /**
   * Returns the generated methods.
   */
  Iterable<Method> getMethods() {
    return methods;
  }

  /**
   * Adds a {@link Method} from {@link Operation}.
   */
  void addMethodFromOperation(
      Operation operation, Path parentPath, String operationType, String path) {
    TypeInfo responseTypeInfo = getResponseTypeInfo(operation, operationType, path);
    TypeInfo requestType = getRequestTypeInfo(
        operation, parentPath, SwaggerToService.createOperationLocation(operationType, path));
    com.google.protobuf.Method.Builder coreMethodBuilder =
        com.google.protobuf.Method.newBuilder()
            .setName(NameConverter.operationIdToMethodName(operation.getOperationId()))
            .setRequestTypeUrl(requestType.typeUrl())
            .setResponseTypeUrl(responseTypeInfo.typeUrl());
    methods.add(coreMethodBuilder.build());
  }

  /**
   * Returns {@link TypeInfo} corresponding to the request object.
   */
  private TypeInfo getRequestTypeInfo(Operation operation, Path parentPath, Location location) {
    List<Parameter> allParameters =
        getAllResolvedParameters(operation, parentPath, diagCollector, location);
    if (allParameters.isEmpty()) {
      return WellKnownTypeUtils.getTypeInfo("empty");
    }
    TypeInfo requestType = typeBuilder.createTypeFromParameter(
        NameConverter.operationIdToRequestMessageName(operation.getOperationId()), allParameters);
    return requestType;
  }

  /**
   * Returns all parameters for the operation. Note: According to the spec, parameters defined
   * inside the operations overrides the parameters defined in the path scope which has the same
   * name and location values (example name : 'shelveId' and location : 'query').
   */
  public static ImmutableList<Parameter> getAllResolvedParameters(
      Operation operation, Path parentPath, final DiagCollector diagCollector, Location location) {
    List<Parameter> allResolvedParameters = new ArrayList<>();
    // First populate all the parameters defined in the operation.
    if (operation.getParameters() != null) {
      ImmutableList<Parameter> resolvedParameters = getResolvedParameters(
          diagCollector, ImmutableList.copyOf(operation.getParameters()), location);
      allResolvedParameters.addAll(resolvedParameters);
    }
    FluentIterable<Parameter> fluentAllParameters = FluentIterable.from(allResolvedParameters);

    // Now populate shared parameters that were not overridden inside the operation.
    if (parentPath.getParameters() != null) {
      ImmutableList<Parameter> resolvedSharedParameters = getResolvedParameters(
          diagCollector, ImmutableList.copyOf(parentPath.getParameters()), location);
      for (final Parameter sharedParam : resolvedSharedParameters) {
        boolean overriddenInOperation = fluentAllParameters.anyMatch(new Predicate<Parameter>() {
          @Override
          public boolean apply(Parameter parameter) {
            return parameter.getName().equals(sharedParam.getName())
                && parameter.getIn().equals(sharedParam.getIn());
          }
        });
        if (!overriddenInOperation) {
          allResolvedParameters.add(sharedParam);
        }
      }
    }
    return ImmutableList.copyOf(allResolvedParameters);
  }

  /**
   * Ensures the parameters are all resolved and return just the list of parameters that are fully
   * resolved by the swagger core parser.
   */
  private static ImmutableList<Parameter> getResolvedParameters(final DiagCollector diagCollector,
      ImmutableList<Parameter> parameters, final Location location) {
    ImmutableList<Parameter> resolvedParameters =
        FluentIterable.from(parameters)
            .filter(new Predicate<Parameter>() {
              @Override
              public boolean apply(Parameter parameter) {
                if (parameter instanceof RefParameter) {
                  /*
                   * This is an invalid state. Reference parameters should automatically get
                   * resolved into parameter objects by the swagger core parser, because only
                   * references that are allowed are to parameters that are defined at the Swagger
                   * Object's parameters which are in the same file. If we reach here it would mean
                   * the reference cannot be resolved and nothing this converter can do.
                   */
                  diagCollector.addDiag(Diag.warning(location, "Reference %s cannot be resolved",
                      ((RefParameter) parameter).get$ref()));
                  return false;
                } else {
                  return true;
                }
              }
            })
            .toList();
    return resolvedParameters;
  }

  /**
   * Returns {@link TypeInfo} corresponding to the response object.
   *
   * <p>In case we cannot resolve the schema we fallback to the following well known types:
   * <li>'Value' type : If there is more than one success response codes or the success code schema
   * represents a non message type.
   * <li>'List' type : If the response schema represents an array.
   * <li>'Empty' type : If there are no responses or there are no response for success code.
   */
  TypeInfo getResponseTypeInfo(Operation operation, String operationType, String path) {
    if (operation.getResponses() == null) {
      return WellKnownTypeUtils.getTypeInfo("empty");
    }

    int successCodeCount = 0;
    Response successResponse = null;
    for (String responseCode : operation.getResponses().keySet()) {
      Response response = operation.getResponses().get(responseCode);
      if (isSuccessCode(responseCode)) {
        successCodeCount++;
        if (response.getSchema() != null) {
          successResponse = response;
        }
      } else {
        // TODO (guptasu): Handle other cases like 4xx errors and non-RefProperty Schemas.
      }
    }

    if (successCodeCount == 1 && successResponse != null && successResponse.getSchema() != null) {
      TypeInfo responseTypeInfo = typeBuilder.ensureNamed(
          typeBuilder.getTypeInfo(successResponse.getSchema()),
          NameConverter.operationIdToResponseMessageName(operation.getOperationId()));
      if (responseTypeInfo.cardinality() == Cardinality.CARDINALITY_REPEATED) {
        // TODO (guptasu): Seems like we cannot create custom ListValue, something like
        // ListString. Therefore falling back to ListValue. Can we do better than this ?
        return WellKnownTypeUtils.getTypeInfo("list");
      } else if (responseTypeInfo.kind() != Kind.TYPE_MESSAGE) {
        return WellKnownTypeUtils.getTypeInfo("value");
      } else {
        return responseTypeInfo;
      }
    } else if (successCodeCount == 0) {
      return WellKnownTypeUtils.getTypeInfo("empty");
    } else {
      // TODO (guptasu): Due to multiple schemas for successful response code  return type for the
      // operation is generalized as Value type.
      return WellKnownTypeUtils.getTypeInfo("value");
    }
  }

  /**
   * Returns true if the responseCode represents a success code; false otherwise.
   */
  private boolean isSuccessCode(String responseCode) {
    return responseCode.equalsIgnoreCase("default") || responseCode.startsWith("2");
  }
}
