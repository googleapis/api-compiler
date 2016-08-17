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
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Extension;
import com.google.protobuf.Message;

import java.util.Map;

/**
 * Represents a method declaration. The REST accessors correspond to the primary REST binding.
 */
public class Method extends ProtoElement {

  /**
   * Creates a method with {@link MethodDescriptorProto}.
   */
  public static Method create(Interface parent, MethodDescriptorProto proto, String path) {
    return new Method(parent, proto, path);
  }

  private final MethodDescriptor descriptor;
  private final boolean isDeprecated;
  private final boolean requestStreaming;
  private final boolean responseStreaming;

  private Method(Interface parent, MethodDescriptorProto proto, String path) {
    super(parent, proto.getName(), path);
    this.isDeprecated = proto.getOptions().getDeprecated();
    this.descriptor = new MethodDescriptor(proto);
    this.requestStreaming = proto.getClientStreaming();
    this.responseStreaming = proto.getServerStreaming();
  }

  @Override
  public String toString() {
    return "method " + getFullName();
  }

  /**
   * Returns true if this object represents something that is configured as deprecated.
   */
  @Override
  public boolean isDeprecated() {
    return isDeprecated;
  }

  //-------------------------------------------------------------------------
  // Syntax

  /**
   * Returns the {@link MethodDescriptor}.
   */
  public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  /** Return this element's options from the proto. */
  @Override
  public Map<FieldDescriptor, Object> getOptionFields() {
    return descriptor.optionFields;
  }

  /**
   * Returns true if request is streamed.
   */
  public boolean getRequestStreaming() {
    return requestStreaming;
  }

  /**
   * Returns true if response is streamed.
   */
  public boolean getResponseStreaming() {
    return responseStreaming;
  }

  //-------------------------------------------------------------------------
  // Attributes belonging to resolved stage

  @Requires(Resolved.class) private TypeRef inputType;
  @Requires(Resolved.class) private TypeRef outputType;

  /**
   * Returns the input type.
   */
  @Requires(Resolved.class)
  public TypeRef getInputType() {
    return inputType;
  }

  /**
   * For setting the input type.
   */
  public void setInputType(TypeRef inputType) {
    this.inputType = inputType;
  }

  /**
   * Returns the output type.
   */
  @Requires(Resolved.class)
  public TypeRef getOutputType() {
    return outputType;
  }

  /**
   * For setting the output type.
   */
  public void setOutputType(TypeRef outputType) {
    this.outputType = outputType;
  }

  /**
   * Helper for getting the methods input message.
   */
  // TODO(user): The majority of calls to getInputType and getOutputType just want the
  // MessageType; substitute them for clarity.
  public MessageType getInputMessage() {
    return inputType.getMessageType();
  }

  /**
   * Helper for getting the methods output message.
   */
  public MessageType getOutputMessage() {
    return outputType.getMessageType();
  }

  //-------------------------------------------------------------------------
  // Adapting for legacy stream types

  /**
   * An adapter type for {@link MethodDescriptorProto}.
   */
  public static class MethodDescriptor {

    private final String inputTypeName;
    private final String outputTypeName;
    private final MethodDescriptorProto methodProto;

    private final Map<FieldDescriptor, Object> optionFields;

    private MethodDescriptor(MethodDescriptorProto methodProto) {
      this.inputTypeName = methodProto.getInputType();
      this.outputTypeName = methodProto.getOutputType();
      this.optionFields = ImmutableMap.copyOf(methodProto.getOptions().getAllFields());
      this.methodProto = methodProto;
    }

    /**
     * Returns the type name of the input message
     */
    public String getInputTypeName() {
      return inputTypeName;
    }

    /**
     * Returns the type name of the output message
     */
    public String getOutputTypeName() {
      return outputTypeName;
    }

    /**
     * Returns a method-level annotation, or null if it is a stream.
     */
    public <T extends Message> T getMethodAnnotation(Extension<MethodOptions, T> extension) {
      return methodProto.getOptions().getExtension(extension);
    }

  }
}
