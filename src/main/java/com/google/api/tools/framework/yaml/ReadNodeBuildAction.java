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

import com.google.api.tools.framework.model.ConfigSource.BuildAction;
import com.google.api.tools.framework.model.ConfigSource.Builder;

import org.yaml.snakeyaml.nodes.Node;

/**
 * ConfigSource build action that will read the given Yaml node, using the provided configuration
 */
class ReadNodeBuildAction implements BuildAction {

  private final String path;
  private final Node node;
  private final YamlReaderHelper helper;

  public ReadNodeBuildAction(YamlReaderHelper helper, Node node, String path) {
    this.node = node;
    this.path = path;
    this.helper = helper;
  }

  @Override
  public void accept(Builder builder) {
    new YamlNodeReader(helper, builder, path).readNode(node);
  }
}
