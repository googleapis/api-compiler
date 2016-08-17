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

package com.google.api.tools.framework.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Syntax;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Base class of model elements which stem from protocol buffers.
 */
public abstract class ProtoElement extends Element {

  private final ProtoElement parent;
  private final String name;
  private final String path;

  /**
   * Creates the element, given its parent and (simple) name.
   */
  protected ProtoElement(
      @Nullable ProtoElement parent, String name, String path) {
    this.parent = parent;
    this.name = Preconditions.checkNotNull(name);
    this.path = path;
  }

  //-------------------------------------------------------------------------
  // Syntax

  /**
   * Returns the parent, or null if this a root (a proto file).
   */
  @Nullable public ProtoElement getParent() {
    return parent;
  }

  /**
   * Returns the model.
   */
  @Override
  public Model getModel() {
    // Don't need to care about root case as that overrides.
    return parent.getModel();
  }

  /**
   * Returns the simple name. For a file, this will return the file name, relative to root of source
   * tree.
   */
  @Override
  public String getSimpleName() {
    return name;
  }

  /**
   * Returns the full name of this element, including package and outer messages
   * for nested elements. For a file, this will return the package name of the file.
   */
  @Override
  public String getFullName() {
    // Don't need to care about root case as that overrides.
    if (Strings.isNullOrEmpty(parent.getFullName())) {
      return getSimpleName();
    }
    return parent.getFullName() + "." + getSimpleName();
  }

  /**
   * Returns the proto file in which this element lives.
   */
  public ProtoFile getFile() {
    // Don't need to care about root case as that overrides.
    return parent.getFile();
  }

  /**
   * Return this element's options from the proto.
   */
  public abstract Map<FieldDescriptor, Object> getOptionFields();

  /**
   * Deliver the location of this protocol element.
   */
  @Override
  public Location getLocation() {
    return getFile().getLocation(this);
  }

  /**
   * Returns the location path of this element in the proto file.
   */
  String getPath() {
    return path;
  }

  /**
   * Helper function to build location path.
   */
  static String buildPath(String parentPath, int fieldNumber, int fieldIndex) {
    if (Strings.isNullOrEmpty(parentPath)) {
      return String.format("%d.%d", fieldNumber, fieldIndex);
    } else {
      return String.format("%s.%d.%d", parentPath, fieldNumber, fieldIndex);
    }
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to merged stage

  /**
   * Gets the syntax in which the element was defined.
   */
  public Syntax getSyntax() {
    return parent.getSyntax();
  }

  /**
   * Return true if the element is reachable with the current scoper.
   */
  public boolean isReachable() {
    return getModel().getScoper().isReachable(this);
  }

  /**
   * Returns true if this object represents something that is configured as deprecated.
   */
  public boolean isDeprecated() {
    // Assume that element is not deprecated - subclasses will override this method if they can be
    // marked as deprecated.
    return false;
  }

}
