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

import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.stages.Requires;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Api;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Represents a interface declarations
 */
public class Interface extends ProtoElement {

  /**
   * Creates a interface backed up by the given proto.
   */
  public static Interface create(ProtoFile parent, ServiceDescriptorProto proto, String path) {
    return new Interface(parent, proto, path);
  }

  private final ServiceDescriptorProto proto;
  private final ImmutableList<Method> methods;

  private Interface(ProtoFile parent, ServiceDescriptorProto proto, String path) {
    super(parent, proto.getName(), path);
    this.proto = proto;

    // Build methods.
    ImmutableList.Builder<Method> methodsBuilder = ImmutableList.builder();
    List<MethodDescriptorProto> methodProtos = proto.getMethodList();
    for (int i = 0; i < methodProtos.size(); i++) {
      String childPath = buildPath(path, ServiceDescriptorProto.METHOD_FIELD_NUMBER, i);
      methodsBuilder.add(Method.create(this, methodProtos.get(i), childPath));
    }

    methods = methodsBuilder.build();
  }

  @Override
  public String toString() {
    return "api " + getFullName();
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
  public ServiceDescriptorProto getProto() {
    return proto;
  }

  @Override public Map<FieldDescriptor, Object> getOptionFields() {
    return proto.getOptions().getAllFields();
  }

  /**
   * Returns the methods.
   */
  public ImmutableList<Method> getMethods() {
    return methods;
  }

  /**
   * Returns the methods reachable with the active scoper.
   */
  public Iterable<Method> getReachableMethods() {
    return getModel().reachable(methods);
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to resolved stage

  @Requires(Resolved.class) private ImmutableMap<String, Method> methodByName;

  /**
   * Looks up a method by its name.
   */
  @Requires(Resolved.class)
  @Nullable
  public Method lookupMethod(String name) {
    return methodByName.get(name);
  }

  /**
   * For setting the method-by-name map.
   */
  public void setMethodByNameMap(ImmutableMap<String, Method> methodByName) {
    this.methodByName = methodByName;
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to the merged stage

  private Api config;

  /**
   * Returns the api config.
   */
  @Requires(Merged.class)
  public Api getConfig() {
    return config;
  }

  /**
   * Sets the api config.
   */
  public void setConfig(Api config) {
    this.config = config;
  }
}
