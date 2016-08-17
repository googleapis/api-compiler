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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Represents an enum declaration.
 */
public class EnumType extends ProtoElement {

  /**
   * Creates an enum backed up by the given proto.
   */
  public static EnumType create(ProtoContainerElement parent, EnumDescriptorProto proto,
      String path) {
    return new EnumType(parent, proto, path);
  }

  private final EnumDescriptorProto proto;
  private final ImmutableList<EnumValue> values;

  private EnumType(ProtoContainerElement parent, EnumDescriptorProto proto, String path) {
    super(parent, proto.getName(), path);
    this.proto = proto;

    // Build values.
    ImmutableList.Builder<EnumValue> valuesBuilder = ImmutableList.builder();
    List<EnumValueDescriptorProto> valueProtos = proto.getValueList();
    for (int i = 0; i < valueProtos.size(); i++) {
      EnumValueDescriptorProto value = valueProtos.get(i);
      String childPath = buildPath(path, EnumDescriptorProto.VALUE_FIELD_NUMBER, i);
      valuesBuilder.add(EnumValue.create(this, value, childPath));
    }
    values = valuesBuilder.build();
  }

  @Override public String toString() {
    return "enum " + getFullName();
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

  /**
   * Returns the underlying proto representation.
   */
  public EnumDescriptorProto getProto() {
    return proto;
  }

  @Override public Map<FieldDescriptor, Object> getOptionFields() {
    return proto.getOptions().getAllFields();
  }

  /**
   * Returns a proto representation that includes visible values only. Enum options and
   * source location are omitted since visibility isn't applicable for them.
   */
  public com.google.protobuf.Enum getVisibleProto() {
    com.google.protobuf.Enum.Builder scopedEnum = com.google.protobuf.Enum.newBuilder()
        .setName(getFullName());
    for (EnumValue value : values) {
      if (value.isReachable()) {
        scopedEnum.addEnumvalue(com.google.protobuf.EnumValue.newBuilder()
            .setName(value.getSimpleName())
            .setNumber(value.getNumber()));
      }
    }
    return scopedEnum.build();
  }

  /**
   * Returns the values.
   */
  public ImmutableList<EnumValue> getValues() {
    return values;
  }

  /**
   * Returns the enum values for which {@link ProtoElement#isReachable} is true.
   */
  public Iterable<EnumValue> getReachableValues() {
      return getModel().reachable(values);
  }

  /**
   * Returns whether any of the enum's values are hidden.
   */
  public boolean hasHiddenValue() {
    for (EnumValue value : values) {
      if (!value.isReachable()) {
        return true;
      }
    }
    return false;
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to resolved stage

  @Requires(Resolved.class) private ImmutableMap<String, EnumValue> valueByName;

  /**
   * Looks up the value by its name.
   */
  @Requires(Resolved.class)
  @Nullable
  public EnumValue lookupValue(String name) {
    return valueByName.get(name);
  }

  /**
   * For setting the value-by-name map.
   */
  public void setValueByNameMap(ImmutableMap<String, EnumValue> valueByName) {
    this.valueByName = valueByName;
  }
}
