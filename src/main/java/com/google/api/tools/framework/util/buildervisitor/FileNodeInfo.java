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
import com.google.api.tools.framework.util.ProtoPathTree;
import com.google.api.tools.framework.util.ProtoPathWrapper;
import com.google.api.tools.framework.util.VisitsAfter;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.base.MoreObjects;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Message;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/** Context info for a "file" node in the tree being traversed by a BuilderVisitor. */
public class FileNodeInfo extends GenericNodeInfo {
  // Configuration state passed in at init time.
  private boolean manageSourceCodeInfo = false;

  // Current state within the current FileDescriptor:
  private boolean modifiedSourceCodeInfo = false;
  private Stack<ProtoPathWrapper> activePaths = new Stack<>();
  private ProtoPathTree<SourceCodeInfo.Location> pathToLocation = new ProtoPathTree<>();
  private Map<Message.Builder, ProtoPathWrapper> elementToOriginalPath = new HashMap<>();

  protected FileNodeInfo(Message.Builder node) {
    super(node, null);
  }

  @Override
  public FileNodeInfo getContainingFile() {
    return this;
  }

  @Override
  public void cleanup() {
    super.cleanup();

    resetPathsForFile((FileDescriptorProto.Builder) node());
  }

  public void setManageSourceCodeInfo(boolean manageSourceCodeInfo) {
    this.manageSourceCodeInfo = manageSourceCodeInfo;
    if (manageSourceCodeInfo) {
      setupPathsForFile((FileDescriptorProto.Builder) node());
    }
  }

  public boolean modifiedSourceCodeInfo() {
    return modifiedSourceCodeInfo;
  }

  public Stack<ProtoPathWrapper> activePaths() {
    return activePaths;
  }

  public ProtoPathTree<SourceCodeInfo.Location> pathToLocation() {
    return pathToLocation;
  }

  public Map<Message.Builder, ProtoPathWrapper> elementToOriginalPath() {
    return elementToOriginalPath;
  }

  public ProtoPathTree<SourceCodeInfo.Location> getLocationSubtree(ProtoPathWrapper path) {
    return pathToLocation.getSubtree(path);
  }

  public ProtoPathWrapper pathFromElement(Message.Builder element) {
    return elementToOriginalPath.get(element);
  }

  public void processDeletedChildren(Iterable<Message.Builder> elements) {
    if (manageSourceCodeInfo) {
      for (Message.Builder element : elements) {
        ProtoPathWrapper path = pathFromElement(element);
        if (path != null && !path.isEmpty()) {
          ProtoPathTree<SourceCodeInfo.Location> subtree = pathToLocation.getSubtree(path);
          modifiedSourceCodeInfo = true;
          subtree.markForDeletion(true);
        }
      }
    }
  }

  public void processAddedFields(DescriptorProto.Builder message, Iterable<FieldLocation> fields) {
    if (manageSourceCodeInfo) {
      ProtoPathWrapper messagePath = pathFromElement(message);
      if (messagePath == null) {
        throw new RuntimeException(
            String.format(
                "Internal error - couldn't find path for proto message %s",
                ProtoHelpers.getName(message)));
      }
      ProtoPathWrapper fieldsPath =
          ProtoHelpers.buildPath(messagePath, DescriptorProto.FIELD_FIELD_NUMBER);
      ProtoPathTree<SourceCodeInfo.Location> fieldsPathTree =
          pathToLocation.getSubtree(fieldsPath, true);
      for (FieldLocation field : fields) {
        Integer fieldIndex = fieldsPathTree.size();
        if (fieldIndex > 0
            && (fieldsPathTree.firstKey() != 0 || fieldsPathTree.lastKey() != (fieldIndex - 1))) {
          throw new RuntimeException(
              String.format(
                  "BuilderVisitor internal error - non-contiguous field indexes found [%d..%d]\n",
                  fieldsPathTree.firstKey(), fieldsPathTree.lastKey()));
        }
        fieldsPathTree.addDataElement(
            new ProtoPathWrapper(fieldIndex), // relative path of field within this message
            field.location());
        elementToOriginalPath.put(
            field.fieldDescriptor(), ProtoHelpers.buildPath(fieldsPath, fieldIndex));
      }
    }
  }

  public void pushChildPath(Message.Builder element, Integer fieldNumber, Integer fieldIndex) {
    ProtoPathWrapper currentPath =
        activePaths.isEmpty() ? ProtoPathWrapper.EMPTY_PATH : activePaths.peek();
    activePaths.push(ProtoHelpers.buildPath(currentPath, fieldNumber, fieldIndex));
    if (elementToOriginalPath.containsKey(element)) {
      throw new RuntimeException(
          String.format(
              "Internal error - pushChildPath() called multiple times for same proto element %s",
              ProtoHelpers.getName(element)));
    }

    // Remember this element->path mapping.
    elementToOriginalPath.put(element, activePaths.peek());
  }

  public void popChildPath() {
    activePaths.pop();
  }

  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("class", node().getClass())
        .add("name", ProtoHelpers.getName(node()))
        .add("delete", toBeDeleted())
        .add("modifiedSCI", modifiedSourceCodeInfo)
        .add("activePaths", activePaths)
        .add("pathToLocation", pathToLocation)
        .add("elementToOriginalPath", elementToOriginalPath)
        .toString();
  }

  private void setupPathsForFile(FileDescriptorProto.Builder file) {
    // Populate location map
    if (file.hasSourceCodeInfo() && manageSourceCodeInfo) {
      for (SourceCodeInfo.Location location : file.getSourceCodeInfo().getLocationList()) {
        pathToLocation.addDataElement(new ProtoPathWrapper(location.getPathList()), location);
      }
    } else {
      // Turn off SourceCodeInfo management if there is none.
      manageSourceCodeInfo = false;
    }
  }

  private void resetPathsForFile(FileDescriptorProto.Builder file) {
    if (modifiedSourceCodeInfo) {
      SourceCodeInfo.Builder sourceCodeInfo = file.getSourceCodeInfoBuilder();
      sourceCodeInfo.clearLocation();

      new LocationInfoUpdater(sourceCodeInfo).visit(pathToLocation);

      modifiedSourceCodeInfo = false;
    }

    elementToOriginalPath.clear();
    pathToLocation.clear();
  }

  private class LocationInfoUpdater extends ProtoPathTree.Visitor {
    // Track the number of deleted children at each level in the current path through the tree.
    private Stack<Integer> numDeletedChildren = new Stack<Integer>();
    private SourceCodeInfo.Builder sourceCodeInfo;

    public LocationInfoUpdater(SourceCodeInfo.Builder sourceCodeInfo) {
      this.sourceCodeInfo = sourceCodeInfo;
    }

    @VisitsBefore
    public boolean before(ProtoPathTree<SourceCodeInfo.Location> node) {
      if (node.isRootNode()) {
        numDeletedChildren.push(0);
        // Since the root node isn't the child of anything and there's no location info there, we're
        // done - so just return here.
        return true;
      } else if (!node.isMarkedForDeletion()) {
        // NOTE: This logic for computing the new path assumes that the only nodes that we delete
        // are described by path lists ending with a 0..n index (e.g. the 2nd method of a service
        // would have a path ending in 1 and the 3rd field of a message would have a path ending in
        // 2, regardless of its defined field number). It also assumes that ProtoPathTree.Visitor is
        // going to return the children in ascending numerical order based on their path indexes.
        ProtoPathWrapper originalPath = node.getPathFromRoot();
        if (!originalPath.isEmpty()) {
          ArrayList<Integer> newPath = new ArrayList<>(originalPath.getDepth());
          for (int i = 0; i < originalPath.getDepth(); i++) {
            newPath.add(originalPath.getPathElement(i) - numDeletedChildren.get(i));
          }
          addUpdatedLocationInfo(sourceCodeInfo, node, new ProtoPathWrapper(newPath));
        }
        numDeletedChildren.push(0);
      } else {
        // Don't descend into a deleted node, but note that we found a deleted node, so we can
        // adjust path indexes for later children.
        numDeletedChildren.push(numDeletedChildren.pop() + 1);
        return false;
      }
      return true;
    }

    @VisitsAfter
    public void after(ProtoPathTree<SourceCodeInfo.Location> node) {
      if (!node.isRootNode()) {
        numDeletedChildren.pop();
      }
    }
  }

  private void addUpdatedLocationInfo(
      SourceCodeInfo.Builder sourceCodeInfo,
      ProtoPathTree<SourceCodeInfo.Location> currentPathTree,
      ProtoPathWrapper newPath) {
    // Add the location data from the current node, updating path numbers as we go.
    for (SourceCodeInfo.Location location : currentPathTree.getDataElements()) {
      sourceCodeInfo.addLocation(
          location.toBuilder().clearPath().addAllPath(newPath.getPathElements()).build());
    }
  }
}
