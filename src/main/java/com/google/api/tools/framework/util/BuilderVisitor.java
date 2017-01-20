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

package com.google.api.tools.framework.util;

import com.google.auto.value.AutoValue;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 * A visitor base class for protocol buffer builders. Implements all the accept methods for
 * traversing children.
 */
public abstract class BuilderVisitor extends GenericVisitor<MessageOrBuilder> {
  private boolean modified = false;
  private final Stack<AncestorInfo> ancestors = new Stack<>();

  @AutoValue abstract static class AncestorInfo {
    static AncestorInfo create(MessageOrBuilder ancestor) {
      return new AutoValue_BuilderVisitor_AncestorInfo(ancestor, new HashSet<MessageOrBuilder>());
    }

    abstract MessageOrBuilder ancestor();
    abstract Set<MessageOrBuilder> toBeDeleted();
  }

  public BuilderVisitor() {
    super(MessageOrBuilder.class);
  }

  /**
   * Get an ancestor of the node currently being visited.
   *
   * Specify 'generationsToSkip' as 0 for immediate parent, 1 for the grandparent, etc.
   */
  public MessageOrBuilder getAncestor(int generationsToSkip) {
    int index = ancestors.size() - 1 - generationsToSkip;
    if (index < 0 || index >= ancestors.size()) {
      throw new RuntimeException(
          String.format(
              "getAncestor(%d) called with %d ancestors on stack",
              generationsToSkip,
              ancestors.size()));
    }
    return ancestors.elementAt(index).ancestor();
  }

  public int getNumAncestors() {
    return ancestors.size();
  }

  public MessageOrBuilder getParent() {
    return getAncestor(0);
  }

  public String getCurrentContext() {
    StringBuilder currContext = new StringBuilder();
    for (AncestorInfo ancestorInfo : ancestors) {
      currContext.append(".").append(ProtoHelpers.getContextName(ancestorInfo.ancestor()));
    }
    return currContext.toString();
  }

  public boolean isModified() {
    return modified;
  }

  protected void setModified(boolean flag) {
    modified = flag;
  }

  protected MessageOrBuilder pushParent(MessageOrBuilder parent) {
    return ancestors.push(AncestorInfo.create(parent)).ancestor();
  }

  protected MessageOrBuilder popParent() {
    return ancestors.pop().ancestor();
  }

  @SuppressWarnings("ReferenceEquality")
  protected MessageOrBuilder popExpectedParent(MessageOrBuilder expectedParent) {
    MessageOrBuilder oldParent = popParent();

    // The point here is to make sure that the stack hasn't been corrupted, so the caller is
    // passing an object reference and expecting that reference to be exactly what was pushed onto
    // the stack earlier and is being popped off now. (so suppressing "ReferenceEquality" warning
    // above)
    if (expectedParent != oldParent) {
      throw new RuntimeException(
          String.format(
              "Ancestor stack corruption: got %s but expected %s",
              oldParent,
              expectedParent));
    }
    return oldParent;
  }

  protected void deleteThisChild(MessageOrBuilder element) {
    ancestors.peek().toBeDeleted().add(element);
  }

  protected void deleteAncestor(int generationsToSkip) {
    int ancestorIndex = ancestors.size() - 1 - generationsToSkip;
    int ancestorParentIndex = ancestorIndex - 1;
    if (ancestorParentIndex < 0 || ancestorIndex >= ancestors.size()) {
      throw new RuntimeException(
          String.format(
              "deleteAncestor(%d) called with %d ancestors on stack",
              generationsToSkip,
              ancestors.size()));
    }

    ancestors.elementAt(ancestorParentIndex).toBeDeleted().add(
        ancestors.elementAt(ancestorIndex).ancestor());
  }

  protected void visitRepeated(Message.Builder parentBuilder, int fieldNumber) {
    FieldDescriptor fieldDesc = parentBuilder.getDescriptorForType().findFieldByNumber(fieldNumber);

    if (!ancestors.peek().toBeDeleted().isEmpty()) {
      throw new RuntimeException("BuilderVisitor internal error - deleted item(s) not processed");
    }

    int fieldCount = parentBuilder.getRepeatedFieldCount(fieldDesc);
    for (int i = 0; i < fieldCount; i++) {
      MessageOrBuilder item = parentBuilder.getRepeatedFieldBuilder(fieldDesc, i);
      visit(item);
    }

    if (!ancestors.peek().toBeDeleted().isEmpty()) {
      List<MessageOrBuilder> keepItems = new ArrayList<>();
      fieldCount = parentBuilder.getRepeatedFieldCount(fieldDesc);
      for (int i = 0; i < fieldCount; i++) {
        MessageOrBuilder mob = parentBuilder.getRepeatedFieldBuilder(fieldDesc, i);
        if (null != mob && !ancestors.peek().toBeDeleted().contains(mob)) {
          keepItems.add(mob);
        }
      }
      ancestors.peek().toBeDeleted().clear();

      setModified(true);
      parentBuilder.clearField(fieldDesc);
      for (MessageOrBuilder mob : keepItems) {
        if (mob instanceof Message.Builder) {
          Message.Builder newChild = (Message.Builder) mob;
          parentBuilder.addRepeatedField(fieldDesc, newChild.build());
        } else {
          parentBuilder.addRepeatedField(fieldDesc, mob);
        }
      }
    }
  }

  @Accepts protected void accept(final FileDescriptorSet.Builder files) {
    pushParent(files);
    visitRepeated(files, FileDescriptorSet.FILE_FIELD_NUMBER);
    popExpectedParent(files);
  }

  @Accepts protected void accept(final FileDescriptorProto.Builder file) {
    pushParent(file);

    visitRepeated(file, FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER);
    visitRepeated(file, FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER);
    visitRepeated(file, FileDescriptorProto.EXTENSION_FIELD_NUMBER);
    visitRepeated(file, FileDescriptorProto.SERVICE_FIELD_NUMBER);

    visit(file.getOptionsBuilder());

    popExpectedParent(file);
  }

  @Accepts protected void accept(DescriptorProto.Builder message) {
    pushParent(message);

    visitRepeated(message, DescriptorProto.FIELD_FIELD_NUMBER);
    visitRepeated(message, DescriptorProto.NESTED_TYPE_FIELD_NUMBER);
    visitRepeated(message, DescriptorProto.ENUM_TYPE_FIELD_NUMBER);
    visitRepeated(message, DescriptorProto.EXTENSION_FIELD_NUMBER);

    visit(message.getOptionsBuilder());

    popExpectedParent(message);
  }

  @Accepts protected void accept(FieldDescriptorProto.Builder field) {
    pushParent(field);
    visit(field.getOptionsBuilder());
    popExpectedParent(field);
  }

  @Accepts protected void accept(EnumDescriptorProto.Builder enumType) {
    pushParent(enumType);
    visitRepeated(enumType, EnumDescriptorProto.VALUE_FIELD_NUMBER);
    visit(enumType.getOptionsBuilder());
    popExpectedParent(enumType);
  }

  @Accepts protected void accept(EnumValueDescriptorProto.Builder val) {
    pushParent(val);
    visit(val.getOptionsBuilder());
    popExpectedParent(val);
  }

  @Accepts protected void accept(ServiceDescriptorProto.Builder service) {
    pushParent(service);
    visitRepeated(service, ServiceDescriptorProto.METHOD_FIELD_NUMBER);
    visit(service.getOptionsBuilder());
    popExpectedParent(service);
  }

  @Accepts protected void accept(MethodDescriptorProto.Builder method) {
    pushParent(method);
    visit(method.getOptionsBuilder());
    popExpectedParent(method);
  }
}
