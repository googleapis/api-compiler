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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.List;
import java.util.Map;

/**
 * Represents an oneof declaration.
 */
public class Oneof extends ProtoElement {

  final List<Field> fields = Lists.newArrayList();

  private final OneofDescriptorProto proto;

  public static Oneof create(ProtoContainerElement parent, OneofDescriptorProto proto,
      String path) {
    return new Oneof(parent, proto, path);
  }

  private Oneof(ProtoContainerElement parent, OneofDescriptorProto proto, String path) {
    super(parent, proto.getName(), path);
    this.proto = proto;
  }

  /**
   * Gets the name of the oneof.
   */
  public String getName() {
    if (Strings.isNullOrEmpty(proto.getName())) {
      return "oneof";
    }
    return proto.getName();
  }

  /** Return this element's options from the proto. */
  @Override
  public Map<FieldDescriptor, Object> getOptionFields() {
    return ImmutableMap.<FieldDescriptor, Object>of();
  }

  /**
   * Gets all fields belonging to this oneof. This includes also unreachable fields.
   */
  public List<Field> getFields() {
    return fields;
  }

  /**
   * Gets all visible fields belonging to this oneof.
   */
  public Iterable<Field> getVisibleFields() {
    return getModel().reachable(fields);
  }

  @Override
  public String toString() {
    return getName();
  }
}
