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
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Context info for a specific node in the tree being traversed by a BuilderVisitor. This class
 * specifies the base interface and includes factory methods for creating the *NodeInfo objects.
 */
public class GenericNodeInfo extends BuilderVisitorNodeInfo {
  private final Message.Builder node;
  private final FileNodeInfo fileNodeInfo;
  private final Set<Message.Builder> toBeDeleted = new HashSet<>();

  // Package scope - use static create() methods in BuilderVisitorNodeInfo to instantiate.
  GenericNodeInfo(Message.Builder node, FileNodeInfo fileNodeInfo) {
    this.node = node;
    this.fileNodeInfo = fileNodeInfo;
  }

  @Override public void cleanup() {
    // At the end of processing all of the children of this node, this cleanup() method will be
    // called by BuilderVisitor.  By default, no cleanup is needed.
  }

  /** Get the file node corresponding to the FileDescriptor containing this node's proto. */
  @Override public FileNodeInfo getContainingFile() {
    return fileNodeInfo;
  }

  @Override public Message.Builder node() {
    return node;
  }

  @Override public Set<Message.Builder> toBeDeleted() {
    return toBeDeleted;
  }

  @Override public void addChildToBeDeleted(Message.Builder child) {
    toBeDeleted.add(child);
  }

  /**
   * Processes the deletion of children via a particular child field of the current node's
   * descriptor (e.g. fields within a message; nested message types within a message; etc).
   */
  @Override public void processDeletedChildren(FieldDescriptor childFieldDesc) {
    Message.Builder parentBuilder = node();
    if (!toBeDeleted().isEmpty()) {
      if (getContainingFile() != null) {
        getContainingFile().processDeletedChildren(toBeDeleted());
      }

      List<Message.Builder> keepItems = new ArrayList<>();
      int fieldCount = parentBuilder.getRepeatedFieldCount(childFieldDesc);
      for (int i = 0; i < fieldCount; i++) {
        Message.Builder messageBuilder = parentBuilder.getRepeatedFieldBuilder(childFieldDesc, i);
        if (!toBeDeleted().contains(messageBuilder)) {
          keepItems.add(messageBuilder);
        }
      }
      toBeDeleted().clear();

      parentBuilder.clearField(childFieldDesc);
      for (Message.Builder newChild : keepItems) {
        parentBuilder.addRepeatedField(childFieldDesc, newChild.build());
      }
    }
  }

  @Override public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("class", node().getClass())
        .add("name", ProtoHelpers.getName(node()))
        .add("delete", toBeDeleted())
        .toString();
  }
}
