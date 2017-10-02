/*
 * Copyright (C) 2017 Google, Inc.
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

package com.google.api.tools.framework.util.buildervisitor;

import com.google.api.tools.framework.util.ProtoHelpers;
import com.google.common.base.MoreObjects;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Message;
import java.util.HashSet;
import java.util.Set;

/** Context info for a "message type" node in the tree being traversed by a BuilderVisitor. */
public class MessageNodeInfo extends GenericNodeInfo {
  private final Set<FieldLocation> toBeAddedFields = new HashSet<FieldLocation>();

  // Package scope - use static create() methods in BuilderVisitorNodeInfo to instantiate.
  MessageNodeInfo(Message.Builder node, FileNodeInfo fileNodeInfo) {
    super(node, fileNodeInfo);
  }

  public void cleanup() {
    processAddedFields(getContainingFile());
  }

  // Used to synthesize new fields into a Message type ancestor
  public Set<FieldLocation> toBeAddedFields() {
    return toBeAddedFields;
  }

  public void addNewField(
      FieldDescriptorProto.Builder fieldDesc, SourceCodeInfo.Location location) {
    toBeAddedFields.add(FieldLocation.create(fieldDesc, location));
  }

  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("class", node().getClass())
        .add("name", ProtoHelpers.getName(node()))
        .add("delete", toBeDeleted())
        .add("add", toBeAddedFields())
        .toString();
  }

  private void processAddedFields(FileNodeInfo currentFile) {
    if (!toBeAddedFields().isEmpty()) {
      DescriptorProto.Builder message = (DescriptorProto.Builder) node();

      if (currentFile != null) {
        currentFile.processAddedFields(message, toBeAddedFields());
      }

      for (FieldLocation fieldInfo : toBeAddedFields()) {
        message.addField(fieldInfo.fieldDescriptor());
      }
    }
  }
}
