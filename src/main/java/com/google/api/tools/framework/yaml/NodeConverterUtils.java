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

import com.google.api.tools.framework.yaml.ProtoFieldValueParser.ParseException;
import com.google.api.tools.framework.yaml.ProtoFieldValueParser.UnsupportedTypeException;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Descriptors.FieldDescriptor;

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Common utilities for doing type conversions on Nodes
 */
public final class NodeConverterUtils {
  
  public static Object convert(YamlReaderHelper config, FieldDescriptor field, Node node) {
    String value = getStringValue(config, node);
   
    try {
      return node == null ? null : ProtoFieldValueParser.parseFieldFromString(field, value);
    } catch (ParseException | UnsupportedTypeException e) {
      config.error(node, "Parsing of field '%s' failed: %s", field.getName(), e.getMessage());
      return null;
    }
  } 
  
  public static SequenceNode expectList(YamlReaderHelper config, FieldDescriptor field, Node node) {
    if (isEmpty(node)) {
      return new SequenceNode(Tag.SEQ, ImmutableList.<Node>of(), false);
    } else if (node instanceof ScalarNode) {
      // Allow a singleton as a list.
      return new SequenceNode(Tag.SEQ, ImmutableList.<Node>of(node), false);
    } else if (node instanceof SequenceNode) {
      return (SequenceNode) node;
    } else {
      config.error(node, "Expected a list for field '%s', found '%s'.",
          field.getFullName(), node.getNodeId());
      return new SequenceNode(Tag.SEQ, ImmutableList.<Node>of(), false);
    }
  }

  public static MappingNode expectMap(YamlReaderHelper config, FieldDescriptor field, Node node) {
    if (isEmpty(node)) {
      return new MappingNode(Tag.OMAP, ImmutableList.<NodeTuple>of(), false);
    } else if (node instanceof MappingNode) {
      return (MappingNode) node;
    } else {
      config.error(node, "Expected a map to merge with '%s', found '%s'.",
          field.getFullName(), node.getNodeId());
      return new MappingNode(Tag.OMAP, ImmutableList.<NodeTuple>of(), false);
    }
  }
  
  public static boolean isEmpty(Node node) {
    return node instanceof ScalarNode && ((ScalarNode) node).getValue().trim().isEmpty();
  }
  
  public static String getStringValue(YamlReaderHelper config, Node node) {
    if (!(node instanceof ScalarNode)) {
      config.error(node, "Expected a scalar value.");
      return null;
    }
    return ((ScalarNode) node).getValue();
  }
  
}

