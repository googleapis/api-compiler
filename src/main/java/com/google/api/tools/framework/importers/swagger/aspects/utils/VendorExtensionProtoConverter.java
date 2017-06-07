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
package com.google.api.tools.framework.importers.swagger.aspects.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.util.JsonFormat;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Converts Vendor Extensions to Proto messages or a list of Proto Messages */
public class VendorExtensionProtoConverter {

  private final Map<String, Object> extensions;
  private final DiagCollector diagCollector;

  public VendorExtensionProtoConverter(
      Map<String, Object> extensions, DiagCollector diagCollector) {
    if (extensions == null) {
      this.extensions = ImmutableMap.of();
    } else {
      this.extensions = ImmutableMap.copyOf(extensions);
    }
    this.diagCollector = diagCollector;
  }

  public boolean hasExtension(String extensionName) {
    return extensions.containsKey(extensionName);
  }

  /**
   * Converts the given extension into a Message of type 'T'. Takes in a 'T' Prototype object which
   * will be used for creating a T.Builder. Method takes 'T' Instead of 'T.Builder' due to type
   * issues in proto.
   */
  @Nullable
  public <T extends Message> T convertExtensionToProto(T defaultValue, String extensionName) {
    return convertJsonToProto(
        defaultValue, extensions.get(extensionName).toString(), extensionName);
  }

  /**
   * Converts the given extension into a List of Messages of type 'T'. Takes in a 'T' Prototype
   * object which will be used for creating T.Builder(s). Method takes 'T' Instead of 'T.Builder'
   * due to type issues in proto. Will fail if the provided extension is not a JsonArray. Returns
   * empty List on failure.
   */
  public <T extends Message> List<T> convertExtensionToProtos(
      T defaultValue, String extensionName) {
    JsonNode jsonNode = readExtensionAsJsonArray(extensionName);
    if (jsonNode == null) {
      return Lists.newArrayList();
    }
    return convertJsonArrayToProto(defaultValue, jsonNode, extensionName);
  }

  public <T extends Message> List<T> convertJsonArrayToProto(
      T prototype, JsonNode array, String extensionName) {
    List<T> messages = Lists.newArrayList();
    for (JsonNode messageNode : array) {
      try {
        String nodeJson = new ObjectMapper().writeValueAsString(messageNode);
        T message = convertJsonToProto(prototype, nodeJson, extensionName);
        if (!message.equals(prototype)) {
          messages.add(message);
        }
      } catch (IOException ex) {
        // Should not be possible to throw in newer versions of ObjectMapper.
        diagCollector.addDiag(
            Diag.error(
                new SimpleLocation(extensionName),
                String.format(
                    "The extension %s does not match the schema. It should be a %s. Please refer "
                        + "to documentation of the extension.",
                    extensionName, prototype.getDescriptorForType().getName())));
      }
    }
    return messages;
  }

  // Suppress because Proto has terrible type awareness with builders.
  @SuppressWarnings("unchecked")
  public <T extends Message> T convertJsonToProto(T prototype, String json, String extensionName) {
    try {
      Builder builder = prototype.newBuilderForType();
      JsonFormat.parser().merge(json, builder);
      return (T) builder.build();
    } catch (InvalidProtocolBufferException ex) {
      diagCollector.addDiag(
          Diag.error(
              new SimpleLocation(extensionName),
              "Extension %s cannot be converted into proto type %s. Details: %s",
              extensionName,
              prototype.getDescriptorForType().getFullName(),
              ex.getMessage()));
      return prototype;
    }
  }

  public JsonNode readExtensionAsJsonArray(String extensionName) {
    try {
      String extensionJson =
          new ObjectMapper().writer().writeValueAsString(extensions.get(extensionName));
      JsonNode jsonNode = new ObjectMapper().readTree(extensionJson);
      if (jsonNode.isArray()) {
        return jsonNode;
      }
    } catch (IOException ex) {
      //error handling below
    }
    diagCollector.addDiag(
        Diag.error(
            new SimpleLocation(extensionName),
            String.format(
                "The extension %s does not match the schema. It should be a json array. "
                    + "Please refer to documentation of the extension.",
                extensionName)));
    return null;
  }
}
