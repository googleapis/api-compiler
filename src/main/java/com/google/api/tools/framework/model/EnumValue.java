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

import com.google.api.tools.framework.model.stages.Requires;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;

/**
 * Represents an enum value.
 */
public class EnumValue extends ProtoElement {

  /**
   * Creates an enum value backed up by the given proto.
   */
  public static EnumValue create(EnumType parent, EnumValueDescriptorProto proto, String path) {
    return new EnumValue(parent, proto, path);
  }

  private final EnumValueDescriptorProto proto;

  private EnumValue(EnumType parent, EnumValueDescriptorProto proto, String path) {
    super(parent, proto.getName(), path);
    this.proto = proto;
  }

  @Override public String toString() {
    return "value " + getFullName();
  }

  /**
   * Returns true if this object represents something that is configured as deprecated.
   */
  @Override
  public boolean isDeprecated() {
    return proto.getOptions().getDeprecated();
  }

  //-------------------------------------------------------------------------
  // Syntax

  private int valueIndex = -1;

  /**
   * Returns the underlying proto representation.
   */
  public EnumValueDescriptorProto getProto() {
    return proto;
  }

  /**
   * Return the number of the enum value.
   */
  public int getNumber() {
    return proto.getNumber();
  }

  /**
   * Get the index position of this value in its parent.
   */
  public int getIndex() {
    if (valueIndex < 0) {
      valueIndex = ((EnumType) getParent()).getValues().indexOf(this);
    }
    return valueIndex;
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to resolved stage

  @Requires(Resolved.class) private TypeRef type;

  /**
   * Gets the type.
   */
  @Requires(Resolved.class) public TypeRef getType() {
    return type;
  }

  /**
   * For setting the type.
   */
  public void setType(TypeRef type) {
    this.type = type;
  }

}
