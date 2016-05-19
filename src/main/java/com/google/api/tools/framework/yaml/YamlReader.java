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

import com.google.api.Service;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.protobuf.Message;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.composer.ComposerException;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Yaml configuration reader.
 */
public class YamlReader {

  /**
  * Reads a configuration from Yaml, reporting errors to the diag collector. This expects a
  * top-level field 'type' in the config which must be in the passed config type map.
  *
  * <p>Returns proto {@link ConfigSource} representing the config, or null if
  * errors were detected while processing the input.
  */
  @Nullable public static ConfigSource readConfig(DiagCollector collector,
      String inputName, String input, Map<String, Message> supportedConfigTypes) {
    return new YamlReader(collector, inputName, supportedConfigTypes).readYamlString(input);
  }

  /**
   * Same as {@link #readConfig(DiagCollector, String, String, Map)} but works with
   * a default assignment of config types which are known to the framework.
   */
  @Nullable public static ConfigSource readConfig(DiagCollector collector,
      String inputName, String input) {
    return readConfig(collector, inputName, input, SUPPORTED_CONFIG_TYPES);
  }

   /**
   * Reads protos from Yaml, reporting errors to the diag collector. This expects a top-level
   * field 'type' in the config which must be one of the statically configured config types
   * found in {@link #SUPPORTED_CONFIG_TYPES}.
   *
   * <p>Returns proto {@link Message} representing the config, or null if
   * errors detected while processing the input.
   */
  @Deprecated
  @Nullable public static Message read(DiagCollector collector, String inputName, String input) {
    return read(collector, inputName, input, SUPPORTED_CONFIG_TYPES);
  }

  /**
   * Same as {@link #read(DiagCollector, String, String)} but allows to specify a map from
   * message type names to message prototypes which represents the allowed top-level types.
   */
  @Deprecated
  @Nullable public static Message read(DiagCollector collector, String inputName, String input,
      Map<String, Message> supportedConfigTypes) {
    ConfigSource source = new YamlReader(collector, inputName, supportedConfigTypes)
        .readYamlString(input);
    return source != null ? source.getConfig() : null;
  }

  /**
   * Same as {@link #read(DiagCollector, String, String, Map)} but reads the input from a file.
   */
  @Deprecated
  @Nullable public static Message read(DiagCollector collector, File input,
      Map<String, Message> supportedConfigTypes) {
    try {
      String fileContent = Files.toString(input, Charset.forName("UTF8"));
      return read(collector, input.getName(), fileContent, supportedConfigTypes);
    } catch (IOException e) {
      collector.addDiag(Diag.error(SimpleLocation.TOPLEVEL,
          "Cannot read configuration file '%s': %s", input.getName(), e.getMessage()));
      return null;
    }
  }

  // An instance of the snakeyaml reader which does not do any implicit conversions.
  private static final Yaml YAML =
      new Yaml(new Constructor(), new Representer(), new DumperOptions(), new Resolver());

  // Supported configuration types. (May consider to move this out here for more generic
  // use.)
  @VisibleForTesting
  static final Map<String, Message> SUPPORTED_CONFIG_TYPES =
      ImmutableMap.<String, Message>of(
      Service.getDescriptor().getFullName(), Service.getDefaultInstance());

  private static final String TYPE_KEY = "type";

  private final YamlReaderHelper helper;
  private final Map<String, Message> supportedConfigTypes;

  private YamlReader(DiagCollector diag, String inputName,
      Map<String, Message> supportedConfigTypes) {
    helper = new YamlReaderHelper(diag, inputName);
    this.supportedConfigTypes = supportedConfigTypes;
  }

  private ConfigSource readYamlString(String input) {
    int initialErrorCount = helper.getDiag().getErrorCount();
    Node tree;
    try {
      tree = YAML.compose(new StringReader(input));
    } catch (ComposerException e) {
      helper.error(e.getProblemMark(), "Parsing error: %s", e.getMessage());
      return null;
    } catch (Exception e) {
      helper.error(SimpleLocation.UNKNOWN, "Parsing error: %s", e.getMessage());
      return null;
    }

    // Identify the configuration type.
    if (!(tree instanceof MappingNode)) {
      helper.error(tree, "Expected a map as a root object.");
      return null;
    }
    MappingNode map = (MappingNode) tree;
    List<NodeTuple> entriesWithoutType = new ArrayList<>();
    String typeName = null;
    for (NodeTuple entry : map.getValue()) {
      String name = NodeConverterUtils.getStringValue(helper, entry.getKeyNode());
      if (name == null) {
        // Error already reported.
        return null;
      }
      if (TYPE_KEY.equals(name)) {
        typeName = NodeConverterUtils.getStringValue(helper, entry.getValueNode());
        if (typeName == null) {
          // Error already reported.
          return null;
        }
      } else {
        entriesWithoutType.add(entry);
      }
    }
    if (typeName == null) {
      helper.error(tree, "Expected a field '%s' specifying the configuration type "
          + "name in root object.", TYPE_KEY);
      return null;
    }

    // Construct prototype message and read config.
    map.setValue(entriesWithoutType);
    Message prototype = supportedConfigTypes.get(typeName);
    if (prototype == null) {
      helper.error(tree, "The specified configuration type '%s' is unknown.",
          typeName);
      return null;
    }
    ConfigSource.Builder builder = ConfigSource.newBuilder(prototype);
    new YamlNodeReader(helper, builder, "").readNode(map);
    return helper.getDiag().getErrorCount() == initialErrorCount ? builder.build() : null;
  }

}
