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

import com.google.api.AuthenticationRule;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.SwaggerLocations;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.type.TypeBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.type.TypeInfo;
import com.google.api.tools.framework.importers.swagger.aspects.type.WellKnownTypeUtils.WellKnownType;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.importers.swagger.aspects.utils.SwaggerUtils;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionUtils;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.protobuf.Api;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.Method;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.RefParameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Class to create {@link Api} from swagger operations. */
public class ApiFromSwagger {
  private final TypeBuilder typeBuilder;
  private final DiagCollector diagCollector;
  private final Api.Builder coreApiBuilder;
  private final HttpRuleGenerator httpRuleGenerator;
  private final AuthRuleGenerator authRuleGenerator;
  private final AuthBuilder authBuilder;

  public ApiFromSwagger(
      DiagCollector diagCollector,
      TypeBuilder typeBuilder,
      String filename,
      String apiName,
      HttpRuleGenerator httpRuleGenerator,
      AuthRuleGenerator authRuleGenerator,
      AuthBuilder authBuilder) {
    this.typeBuilder = typeBuilder;
    this.diagCollector = diagCollector;
    coreApiBuilder = Api.newBuilder().setName(apiName);
    coreApiBuilder.getSourceContextBuilder().setFileName(filename);
    this.httpRuleGenerator = httpRuleGenerator;
    this.authRuleGenerator = authRuleGenerator;
    this.authBuilder = authBuilder;
  }

  public void addFromSwagger(
      Service.Builder serviceBuilder,
      Swagger swagger) {
    Map<String, String> duplicateOperationIdLookup = Maps.newHashMap();
    TreeSet<String> urlPaths = Sets.newTreeSet(swagger.getPaths().keySet());
    for (String urlPath : urlPaths) {
      Path pathObj = swagger.getPath(urlPath);
      createServiceMethodsFromPath(
          serviceBuilder,
          urlPath,
          pathObj,
          duplicateOperationIdLookup);
    }

    if (isAllowAllMethodsConfigured(swagger, diagCollector)) {
      Path userDefinedWildCardPathObject = new Path();
      if (urlPaths.contains(SwaggerUtils.WILDCARD_URL_PATH)) {
        userDefinedWildCardPathObject = swagger.getPath(SwaggerUtils.WILDCARD_URL_PATH);
      }
      createServiceMethodsFromPath(
          serviceBuilder,
          SwaggerUtils.WILDCARD_URL_PATH,
          getNewWildCardPathObject(userDefinedWildCardPathObject),
          duplicateOperationIdLookup);
    }
    coreApiBuilder.setVersion(swagger.getInfo().getVersion());
    serviceBuilder.addApis(coreApiBuilder);
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
      ImmutableList<Parameter> resolvedParameters =
          getResolvedParameters(
              diagCollector, ImmutableList.copyOf(operation.getParameters()), location);
      allResolvedParameters.addAll(resolvedParameters);
    }
    FluentIterable<Parameter> fluentAllParameters = FluentIterable.from(allResolvedParameters);

    // Now populate shared parameters that were not overridden inside the operation.
    if (parentPath.getParameters() != null) {
      ImmutableList<Parameter> resolvedSharedParameters =
          getResolvedParameters(
              diagCollector, ImmutableList.copyOf(parentPath.getParameters()), location);
      for (final Parameter sharedParam : resolvedSharedParameters) {
        boolean overriddenInOperation =
            fluentAllParameters.anyMatch(
                new Predicate<Parameter>() {
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

  private Path getNewWildCardPathObject(Path userDefinedWildCardPathObject) {
    Preconditions.checkNotNull(
        userDefinedWildCardPathObject, "userDefinedWildCardPathObject cannot be null");

    Path path = new Path();
    if (userDefinedWildCardPathObject.getGet() == null) {
      path.set("get", constructReservedOperation("Get"));
    }
    if (userDefinedWildCardPathObject.getDelete() == null) {
      path.set("delete", constructReservedOperation("Delete"));
    }
    if (userDefinedWildCardPathObject.getPatch() == null) {
      path.set("patch", constructReservedOperation("Patch"));
    }
    if (userDefinedWildCardPathObject.getPost() == null) {
      path.set("post", constructReservedOperation("Post"));
    }
    if (userDefinedWildCardPathObject.getPut() == null) {
      path.set("put", constructReservedOperation("Put"));
    }
    return path;
  }

  private void createServiceMethodsFromPath(
      Service.Builder serviceBuilder,
      String urlPath,
      Path pathObj,
      Map<String, String> dupliacteOperationIdLookup) {
    Map<String, Operation> operations = getOperationsForPath(pathObj);
    for (String operationType : operations.keySet()) {
      Operation operation = operations.get(operationType);
      if (operation == null) {
        continue;
      }
      if (!validateOperationId(
          operation, urlPath, operationType, diagCollector, dupliacteOperationIdLookup)) {
        continue;
      }

      addMethodFromOperation(serviceBuilder, operation, pathObj, operationType, urlPath);
      serviceBuilder
          .getHttpBuilder()
          .addRules(httpRuleGenerator.createHttpRule(operation, pathObj, operationType, urlPath));
      AuthenticationRule authRule =
          authRuleGenerator.createAuthRule(operation, operationType, urlPath);
      if (authRule != null) {
        serviceBuilder.getAuthenticationBuilder().addRules(authRule);
      }
      serviceBuilder
          .getUsageBuilder()
          .addRules(
              authBuilder.createUsageRule(
                  operation, operationType, urlPath));
    }
  }

  private Operation constructReservedOperation(String suffix) {
    Operation getOperation = new Operation();
    getOperation.setOperationId(
        String.format("Google_Autogenerated_Unrecognized_%s_Method_Call", suffix));

    // Clear all the control plane settings that do not apply to the wild card operations.
    getOperation.setSecurity(new ArrayList<Map<String, List<String>>>());

    return getOperation;
  }

  /** Returns true if x-google-allow is set to all; false otherwise. */
  private static boolean isAllowAllMethodsConfigured(Swagger swagger, DiagCollector diagCollector) {
    String googleAllowExtensionNameUsed =
        VendorExtensionUtils.usedExtension(
            diagCollector, swagger.getVendorExtensions(), ExtensionNames.X_GOOGLE_ALLOW);
    if (!Strings.isNullOrEmpty(googleAllowExtensionNameUsed)) {
      String allowMethodsExtensionValue =
          VendorExtensionUtils.getExtensionValue(
              swagger.getVendorExtensions(),
              String.class,
              diagCollector,
              googleAllowExtensionNameUsed);
      if (!Strings.isNullOrEmpty(allowMethodsExtensionValue)) {
        if (allowMethodsExtensionValue.equalsIgnoreCase("all")) {
          return true;
        } else if (allowMethodsExtensionValue.equalsIgnoreCase("configured")) {
          return false;
        } else {
          diagCollector.addDiag(
              Diag.error(
                  new SimpleLocation(ExtensionNames.X_GOOGLE_ALLOW),
                  "Only allowed values for %s are %s",
                  ExtensionNames.X_GOOGLE_ALLOW,
                  "all|configured"));
          return false;
        }
      }
    }
    return false;
  }

  /** Validate if the operation id is correct and is unique. */
  private boolean validateOperationId(
      Operation operation,
      String urlPath,
      String operationType,
      DiagCollector diagCollector,
      Map<String, String> duplicateOperationIdLookup) {
    if (Strings.isNullOrEmpty(operation.getOperationId())) {
      diagCollector.addDiag(
          Diag.error(
              SwaggerLocations.createOperationLocation(operationType, urlPath),
              "Operation does not have the required 'operationId' field. Please specify unique"
                  + " value for 'operationId' field for all operations."));
      return false;
    }
    String operationId = operation.getOperationId();
    String sanitizedOperationId = NameConverter.operationIdToMethodName(operationId);
    if (duplicateOperationIdLookup.containsKey(sanitizedOperationId)) {
      String dupeOperationId = duplicateOperationIdLookup.get(sanitizedOperationId);
      Location errorLocation = SwaggerLocations.createOperationLocation(operationType, urlPath);
      String errorMessage = String.format("operationId '%s' has duplicate entry", operationId);
      if (!operationId.equals(dupeOperationId)) {
        errorLocation = SimpleLocation.TOPLEVEL;
        errorMessage +=
            String.format(
                ". Duplicate operationId found is '%s'. The two operationIds result into same "
                    + "underlying method name '%s'. Please use unique values for operationId",
                dupeOperationId, sanitizedOperationId);
      }
      diagCollector.addDiag(Diag.error(errorLocation, errorMessage));
      return false;
    }

    duplicateOperationIdLookup.put(sanitizedOperationId, operationId);
    return true;
  }

  /** Creates a map between http verb and operation. */
  private Map<String, Operation> getOperationsForPath(Path pathObj) {
    Map<String, Operation> hmap = Maps.newLinkedHashMap();
    hmap.put("get", pathObj.getGet());
    hmap.put("delete", pathObj.getDelete());
    hmap.put("patch", pathObj.getPatch());
    hmap.put("post", pathObj.getPost());
    hmap.put("put", pathObj.getPut());
    hmap.put("options", pathObj.getOptions());
    return hmap;
  }
  /** Adds a {@link Method} from {@link Operation}. */
  private void addMethodFromOperation(
      Service.Builder serviceBuilder,
      Operation operation,
      Path parentPath,
      String operationType,
      String path) {
    TypeInfo responseTypeInfo = getResponseTypeInfo(serviceBuilder, operation);
    TypeInfo requestType =
        getRequestTypeInfo(
            serviceBuilder,
            operation,
            parentPath,
            SwaggerLocations.createOperationLocation(operationType, path));
    com.google.protobuf.Method.Builder coreMethodBuilder =
        com.google.protobuf.Method.newBuilder()
            .setName(NameConverter.operationIdToMethodName(operation.getOperationId()))
            .setRequestTypeUrl(requestType.typeUrl())
            .setResponseTypeUrl(responseTypeInfo.typeUrl());
    coreApiBuilder.addMethods(coreMethodBuilder);
  }

  /** Returns {@link TypeInfo} corresponding to the request object. */
  private TypeInfo getRequestTypeInfo(
      Service.Builder serviceBuilder, Operation operation, Path parentPath, Location location) {
    List<Parameter> allParameters =
        getAllResolvedParameters(operation, parentPath, diagCollector, location);
    if (allParameters.isEmpty()) {
      return WellKnownType.EMPTY.toTypeInfo();
    }
    TypeInfo requestType =
        typeBuilder.createTypeFromParameter(
            serviceBuilder,
            NameConverter.operationIdToRequestMessageName(operation.getOperationId()),
            allParameters);
    return requestType;
  }

  /**
   * Ensures the parameters are all resolved and return just the list of parameters that are fully
   * resolved by the swagger core parser.
   */
  private static ImmutableList<Parameter> getResolvedParameters(
      final DiagCollector diagCollector,
      ImmutableList<Parameter> parameters,
      final Location location) {
    ImmutableList<Parameter> resolvedParameters =
        FluentIterable.from(parameters)
            .filter(
                new Predicate<Parameter>() {
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
                      diagCollector.addDiag(
                          Diag.warning(
                              location,
                              "Reference %s cannot be resolved",
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
   *     represents a non message type.
   * <li>'List' type : If the response schema represents an array.
   * <li>'Empty' type : If there are no responses or there are no response for success code.
   */
  private TypeInfo getResponseTypeInfo(Service.Builder serviceBuilder, Operation operation) {
    if (operation.getResponses() == null) {
      return WellKnownType.EMPTY.toTypeInfo();
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
      TypeInfo responseTypeInfo =
          typeBuilder.ensureNamed(serviceBuilder,
              typeBuilder.getTypeInfo(serviceBuilder, successResponse.getSchema()),
              NameConverter.operationIdToResponseMessageName(operation.getOperationId()));
      if (responseTypeInfo.cardinality() == Cardinality.CARDINALITY_REPEATED) {
        // TODO (guptasu): Seems like we cannot create custom ListValue, something like
        // ListString. Therefore falling back to ListValue. Can we do better than this ?
        return WellKnownType.LIST.toTypeInfo();
      } else if (responseTypeInfo.kind() != Kind.TYPE_MESSAGE) {
        return WellKnownType.VALUE.toTypeInfo();
      } else {
        return responseTypeInfo;
      }
    } else if (successCodeCount == 0) {
      return WellKnownType.EMPTY.toTypeInfo();
    } else {
      // TODO (guptasu): Due to multiple schemas for successful response code  return type for the
      // operation is generalized as Value type.
      return WellKnownType.VALUE.toTypeInfo();
    }
  }

  /** Returns true if the responseCode represents a success code; false otherwise. */
  private boolean isSuccessCode(String responseCode) {
    return responseCode.equalsIgnoreCase("default") || responseCode.startsWith("2");
  }
}
