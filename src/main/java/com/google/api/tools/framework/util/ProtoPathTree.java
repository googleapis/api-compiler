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

package com.google.api.tools.framework.util;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

/**
 * This class is used for storing data that are indexed by a SourceCodeInfo.Location "path" field
 * (which is defined as "repeated int32"). Conceptually, the paths form a n-ary tree where (for
 * example) path [a,b] is a parent node of [a,b,c,d] & [a,b,c,d,e,f] & [a,b,g,h].
 *
 * <p>For details about these path lists, see:
 * https://developers.google.com/protocol-buffers/docs/reference/java/com/google/protobuf/DescriptorProtos.SourceCodeInfo.Location
 *
 * <p>Each instance of ProtoPathTree is a node in the tree. The entries in its map correspond to its
 * child node. Data elements stored at this node are managed via addDataElement() &
 * getDataElements() methods.
 */
public class ProtoPathTree<DATA> extends TreeMap<Integer, ProtoPathTree<DATA>> {
  private final ProtoPathTree<DATA> parent;
  private final ProtoPathWrapper pathFromRoot;
  private final List<DATA> dataElements = new ArrayList<>();
  private boolean isDeleted = false;

  public ProtoPathTree() {
    this.parent = null;
    this.pathFromRoot = ProtoPathWrapper.EMPTY_PATH;
  }

  public ProtoPathTree(ProtoPathTree<DATA> parent, int childPathIndex) {
    this.parent =
        Preconditions.checkNotNull(parent, "Parent node must be specified for the child ctor.");

    ProtoPathWrapper parentPathFromRoot = parent.getPathFromRoot();
    this.pathFromRoot = ProtoHelpers.buildPath(parentPathFromRoot, childPathIndex);
    parent.put(childPathIndex, this);
  }

  public boolean isRootNode() {
    return parent == null;
  }

  /**
   * Returns the parent node of this node in the tree. Throws an exception if there is no parent.
   */
  public ProtoPathTree<DATA> getParent() {
    return parent;
  }

  /**
   * Returns the child nodes of this one, in numerical order based on the "path" indexes of each
   * child.
   */
  public Collection<ProtoPathTree<DATA>> getChildren() {
    return values();
  }

  /**
   * Returns the path to get to this node of the tree from the root. The path from the root node to
   * itself is an empty list.
   */
  public ProtoPathWrapper getPathFromRoot() {
    return pathFromRoot;
  }

  /**
   * Visitor that visits each node in the tree, recursively. Child nodes will be visited in the same
   * order that getChildren() returns them in.
   */
  public static class Visitor extends GenericVisitor<ProtoPathTree> {
    public Visitor() {
      super(ProtoPathTree.class);
    }

    @Accepts
    public void acceptNode(ProtoPathTree tree) {
      for (Object child : tree.getChildren()) {
        visit((ProtoPathTree) child);
      }
    }
  }

  /** Returns the subtree rooted at 'childPath' into which 'dataElement' is added. */
  public ProtoPathTree<DATA> addDataElement(ProtoPathWrapper childPath, DATA dataElement) {
    if (childPath.isEmpty()) {
      dataElements.add(dataElement);
      return this;
    }

    ProtoPathTree<DATA> subtree = getSubtree(childPath, true);
    return subtree.addDataElement(ProtoPathWrapper.EMPTY_PATH, dataElement);
  }

  /** Returns the data stored at this node of the tree. Returns an empty list if none are here. */
  public ImmutableList<DATA> getDataElements() {
    return ImmutableList.copyOf(this.dataElements);
  }

  /**
   * Marks this node of the tree for deletion. Since some of the integers in a path are index
   * numbers, doing a "mark for delete" means that the index numbers aren't invalidated.
   */
  public boolean markForDeletion(boolean isDeleted) {
    boolean ret = this.isDeleted;
    this.isDeleted = isDeleted;
    return ret;
  }

  public boolean isMarkedForDeletion() {
    return isDeleted;
  }

  /** Gets the subtree rooted at the specified child path (path relative to this node). */
  public ProtoPathTree<DATA> getSubtree(ProtoPathWrapper childPath) {
    return getSubtree(childPath, false);
  }

  /**
   * Gets the subtree rooted at the specified child path (path relative to this node), creating
   * nodes along the way as needed.
   */
  public ProtoPathTree<DATA> getSubtree(ProtoPathWrapper childPath, boolean autoCreate) {
    ProtoPathTree<DATA> currSubtree = this;

    // Descend into the tree along the path defined by 'childPath', auto-creating intermediate
    // nodes, if needed.
    for (Integer childPathIndex = 0; childPathIndex < childPath.getDepth(); childPathIndex++) {
      Integer childPathElement = childPath.getPathElement(childPathIndex);
      if (currSubtree.containsKey(childPathElement)) {
        currSubtree = currSubtree.get(childPathElement);
      } else if (autoCreate) {
        currSubtree = new ProtoPathTree<>(currSubtree, childPathElement);
      } else {
        return null;
      }
    }
    return currSubtree;
  }

  /** Removes the subtree rooted at the specified child path (path relative to this node). */
  public void removeSubtree(ProtoPathWrapper childPath) {
    if (childPath.isEmpty()) {
      // No child specified --> no-op
      return;
    }

    ProtoPathTree<DATA> childSubtree = getSubtree(childPath);
    if (childSubtree == null) {
      throw new IllegalArgumentException(
          String.format("Specified path %s doesn't exist, so can't be removed.", childPath));
    }

    ProtoPathTree<DATA> parentSubtree = childSubtree.getParent();
    Integer childPathIndex = childPath.getPathElement(childPath.getDepth() - 1);

    parentSubtree.remove(childPathIndex);
  }

  /** Format tree and its children into a string, primarily for debugging output. */
  public String format() {
    return format("", this);
  }

  public String format(String indent) {
    return format(indent, this);
  }

  public String format(String indent, ProtoPathTree<DATA> tree) {
    return MoreObjects.toStringHelper(tree)
        .add("isDeleted", tree.isDeleted)
        .add("dataElements", tree.dataElements)
        .add("children", formatChildren(indent + "|  ", tree))
        .toString();
  }

  public String formatChildren(final String indent, final ProtoPathTree<DATA> tree) {
    return Joiner.on("")
        .join(
            FluentIterable.from(tree.keySet())
                .transform(
                    new Function<Integer, String>() {
                      @Override
                      public String apply(Integer key) {
                        return String.format(
                            "\n%s[%d]=%s", indent, key, format(indent, tree.get(key)));
                      }
                    }));
  }
}
