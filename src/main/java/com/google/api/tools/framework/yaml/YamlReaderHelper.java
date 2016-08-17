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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.HashSet;
import java.util.Set;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.nodes.Node;

/**
 * Configuration objects used during YamlParsing w/ convenience methods for common operations
 */
final class YamlReaderHelper {

  private final DiagCollector diag;
  private final String fileName;
  private final Set<String> traversedPaths = new HashSet<>();

  public YamlReaderHelper(DiagCollector diag, String fileName){
    this.diag = diag;
    this.fileName = fileName;
  }

  public DiagCollector getDiag(){
    return diag;
  }

  public String getInputName(){
    return fileName;
  }

  public boolean checkAndAddPath(String path, Node value, FieldDescriptor field){
    if (!traversedPaths.add(path)) {
      error(value, "Node '%s' is already defined in this yaml file. Multiple definitions "
          + "for the same node are not allowed.", field.getFullName());
      return false;
    }
    return true;
  }

  public void warning(Node node, String message, Object... params) {
    diag.addDiag(Diag.warning(getLocation(node), message, params));
  }

  public void error(Location location, String message, Object...params) {
    diag.addDiag(Diag.error(location, message, params));
  }

  public void error(Mark mark, String message, Object... params) {
    error(getLocation(mark), message, params);
  }

  public void error(Node node, String message, Object... params) {
    error(getLocation(node.getStartMark()), message, params);
  }

  public Location getLocation(Node node) {
    return getLocation(node.getStartMark());
  }

  private Location getLocation(Mark mark) {
    return new SimpleLocation(String.format("%s:%s", fileName, mark.getLine() + 1), fileName);
  }
}
