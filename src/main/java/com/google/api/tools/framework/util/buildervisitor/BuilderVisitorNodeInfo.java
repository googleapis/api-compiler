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

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.Set;

/**
 * Context info for a specific node in the tree being traversed by a BuilderVisitor. This class
 * specifies the base interface and includes factory methods for creating the *NodeInfo objects.
 */
public abstract class BuilderVisitorNodeInfo {
  public static BuilderVisitorNodeInfo create(Message.Builder node, FileNodeInfo parentFile) {
    return new GenericNodeInfo(node, parentFile);
  }

  public static BuilderVisitorNodeInfo create(
      DescriptorProto.Builder node, FileNodeInfo parentFile) {
    return new MessageNodeInfo(node, parentFile);
  }

  public static BuilderVisitorNodeInfo create(FileDescriptorProto.Builder node) {
    return new FileNodeInfo(node);
  }

  public static BuilderVisitorNodeInfo create(FileDescriptorSet.Builder node) {
    // FileDescriptorSet can't be contained within a FileDescriptor, so pass null here.
    return new GenericNodeInfo(node, null);
  }

  /** Get the file node corresponding to the FileDescriptor containing this node's proto. */
  public abstract FileNodeInfo getContainingFile();

  /** Get the Builder object associated with this node being visited by the BuilderVisitor. */
  public abstract Message.Builder node();

  /** Get the name of the node() this NodeInfo object wraps */
  public abstract String getFullyQualifiedName();

  /** Set the name of the node() this NodeInfo object wraps */
  public abstract void setFullyQualifiedName(String fullyQualifiedName);

  /** Get the set of child Builder objects to be deleted from this parent node. */
  public abstract Set<Message.Builder> toBeDeleted();

  /** Add a child Builder object to the set of those to be deleted. */
  public abstract void addChildToBeDeleted(Message.Builder child);

  /**
   * Processes the deletion of children via a particular child field of the current node's
   * descriptor (e.g. fields within a message; nested message types within a message; etc).
   *
   * <p>NOTE: There is global state (e.g. SourceCodeInfo.Location info) stored at the file level, so
   * the deletion processing needs that as well.
   */
  public abstract void processDeletedChildren(FieldDescriptor childFieldDesc);

  /**
   * At the end of processing all of the children of this node, this cleanup() method will be called
   * by BuilderVisitor.
   */
  public abstract void cleanup();
}
