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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.report.ProcessingReport;
import com.github.fge.jsonschema.report.ProcessingMessage;
import com.google.api.Service;
import com.google.api.tools.framework.tools.FileWrapper;
import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import io.swagger.util.Json;
import io.swagger.util.Yaml;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.lang3.CharUtils;

/**
 * Converts Multiple swagger files from in memory {@link FileWrapper}s to {@link SwaggerFile}
 * objects.
 */
public class MultiSwaggerParser {

  private static final String SCHEMA_RESOURCE_PATH = "swagger/schema2_0/schema.json";
  private static final Map<String, ObjectMapper> mapperForExtension =
      ImmutableMap.of("yaml", Yaml.mapper(), "yml", Yaml.mapper(), "json", Json.mapper());
  private static final String SWAGGER_VERSION_PROPERTY = "swagger";
  private static final String CURRENT_SWAGGER_VERSION = "2.0";
  private static final char API_NAME_FILLER_CHAR = '_';

  /** Build resources for a single Swagger file. */
  @AutoValue
  public abstract static class SwaggerFile {

    public abstract Service.Builder serviceBuilder();

    public abstract Swagger swagger();

    public abstract String filename();

    public abstract String apiName();

    public abstract SwaggerConversionResources conversionResources();

    public static SwaggerFile create(
        Service.Builder serviceBuilder, Swagger swagger, String filename, String typeNamespace) {
      String hostname = Strings.nullToEmpty(swagger.getHost());
      String version = Strings.nullToEmpty(swagger.getInfo().getVersion());
      String apiName = generateApiName(hostname, version);
      return new AutoValue_MultiSwaggerParser_SwaggerFile(
          serviceBuilder,
          swagger,
          filename,
          apiName,
          SwaggerConversionResources.create(swagger, filename, apiName, typeNamespace));
    }
  }

  /**
   * Generates API name in the form [hostname]_[version], with all non ASCII alphanumeric characters
   * replaced with '_'. Adds '_' at start if hostname is empty or starts with non alpha character,
   * since API names can only start with alpha or '_'
   */
  private static String generateApiName(String hostname, String version) {
    StringBuilder apiName = new StringBuilder();
    if (hostname.isEmpty() || !CharUtils.isAsciiAlpha(hostname.charAt(0))) {
      apiName.append(API_NAME_FILLER_CHAR);
    }
    return apiName
        .append(stringToAlphanumeric(hostname))
        .append(API_NAME_FILLER_CHAR)
        .append(stringToAlphanumeric(version))
        .toString();
  }

  /** Replaces all non alphanumeric characters in input string with '_' */
  private static String stringToAlphanumeric(String input) {
    StringBuilder alphaNumeric = new StringBuilder();
    for (char hostnameChar : input.toCharArray()) {
      if (CharUtils.isAsciiAlphanumeric(hostnameChar)) {
        alphaNumeric.append(hostnameChar);
      } else {
        alphaNumeric.append(API_NAME_FILLER_CHAR);
      }
    }
    return alphaNumeric.toString();
  }

  public static List<SwaggerFile> convert(List<FileWrapper> swaggerFiles, String typeNamespace)
      throws SwaggerConversionException {
    Map<String, FileWrapper> savedFilePaths = SwaggerFileWriter.saveFilesOnDisk(swaggerFiles);
    Map<String, File> swaggerFilesMap = validateInputFiles(savedFilePaths);
    Service.Builder serviceBuilder = Service.newBuilder();
    ImmutableList.Builder<SwaggerFile> swaggerObjects = ImmutableList.builder();
    for (Entry<String, File> swaggerFile : swaggerFilesMap.entrySet()) {
      swaggerObjects.add(
          buildSwaggerFile(
              serviceBuilder, swaggerFile.getKey(), swaggerFile.getValue(), typeNamespace));
    }

    return swaggerObjects.build();
  }

  private static SwaggerFile buildSwaggerFile(
      Service.Builder serviceToBuild, String userDefinedFilename, File file, String typeNamespace)
      throws SwaggerConversionException {
    Swagger swagger = new SwaggerParser().read(file.getAbsolutePath());
    if (swagger == null) {
      throw new SwaggerConversionException(
          String.format(
              "Swagger spec in file {%s} is ill formed and cannot be parsed", userDefinedFilename));
    }
    return SwaggerFile.create(serviceToBuild, swagger, userDefinedFilename, typeNamespace);
  }

  /**
   * Ensures that all files are valid json/yaml and does schema validation on swagger spec. Returns
   *
   * <p>the valid swagger file.
   *
   * @throws SwaggerConversionException
   */
  private static Map<String, File> validateInputFiles(Map<String, FileWrapper> savedFilePaths)
      throws SwaggerConversionException {
    Map<String, File> topLevelSwaggerFiles = getTopLevelSwaggerFiles(savedFilePaths);
    if (topLevelSwaggerFiles.isEmpty()) {
      throw new SwaggerConversionException(
          String.format(
              "Cannot find a valid swagger %s spec in the input files", CURRENT_SWAGGER_VERSION));
    }
    return topLevelSwaggerFiles;
  }

  private static Map<String, File> getTopLevelSwaggerFiles(Map<String, FileWrapper> savedFiles)
      throws SwaggerConversionException {
    ImmutableMap.Builder<String, File> topLevelFiles = ImmutableMap.builder();
    for (Entry<String, FileWrapper> savedFile : savedFiles.entrySet()) {

      try {
        String inputFileContent = savedFile.getValue().getFileContents().toStringUtf8();
        File inputFile = new File(savedFile.getValue().getFilename());
        ObjectMapper objMapper = createObjectMapperForExtension(inputFile);
        JsonNode data = objMapper.readTree(inputFileContent);

        if (isTopLevelSwaggerFile(data)) {
          validateSwaggerSpec(data);
          topLevelFiles.put(savedFile.getKey(), inputFile);
        }
      } catch (IOException ex) {
        throw new SwaggerConversionException("Unable to parse the content. " + ex.getMessage(), ex);
      }
    }
    return topLevelFiles.build();
  }

  private static boolean isTopLevelSwaggerFile(JsonNode data) {
    return data.get(SWAGGER_VERSION_PROPERTY) != null
        && data.get(SWAGGER_VERSION_PROPERTY).toString().contains(CURRENT_SWAGGER_VERSION);
  }

  private static ObjectMapper createObjectMapperForExtension(File file)
      throws SwaggerConversionException {
    String fileExtension = Files.getFileExtension(file.getAbsolutePath());
    if (mapperForExtension.containsKey(fileExtension)) {
      return mapperForExtension.get(fileExtension);
    }
    throw new SwaggerConversionException(
        String.format(
            "Swagger spec file '%s' has invalid extension '%s'. Only files with {%s} file "
                + "extensions are allowed.",
            file.getName(), fileExtension, Joiner.on(", ").join(mapperForExtension.keySet())));
  }

  /**
   * Validates the input Swagger JsonNode against Swagger Specification schema.
   *
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
    } catch (Exception ex) {
      throw new SwaggerConversionException("Unable to parse the content. " + ex.getMessage(), ex);
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
}
