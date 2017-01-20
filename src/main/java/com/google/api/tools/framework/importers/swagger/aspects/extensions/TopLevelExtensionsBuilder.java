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

package com.google.api.tools.framework.importers.swagger.aspects.extensions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.utils.ExtensionNames;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionUtils;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import io.swagger.models.Swagger;
import java.io.IOException;
import java.util.List;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Class to parser "x-google-*" extensions and build top level Service config fields like
 * Service.endpoints etc.
 */
public final class TopLevelExtensionsBuilder implements AspectBuilder{

  private final ImmutableMap<String, Integer> extensionsToFieldId =
      ImmutableMap.of(ExtensionNames.ENDPOINTS_EXTENSION_NAME, Service.ENDPOINTS_FIELD_NUMBER);

  private final DiagCollector diagCollector;

  public TopLevelExtensionsBuilder(DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
  }

  @Override
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger) {

    for (String extensionName : extensionsToFieldId.keySet()) {
      FieldDescriptor field =
          Service.getDescriptor().findFieldByNumber(extensionsToFieldId.get(extensionName));
      if (isFieldExtensionValid(extensionName, field, swagger)) {
        try {
          if (field.isRepeated()) {
            serviceBuilder.setField(
                field,
                getRepeatedFieldExtension(
                    swagger.getVendorExtensions().get(extensionName), extensionName, field));

          } else {
            // For now we only have extension for repeated fields. As we add more extensions, this
            // code can be added.
            throw new NotImplementedException(
                "Extensions for non repeated fields is not implemented yet.");
          }
        } catch (Exception ex) {
          diagCollector.addDiag(
              Diag.error(
                  new SimpleLocation("Swagger Spec"),
                  "Extension %s value cannot be parsed successfully. Details: %s",
                  extensionName,
                  ex.getMessage()));
        }
      }
    }
  }

  private List<Message> getRepeatedFieldExtension(
      Object extension, String extensionName, FieldDescriptor field) throws IOException {
    List<Message> messages = Lists.newArrayList();

    String extensionJson = new ObjectMapper().writer().writeValueAsString(extension);
    JsonNode jsonNode = new ObjectMapper().readTree(extensionJson);

    if (!jsonNode.isArray()) {
      throw new IllegalArgumentException(
          String.format(
              "The extension %s does not match the schema. It shoud be a json array. Please refer "
              + "to documentation of the extension.",
              extensionName));
    }

    for (final JsonNode messageNode : jsonNode) {
      messages.add(parseMessage(field, messageNode));
    }
    return messages;
  }

  private Message parseMessage(FieldDescriptor field, final JsonNode messageNode)
      throws JsonProcessingException, InvalidProtocolBufferException {
    Builder builder = Service.newBuilder().newBuilderForField(field);
    String messageAsJson = new ObjectMapper().writeValueAsString(messageNode);
    JsonFormat.parser().merge(messageAsJson, builder);
    return builder.build();
  }

  private boolean isFieldExtensionValid(
      String extensionName, FieldDescriptor field, Swagger swagger) {
    Class<?> expected = field.isRepeated() ? List.class : Object.class;
    String extensionNameUsed =
        VendorExtensionUtils.usedExtension(
            diagCollector, swagger.getVendorExtensions(), extensionName);
    return !Strings.isNullOrEmpty(extensionNameUsed)
        && VendorExtensionUtils.getExtensionValue(
                swagger.getVendorExtensions(), expected, diagCollector, extensionNameUsed)
            != null;
  }
}
