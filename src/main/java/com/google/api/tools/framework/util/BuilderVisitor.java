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

import com.google.api.tools.framework.util.buildervisitor.BuilderVisitorNodeInfo;
import com.google.api.tools.framework.util.buildervisitor.FileNodeInfo;
import com.google.api.tools.framework.util.buildervisitor.MessageNodeInfo;
import com.google.common.base.Strings;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * A visitor base class for protocol buffer builders. Implements all the accept methods for
 * traversing children.
 */
public abstract class BuilderVisitor extends GenericVisitor<Message.Builder> {
  // Configuration for the operation of this instance.
  private final boolean manageSourceCodeInfo;

  // Current state of this visitor instance.
  private final Stack<BuilderVisitorNodeInfo> ancestors = new Stack<>();
  private final Stack<String> fullyQualifiedNameComponents = new Stack<>();
  private boolean modified = false;
  private FileNodeInfo currentFile = null;

  public BuilderVisitor() {
    this(false);
  }

  public BuilderVisitor(boolean manageSourceCodeInfo) {
    super(Message.Builder.class);
    this.manageSourceCodeInfo = manageSourceCodeInfo;
  }

  /**
   * Get an ancestor of the node currently being visited.
   *
   * <p>Specify 'generationsToSkip' as 0 for immediate parent, 1 for the grandparent, etc.
   */
  public Message.Builder getAncestor(int generationsToSkip) {
    return getAncestorInfo(generationsToSkip).node();
  }

  public String getAncestorFullyQualifiedName(int generationsToSkip) {
    return getAncestorInfo(generationsToSkip).getFullyQualifiedName();
  }

  private BuilderVisitorNodeInfo getAncestorInfo(int generationsToSkip) {
    int index = ancestors.size() - 1 - generationsToSkip;
    if (index < 0 || index >= ancestors.size()) {
      throw new RuntimeException(
          String.format(
              "getAncestorInfo(%d) called with %d ancestors on stack",
              generationsToSkip, ancestors.size()));
    }
    return ancestors.elementAt(index);
  }

  public int getNumAncestors() {
    return ancestors.size();
  }

  public Message.Builder getParent() {
    return getAncestor(0);
  }

  public FileDescriptorProto.Builder getCurrentFile() {
    if (currentFile != null) {
      return (FileDescriptorProto.Builder) currentFile.node();
    }
    return null;
  }

  public String getFullyQualifiedName() {
    return fullyQualifiedNameComponents
        .stream()
        .filter(s -> !Strings.isNullOrEmpty(s.trim()))
        .collect(Collectors.joining("."));
  }

  public String getCurrentContext() {
    StringBuilder currContext = new StringBuilder();
    for (BuilderVisitorNodeInfo ancestorInfo : ancestors) {
      currContext.append(".").append(ProtoHelpers.getContextName(ancestorInfo.node()));
    }
    return currContext.toString();
  }

  public boolean isModified() {
    return modified;
  }

  protected void setModified(boolean flag) {
    modified = flag;
  }

  protected BuilderVisitorNodeInfo pushParent(BuilderVisitorNodeInfo pushed) {
    ancestors.push(pushed);

    if (pushed instanceof FileNodeInfo) {
      currentFile = (FileNodeInfo) pushed;
      fullyQualifiedNameComponents.push(((FileDescriptorProto.Builder) pushed.node()).getPackage());
    } else {
      fullyQualifiedNameComponents.push(ProtoHelpers.getName(pushed.node()));
    }
    pushed.setFullyQualifiedName(getFullyQualifiedName());
    return pushed;
  }

  protected BuilderVisitorNodeInfo popParent() {
    BuilderVisitorNodeInfo popped = ancestors.pop();
    popped.cleanup();

    if (popped instanceof FileNodeInfo) {
      // Reset the currentFile state -- assumes that we can't have nested File nodes.
      currentFile = null;
    }
    fullyQualifiedNameComponents.pop();
    return popped;
  }

  @SuppressWarnings("ReferenceEquality")
  protected BuilderVisitorNodeInfo popExpectedParent(Message.Builder expectedParent) {
    BuilderVisitorNodeInfo oldParent = popParent();

    // The point here is to make sure that the stack hasn't been corrupted, so the caller is
    // passing an object reference and expecting that reference to be exactly what was pushed onto
    // the stack earlier and is being popped off now. (so suppressing "ReferenceEquality" warning
    // above)
    if (expectedParent != oldParent.node()) {
      throw new RuntimeException(
          String.format(
              "Ancestor stack corruption: got %s but expected %s",
              oldParent.node(), expectedParent));
    }
    return oldParent;
  }

  protected void deleteThisChild(Message.Builder element) {
    ancestors.peek().addChildToBeDeleted(element);
    setModified(true);
  }

  protected void deleteAncestor(int generationsToSkip) {
    BuilderVisitorNodeInfo ancestorInfo = getAncestorInfo(generationsToSkip);
    BuilderVisitorNodeInfo ancestorParentInfo = getAncestorInfo(generationsToSkip + 1);
    ancestorParentInfo.addChildToBeDeleted(ancestorInfo.node());
    setModified(true);
  }

  protected void addFieldToMessageParent(FieldDescriptorProto.Builder fieldDesc) {
    addFieldToMessageAncestor(0, fieldDesc, null);
  }

  protected void addFieldToMessageParent(
      FieldDescriptorProto.Builder fieldDesc, SourceCodeInfo.Location location) {
    addFieldToMessageAncestor(0, fieldDesc, location);
  }

  protected void addFieldToMessageAncestor(
      int generationsToSkip, FieldDescriptorProto.Builder fieldDesc) {
    addFieldToMessageAncestor(generationsToSkip, fieldDesc, null);
  }

  protected void addFieldToMessageAncestor(
      int generationsToSkip,
      FieldDescriptorProto.Builder fieldDesc,
      SourceCodeInfo.Location location) {
    BuilderVisitorNodeInfo ancestorInfo = getAncestorInfo(generationsToSkip);
    if (ancestorInfo instanceof MessageNodeInfo) {
      ((MessageNodeInfo) ancestorInfo).addNewField(fieldDesc, location);
      setModified(true);
    } else {
      throw new RuntimeException(
          String.format(
              "Tried to add a field to a %s, but can only add to %s",
              ancestorInfo.node().getClass(), DescriptorProto.Builder.class));
    }
  }

  protected void visitRepeated(int fieldNumber) {
    BuilderVisitorNodeInfo parentInfo = ancestors.peek();
    Message.Builder parentBuilder = (Message.Builder) parentInfo.node();

    FieldDescriptor fieldDesc = parentBuilder.getDescriptorForType().findFieldByNumber(fieldNumber);
    if (fieldDesc == null) {
      throw new RuntimeException(
          String.format(
              "BuilderVisitor internal error - bad field number %d for type %s",
              fieldNumber, parentBuilder.getDescriptorForType().getFullName()));
    }

    int fieldCount = parentBuilder.getRepeatedFieldCount(fieldDesc);
    for (int i = 0; i < fieldCount; i++) {
      Message.Builder element = parentBuilder.getRepeatedFieldBuilder(fieldDesc, i);
      // If we got in here via a FileDescriptor, let it know where we are in the traversal so it
      // can build up its path <-> proto element mappings.
      if (currentFile != null) {
        currentFile.pushChildPath(element, fieldNumber, i);
      }
      try {
        visit(element);
      } finally {
        if (currentFile != null) {
          currentFile.popChildPath();
        }
      }
    }

    parentInfo.processDeletedChildren(fieldDesc);
  }

  @Accepts
  protected void accept(final FileDescriptorSet.Builder files) {
    pushParent(BuilderVisitorNodeInfo.create(files));
    visitRepeated(FileDescriptorSet.FILE_FIELD_NUMBER);
    popExpectedParent(files);
  }

  @Accepts
  protected void accept(final FileDescriptorProto.Builder file) {
    pushParent(BuilderVisitorNodeInfo.create(file));
    currentFile.setManageSourceCodeInfo(manageSourceCodeInfo);

    visitRepeated(FileDescriptorProto.MESSAGE_TYPE_FIELD_NUMBER);
    visitRepeated(FileDescriptorProto.ENUM_TYPE_FIELD_NUMBER);
    visitRepeated(FileDescriptorProto.EXTENSION_FIELD_NUMBER);
    visitRepeated(FileDescriptorProto.SERVICE_FIELD_NUMBER);

    visit(file.getOptionsBuilder());

    popExpectedParent(file);
  }

  @Accepts
  protected void accept(DescriptorProto.Builder message) {
    MessageNodeInfo messageInfo =
        (MessageNodeInfo) pushParent(BuilderVisitorNodeInfo.create(message, currentFile));

    visitRepeated(DescriptorProto.FIELD_FIELD_NUMBER);
    visitRepeated(DescriptorProto.NESTED_TYPE_FIELD_NUMBER);
    visitRepeated(DescriptorProto.ENUM_TYPE_FIELD_NUMBER);
    visitRepeated(DescriptorProto.EXTENSION_FIELD_NUMBER);

    visit(message.getOptionsBuilder());

    popExpectedParent(message);
  }

  @Accepts
  protected void accept(FieldDescriptorProto.Builder field) {
    pushParent(BuilderVisitorNodeInfo.create(field, currentFile));
    visit(field.getOptionsBuilder());
    popExpectedParent(field);
  }

  @Accepts
  protected void accept(EnumDescriptorProto.Builder enumType) {
    pushParent(BuilderVisitorNodeInfo.create(enumType, currentFile));
    visitRepeated(EnumDescriptorProto.VALUE_FIELD_NUMBER);
    visit(enumType.getOptionsBuilder());
    popExpectedParent(enumType);
  }

  @Accepts
  protected void accept(EnumValueDescriptorProto.Builder val) {
    pushParent(BuilderVisitorNodeInfo.create(val, currentFile));
    visit(val.getOptionsBuilder());
    popExpectedParent(val);
  }

  @Accepts
  protected void accept(ServiceDescriptorProto.Builder service) {
    pushParent(BuilderVisitorNodeInfo.create(service, currentFile));
    visitRepeated(ServiceDescriptorProto.METHOD_FIELD_NUMBER);
    visit(service.getOptionsBuilder());
    popExpectedParent(service);
  }

  @Accepts
  protected void accept(MethodDescriptorProto.Builder method) {
    pushParent(BuilderVisitorNodeInfo.create(method, currentFile));
    visit(method.getOptionsBuilder());
    popExpectedParent(method);
  }
}
