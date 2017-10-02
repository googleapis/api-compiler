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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Thin wrapper around the collection of integers that forms a "path" as defined by the
 * SourceCodeInfo.Location.path field in a FileDescriptor.
 */
public class ProtoPathWrapper {
  public static final ProtoPathWrapper EMPTY_PATH = new ProtoPathWrapper();

  private final LinkedList<Integer> path;

  public ProtoPathWrapper(Integer... pathElements) {
    this(Arrays.asList(pathElements));
  }

  public ProtoPathWrapper(Collection<Integer> pathElements) {
    this.path = new LinkedList<>(pathElements);
  }

  public ProtoPathWrapper(ProtoPathWrapper parentPath, Integer... pathElements) {
    this.path = new LinkedList<>(parentPath.path);
    this.path.addAll(Arrays.asList(pathElements));
  }

  public ProtoPathWrapper getParentPath() {
    return new ProtoPathWrapper(path.subList(0, path.size() - 1));
  }

  public Integer getPathElement(int index) {
    return path.get(index);
  }

  public ImmutableList<Integer> getPathElements() {
    return ImmutableList.copyOf(path);
  }

  public boolean isEmpty() {
    return path.isEmpty();
  }

  public int getDepth() {
    return path.size();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("path", path).toString();
  }

  @Override
  public int hashCode() {
    return Objects.hash(path);
  }

  @Override
  public boolean equals(Object obj) {
    return (this == obj)
        || (obj != null
            && obj instanceof ProtoPathWrapper
            && path.equals(((ProtoPathWrapper) obj).path));
  }
}
