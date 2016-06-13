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

import com.google.api.Http;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.control.model.ControlConfigUtil;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.yaml.YamlReader;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.protobuf.Api;
import com.google.protobuf.Method;
import com.google.protobuf.Type;
import com.google.protobuf.UInt32Value;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.report.ProcessingMessage;

import io.swagger.models.Info;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.Parameter;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeSet;

/**
 * Class to create a {@link Service} instance from a {@link Swagger} object.
 */
public class SwaggerToService implements DiagCollector {

  private static final String SCHEMA_RESOURCE_PATH = "swagger/schema2_0/schema.json";
  static final String WILDCARD_URL_PATH = "/**";
  private static final int TOOLS_CONFIG_VERSION_3 = 3;

  private final Swagger swagger;
  private final Service.Builder serviceBuilder;
  private final String serviceName;
  private final String methodNamespace;
  private final TypeBuilder typeBuilder;
  private final MethodBuilder methodBuilder;
  private final HttpRuleBuilder httpRuleBuilder;
  private final AuthBuilder authBuilder;
  private final String swaggerFileName;
  private final Location swaggerFileLocation;
  private final List<Diag> diags = Lists.newArrayList();
  private final ImmutableMap<String, String> additionalConfigs;
  private final Map<String, String> duplicateOperationIdLookup;
  private int errorCount = 0;

  /**
   * Initializes Swagger to Service config converter.
   *
   * @param swaggerFilePathToContentMap Swagger files.
   * @param serviceName A service name to use for generated service config. If empty, service name
   *                    is extracted from the `host` field of the Swagger spec.
   * @param typeNamespace A namespace prefix used for all types in service config.
   * @param methodNamespace A namespace prefix used for all methods in service config.
   * @param additionalConfigs
   */
  public SwaggerToService(ImmutableMap<String, String> swaggerFilePathToContentMap,
      String serviceName, String typeNamespace, String methodNamespace,
      ImmutableMap<String, String> additionalConfigs) throws SwaggerConversionException {
    Preconditions.checkState(
        swaggerFilePathToContentMap != null && !swaggerFilePathToContentMap.isEmpty(),
        "swaggerFilePathToContentMap cannot be null or empty");

    ImmutableList<String> savedFilePaths = saveFilesOnDisk(swaggerFilePathToContentMap);
    String swaggerFilePath = validateInputFiles(savedFilePaths);

    Swagger swaggerObject = new SwaggerParser().read(swaggerFilePath);
    if (swaggerObject == null) {
      throw new SwaggerConversionException("Swagger spec is ill formed and cannot be parsed");
    }

    this.swaggerFileName = (new File(swaggerFilePath)).getName();
    swaggerFileLocation = new SimpleLocation(swaggerFileName);
    this.swagger = swaggerObject;
    this.serviceName = serviceName == null ? "" : serviceName.trim();
    typeNamespace = typeNamespace == null ? "" : typeNamespace.trim();
    this.methodNamespace = methodNamespace == null ? "" : methodNamespace.trim();
    if (typeNamespace.endsWith(".")) {
      typeNamespace = typeNamespace.substring(0, typeNamespace.length() - 1);
    }
    this.additionalConfigs = additionalConfigs;
    this.duplicateOperationIdLookup = Maps.newLinkedHashMap();
    typeBuilder = new TypeBuilder(swagger, typeNamespace);
    methodBuilder = new MethodBuilder(this, typeBuilder);
    httpRuleBuilder = new HttpRuleBuilder(methodNamespace, this.swagger.getBasePath(), this);
    authBuilder = new AuthBuilder(methodNamespace, this);
    serviceBuilder = Service.newBuilder();
  }

  /**
   * Saves the file contents on the disk and returns the saved file paths.
   */
  private ImmutableList<String> saveFilesOnDisk(
      ImmutableMap<String, String> swaggerFilePathToContentMap) {
    List<String> savedfilePaths = new ArrayList<>();
    File tempDir = Files.createTempDir();
    String tmpDirLocation = tempDir.getAbsolutePath();
    for (Entry<String, String> entry : swaggerFilePathToContentMap.entrySet()) {
      String filePath = entry.getKey();
      String fileContent = entry.getValue();

      Preconditions.checkState(
          !Strings.isNullOrEmpty(fileContent), "swagger spec file contents empty");
      Preconditions.checkState(
          !Strings.isNullOrEmpty(filePath), "swagger spec file path not provided");

      String filePathToSave = File.separator + tmpDirLocation + File.separator
          + "swagger_spec_files" + File.separator + filePath;
      try {

        String savedFilePath = saveFileOnDisk(filePathToSave, fileContent);
        savedfilePaths.add(savedFilePath);
      } catch (IOException e) {
        throw new IllegalStateException(
            String.format(
                "Unable to save the swagger spec contents on the disk at %s", filePathToSave),
            e);
      }
    }
    return ImmutableList.copyOf(savedfilePaths);
  }

  /**
   * Saves the individual file on disk with the fileContent.
   */
  private String saveFileOnDisk(String filePathToSave, String fileContent) throws IOException {
    File file = new File(filePathToSave);
    Files.createParentDirs(file);
    Files.write(fileContent, file, Charset.defaultCharset());
    return filePathToSave;
  }

  /**
   * Ensures that all files are valid json/yaml and does schema validation on swagger spec.
   * Returns the path to the valid swagger file.
   * @throws SwaggerConversionException
   */
  private static String validateInputFiles(ImmutableList<String> savedFilePaths)
      throws SwaggerConversionException {
    JsonNode data = null;
    String validSwaggerFilePath = null;
    for (String filePath : savedFilePaths) {
      try {
        File inputFile = new File(filePath);
        String inputFileContent = FileUtils.readFileToString(inputFile, "UTF-8");
        ObjectMapper objMapper = null;
        String fileExtension = Files.getFileExtension(filePath);
        if (fileExtension.equalsIgnoreCase("json")) {
          objMapper = Json.mapper();
        } else if (fileExtension.equalsIgnoreCase("yaml")) {
          objMapper = Yaml.mapper();
        } else {
          throw new IllegalArgumentException(String.format(
              "Swagger spec files '%s' have invalid extension '%s'. Only files with 'json' and "
              + "'yaml' file extensions are allowed.",
              inputFile.getName(), fileExtension));
        }
        data = objMapper.readTree(inputFileContent);
      } catch (Exception e) {
        throw new SwaggerConversionException("Unable to parse the content. " + e.getMessage(), e);
      }

      if (data.get("swagger") != null && data.get("swagger").toString().contains("2.0")) {
        if (validSwaggerFilePath != null) {
          throw new SwaggerConversionException("Multiple swagger files were passed as input. "
              + "Only one top-level swagger file is allowed which can reference schemas from other "
              + "files passed as input.");
        }
        validateSwaggerSpec(data);
        validSwaggerFilePath = filePath;
      }
    }
    if (Strings.isNullOrEmpty(validSwaggerFilePath)) {
      throw new SwaggerConversionException(
          "Cannot find a valid swagger 2.0 spec in the input files");
    } else {
      return validSwaggerFilePath;
    }
  }

  /**
   * Validates the input Swagger JsonNode against Swagger Specification schema.
   * @throws SwaggerConversionException
   */
  private static void validateSwaggerSpec(JsonNode swaggerJsonNode)
      throws SwaggerConversionException {
    ProcessingReport report = null;
    try {
      URL url = Resources.getResource(SCHEMA_RESOURCE_PATH);
      String swaggerSchema = Resources.toString(url, StandardCharsets.UTF_8);
      JsonNode schemaNode = Yaml.mapper().readTree(swaggerSchema);
      JsonSchema schema = JsonSchemaFactory.byDefault().getJsonSchema(schemaNode);
      report = schema.validate(swaggerJsonNode);
    } catch (Exception e) {
      throw new SwaggerConversionException("Unable to parse the content. " + e.getMessage(), e);
    }
    if (!report.isSuccess()) {
      String message = "";
      Iterator itr = report.iterator();
      if (itr.hasNext()) {
        message += ((ProcessingMessage) itr.next()).toString();
      }
      while(itr.hasNext())
      {
        message += "," + ((ProcessingMessage) itr.next()).toString();
      }
      throw new SwaggerConversionException(
          String.format("Invalid Swagger spec. Please fix the schema errors:\n%s", message));
    }
  }

  /**
   * Creates {@link com.google.api.Service} from Swagger Object, and returns it.
   */
  public Service createServiceConfig() throws SwaggerConversionException {
    createServiceInfoFromSwagger();
    createAuthDefinitionsFromSwagger();
    createServiceTypesFromSwagger();
    createServiceMethodsFromSwagger();

    serviceBuilder.addAllTypes(typeBuilder.getTypes());
    // TODO (guptasu): Do we really need to add these types?
    serviceBuilder.addAllTypes(TypesBuilderFromDescriptor.createAdditionalServiceTypes());
    serviceBuilder.addAllEnums(TypesBuilderFromDescriptor.createAdditionalServiceEnums());

    Api.Builder coreApiBuilder = Api.newBuilder().setName(methodNamespace);
    coreApiBuilder.getSourceContextBuilder().setFileName(methodNamespace);
    coreApiBuilder.addAllMethods(methodBuilder.getMethods());
    serviceBuilder.addApis(coreApiBuilder.build());

    Http.Builder httpBuilder = Http.newBuilder();
    httpBuilder.addAllRules(httpRuleBuilder.getHttpRules());
    serviceBuilder.setHttp(httpBuilder.build());

    serviceBuilder.setAuthentication(authBuilder.getAuthentication());
    serviceBuilder.setUsage(authBuilder.getUsage());
    applyThirdPartyApiSettings();

    return normalizeService(serviceBuilder.build());
  }

  /**
   * Sets special configuration needed for 3rd party Endpoints APIs.
   */
  private void applyThirdPartyApiSettings() {
    serviceBuilder.getControlBuilder().setEnvironment(ControlConfigUtil.ENDPOINTS_SERVICE_CONTROL);

    // Set the config version to 3.
    serviceBuilder.setConfigVersion(
        UInt32Value.newBuilder().setValue(TOOLS_CONFIG_VERSION_3).build());
  }

  /**
   * Merges configurations from all the additionalConfigs and returns a normalized  {@link Service}
   * instance.
   */
  private Service normalizeService(Service service) {
    Model model = createModel(service, additionalConfigs);
    model.enableExperiment("empty-descriptor-defaults");
    model.establishStage(Normalized.KEY);
    if (model.getDiagCollector().hasErrors()) {
      diags.addAll(model.getDiagCollector().getDiags());
      errorCount += model.getDiagCollector().getErrorCount();
      return null;
    }
    return model.getNormalizedConfig();
  }

  /**
   * Returns a {@link Model} generated from the {@link Service} and the additionalConfigs.
   */
  private static Model createModel(
      Service service, ImmutableMap<String, String> additionalConfigs) {
    Model model = Model.create(service);
    if (additionalConfigs != null) {
      List<ConfigSource> allConfigs = Lists.newArrayList();
      allConfigs.add(model.getServiceConfigSource());
      for (Map.Entry<String, String> additionalConfig : additionalConfigs.entrySet()) {
        allConfigs.add(
            YamlReader.readConfig(model.getDiagCollector(), additionalConfig.getKey(),
                additionalConfig.getValue()));
      }
      model.setConfigSources(allConfigs);
    }
    StandardSetup.registerStandardProcessors(model);
    StandardSetup.registerStandardConfigAspects(model);
    return model;
  }

  /**
   * Adds additional information to {@link Service} object.
   * @throws SwaggerConversionException
   */
  private void createServiceInfoFromSwagger() throws SwaggerConversionException {
    String serviceName = this.serviceName; // Try explicitly provided service name first.
    if (Strings.isNullOrEmpty(serviceName)) {
      serviceName = this.swagger.getHost(); // Fall back on swagger host.
      if (serviceName != null) {
        serviceName = serviceName.trim();
      }
      if (Strings.isNullOrEmpty(serviceName)) {
        throw new SwaggerConversionException(
            "Service name must be provided either explicitly or in Swagger 'host' value.");
      }
    }
    serviceBuilder.setName(serviceName);
    if (this.swagger.getInfo() != null) {
      Info swaggerInfo = this.swagger.getInfo();
      if (swaggerInfo.getTitle() != null) {
        serviceBuilder.setTitle(swaggerInfo.getTitle());
      }
      if (swaggerInfo.getDescription() != null) {
        serviceBuilder.getDocumentationBuilder().setSummary(swaggerInfo.getDescription());
      }
    }
    // Add config version to the service instance.
    serviceBuilder.setConfigVersion(
        UInt32Value.newBuilder().setValue(Model.getDefaultConfigVersion()).build());
  }

  /**
   * Adds AuthProviders from Swagger SecuritySchemaDefinitions.
   */
  private void createAuthDefinitionsFromSwagger() {
    if (swagger.getSecurityDefinitions() == null) {
      return;
    }
    TreeSet<String> swaggerSecurityDefNames =
        Sets.newTreeSet(swagger.getSecurityDefinitions().keySet());
    for (String swaggerSecurityDefName : swaggerSecurityDefNames) {
      authBuilder.addAuthProvider(
          swaggerSecurityDefName, swagger.getSecurityDefinitions().get(swaggerSecurityDefName));
    }
    authBuilder.addSecurityRequirementForEntireService(swagger.getSecurity());
    authBuilder.addSecurityRequirementExtensionForEntireService(swagger);
  }

  /**
   * Creates {@link Type} from swagger model.
   */
  private void createServiceTypesFromSwagger() {
    if (swagger.getDefinitions() == null) {
      return;
    }
    TreeSet<String> swaggerModelNames = Sets.newTreeSet(swagger.getDefinitions().keySet());
    for (String swaggerModelName : swaggerModelNames) {
      typeBuilder.addTypeFromModel(
          swaggerModelName, swagger.getDefinitions().get(swaggerModelName));
    }
  }

  /**
   * Creates {@link Method} instances from swagger {@link Operation}.
   */
  private void createServiceMethodsFromSwagger() {
    if (swagger.getPaths() == null) {
      return;
    }
    TreeSet<String> urlPaths = Sets.newTreeSet(swagger.getPaths().keySet());
    for (String urlPath : urlPaths) {
      Path pathObj = swagger.getPath(urlPath);
      createServiceMethodsFromPath(urlPath, pathObj);
    }

    if (isAllowAllMethodsConfigured()) {
      Path userDefinedWildCardPathObject = new Path();
      if (urlPaths.contains(WILDCARD_URL_PATH)) {
        userDefinedWildCardPathObject = swagger.getPath(WILDCARD_URL_PATH);
      }
      createServiceMethodsFromPath(
          WILDCARD_URL_PATH, getNewWildCardPathObject(userDefinedWildCardPathObject));
    }
  }

  private void createServiceMethodsFromPath(String urlPath, Path pathObj) {
    Map<String, Operation> operations = getOperationsForPath(pathObj);
    for (String operationType : operations.keySet()) {
      Operation operation = operations.get(operationType);
      if (operation == null) {
        continue;
      }
      if (!validateOperationId(operation, urlPath, operationType)) {
        continue;
      }
      methodBuilder.addMethodFromOperation(operation, pathObj, operationType, urlPath);
      httpRuleBuilder.addHttpRule(operation, pathObj, operationType, urlPath);
      authBuilder.addAuthRule(operation, operationType, urlPath);
    }
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

  private Operation constructReservedOperation(String suffix) {
    Operation getOperation = new Operation();
    getOperation.setOperationId(
        String.format("Google_Autogenerated_Unrecognized_%s_Method_Call", suffix));
    return getOperation;
  }

  /**
   * Returns true if x-google-allow is set to all; false otherwise.
   */
  private boolean isAllowAllMethodsConfigured() {
    if (VendorExtensionUtils.hasExtension(
        swagger.getVendorExtensions(), VendorExtensionUtils.X_GOOGLE_ALLOW, String.class, this)) {
      String allowMethodsExtensionValue =
          (String) swagger.getVendorExtensions().get(VendorExtensionUtils.X_GOOGLE_ALLOW);
      if (allowMethodsExtensionValue.equalsIgnoreCase("all")) {
        return true;
      } else if (allowMethodsExtensionValue.equalsIgnoreCase("configured")) {
        return false;
      } else {
        addDiag(
            Diag.error(
                new SimpleLocation(VendorExtensionUtils.X_GOOGLE_ALLOW),
                "Only allowed values for %s are %s",
                VendorExtensionUtils.X_GOOGLE_ALLOW,
                "all|configured"));
        return false;
      }
    }
    return false;
  }

  /**
   * Validate if the operation id is correct and is unique.
   */
  private boolean validateOperationId(Operation operation, String urlPath, String operationType) {
    if (Strings.isNullOrEmpty(operation.getOperationId())) {
      addDiag(
          Diag.error(
              createOperationLocation(operationType, urlPath),
              "Operation does not have the required 'operationId' field. Please specify unique"
                  + " value for 'operationId' field for all operations."));
      return false;
    }
    String operationId = operation.getOperationId();
    String sanitizedOperationId = NameConverter.operationIdToMethodName(operationId);
    if (duplicateOperationIdLookup.containsKey(sanitizedOperationId)) {
      String dupeOperationId = duplicateOperationIdLookup.get(sanitizedOperationId);
      Location errorLocation = createOperationLocation(operationType, urlPath);
      String errorMessage = String.format("operationId '%s' has duplicate entry", operationId);
      if (!operationId.equals(dupeOperationId)) {
        errorLocation = SimpleLocation.TOPLEVEL;
        errorMessage += String.format(
            ". Duplicate operationId found is '%s'. The two operationIds result into same "
            + "underlying method name '%s'. Please use unique values for operationId",
            dupeOperationId, sanitizedOperationId);
      }
      addDiag(Diag.error(errorLocation, errorMessage));
      return false;
    }

    duplicateOperationIdLookup.put(sanitizedOperationId, operationId);
    return true;
  }

  /**
   * Creates a map between http verb and operation.
   */
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

  static SimpleLocation createParameterLocation(
      Parameter parameter, String operationType, String path) {
    return new SimpleLocation(String.format("Parameter '%s' in operation '%s' in path '%s'",
        parameter.getName(), operationType, path));
  }

  static SimpleLocation createOperationLocation(
      String operationType, String path) {
    return new SimpleLocation(String.format("Operation '%s' in path '%s'",
        operationType, path));
  }

  /**
   * Accumulates errors and warning encountered during import.
   */
  @Override
  public void addDiag(Diag diag) {
    Location loc = SimpleLocation.UNKNOWN;
    if (diag.getLocation() == SimpleLocation.UNKNOWN
        || diag.getLocation() == SimpleLocation.TOPLEVEL) {
      loc = swaggerFileLocation;
    } else {
      loc = new SimpleLocation(
          String.format("%s: %s", swaggerFileName, diag.getLocation().toString()));
    }
    diag =
        diag.getKind() == Kind.ERROR
            ? Diag.error(loc, diag.getMessage()) : Diag.warning(loc, diag.getMessage());

    diags.add(diag);
    if (diag.getKind() == Diag.Kind.ERROR) {
      errorCount++;
    }
  }

  /**
   * Returns the number of errors and warnings.
   */
  @Override
  public int getErrorCount() {
    return errorCount;
  }

  /**
   * Returns true if there are any diagnosed proper errors; false otherwise
   */
  @Override
  public boolean hasErrors() {
    return getErrorCount() > 0;
  }

  /**
   * Returns the diagnosis accumulated.
   */
  @Override
  public List<Diag> getDiags() {
    return diags;
  }
}
