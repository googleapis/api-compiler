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

package com.google.api.tools.framework.yaml;

import com.google.api.tools.framework.model.ConfigSource;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.Set;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Recursively reads A YamlNode and all of its children
 */
class YamlNodeReader {

  private static final String PATH_SEPARATOR = "/";

  private static final Set<String> WRAPPER_TYPES = ImmutableSet.<String>builder()
      .add("google.protobuf.DoubleValue")
      .add("google.protobuf.FloatValue")
      .add("google.protobuf.Int64Value")
      .add("google.protobuf.Int32Value")
      .add("google.protobuf.UInt64Value")
      .add("google.protobuf.UInt32Value")
      .add("google.protobuf.Int32Value")
      .add("google.protobuf.BoolValue")
      .add("google.protobuf.StringValue")
      .add("google.protobuf.BytesValue")
      .build();

  private final ConfigSource.Builder builder;
  private final String pathToNode;
  private final YamlReaderHelper helper;

  public YamlNodeReader(YamlReaderHelper helper, ConfigSource.Builder builder,
      String pathToNode){
    this.helper = helper;
    this.builder = builder;
    this.pathToNode = pathToNode;
  }

  public void readNode(Node node) {
    if (node == null) {
      return;
    }

    Descriptor messageType = builder.getDescriptorForType();

    if (WRAPPER_TYPES.contains(messageType.getFullName())) {
      // Message is a wrapper type. Directly read value into wrapped field.
      FieldDescriptor wrapperField = messageType.findFieldByName("value");
      readField(builder, wrapperField, node, appendToPath(pathToNode, wrapperField.getName()));
      return;
    }

    if (NodeConverterUtils.isEmpty(node)) {
      node = new MappingNode(Tag.OMAP, ImmutableList.<NodeTuple>of(), false);
    }

    if (!(node instanceof MappingNode)) {
      helper.error(node, "Expected a map to merge with '%s', found '%s'.",
          messageType.getFullName(), node.getNodeId());
      return;
    }
    if (messageType.getOptions().getDeprecated()) {
      helper.warning(node, "The type '%s' is deprecated.", messageType.getFullName());
    }
    MappingNode map = (MappingNode) node;
    for (NodeTuple entry : map.getValue()) {
      String key = NodeConverterUtils.getStringValue(helper, entry.getKeyNode());
      if (key == null) {
        // Error reported.
        continue;
      }
      FieldDescriptor field = messageType.findFieldByName(key);
      if (field == null) {
        helper.error(entry.getKeyNode(), "Found field '%s' which is unknown in '%s'.", key,
            messageType.getFullName());
      } else {
        if (field.getOptions().getDeprecated()) {
          helper.warning(node, "The field '%s' is deprecated.", field.getName());
        }
        readField(builder, field, entry.getValueNode(), appendToPath(pathToNode, field.getName()));
      }
    }
  }

  private void readField(ConfigSource.Builder builder, FieldDescriptor field, Node value,
      String path) {
    if (!helper.checkAndAddPath(path, value, field)){
      return;
    }
    if (field.getType() == FieldDescriptor.Type.MESSAGE) {
      handleMessageField(builder, field, value, path);
    } else {
      handleNonMessageField(builder, field, value);
    }
  }

  private void handleMessageField(ConfigSource.Builder builder, FieldDescriptor field, Node value,
      String path){
    if (field.isMapField()) {

      MappingNode map = NodeConverterUtils.expectMap(helper, field, value);
      FieldDescriptor keyField = field.getMessageType().getFields().get(0);
      FieldDescriptor valueField = field.getMessageType().getFields().get(1);
      boolean isNested =
          field.getMessageType().getFields().get(1).getType() == FieldDescriptor.Type.MESSAGE;
      for (NodeTuple entry : map.getValue()) {

        Object keyObj = NodeConverterUtils.convert(helper, keyField, entry.getKeyNode());
        if (keyObj == null) {
          continue;
        }
        if (isNested) {
          String nestedPath = appendToPath(path, keyObj);
          helper.checkAndAddPath(nestedPath, value, field);
          builder.withBuilder(field, keyObj, new ReadNodeBuildAction(helper, entry.getValueNode(),
              appendToPath(nestedPath, keyObj)));
        } else {
          Object valueObj = NodeConverterUtils.convert(helper, valueField, entry.getValueNode());
          if (valueObj != null) {
            builder.setValue(field, keyObj, valueObj, helper.getLocation(entry.getValueNode()));
          }
        }
      }
    } else if (field.isRepeated()) {
      SequenceNode list = NodeConverterUtils.expectList(helper, field, value);
      int index = 0;
      for (Node elem : list.getValue()) {
        String indexedPath = String.format("%s[%s]", path, index++);
        builder.withAddedBuilder(field, new ReadNodeBuildAction(helper, elem, indexedPath));
      }
    } else {
      builder.withBuilder(field, new ReadNodeBuildAction(helper, value, path));
    }
  }

  private void handleNonMessageField(ConfigSource.Builder builder, FieldDescriptor field,
      Node value){
    if (field.isRepeated()) {
      SequenceNode list = NodeConverterUtils.expectList(helper, field, value);
      for (Node elem : list.getValue()) {
        Object protoValue = NodeConverterUtils.convert(helper, field, elem);
        if (protoValue != null) {
          builder.addValue(field, protoValue, helper.getLocation(elem));
        }
      }
    } else {
      Object protoValue = NodeConverterUtils.convert(helper, field, value);
      if (protoValue != null) {
        builder.setValue(field, null, protoValue, helper.getLocation(value));
      }
    }
  }

  private static String appendToPath(String path, Object element) {
    return path + PATH_SEPARATOR + element;
  }
  
}
