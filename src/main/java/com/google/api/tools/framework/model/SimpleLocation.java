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

import com.google.common.base.Preconditions;
import com.google.protobuf.DescriptorProtos;

/**
 * A simple location with a directly specified display string.
 */
public class SimpleLocation implements Location {

  public static final Location TOPLEVEL = new SimpleLocation("toplevel");
  public static final Location UNKNOWN = new SimpleLocation("unknown location");

  /**
   * <p>Create new instance of {@link SimpleLocation} by converting from instance of
   * {@link com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location} </p>
   *
   * <p>The Display String structure is "proto name:line number:column number". </p>
   *
   * @param location Instance {@link com.google.protobuf.DescriptorProtos.SourceCodeInfo.Location}
   * @param element ProtoElement associated with the given location.
   */
  public static Location convertFrom(final DescriptorProtos.SourceCodeInfo.Location location,
      final ProtoElement element) {
    if (location == null) {
      return UNKNOWN;
    }
    return new Location() {
      @Override
      public String getDisplayString() {
        return String.format("%s:%d:%d", element.getFile().getLocation().getDisplayString(),
            location.getSpan(0) + 1, location.getSpan(1) + 1);
      }
    };
  }

  private final String displayString;

  /**
   * Creates a simple location.
   */
  public SimpleLocation(String displayString) {
    super();
    this.displayString = Preconditions.checkNotNull(displayString);
  }

  @Override
  public String getDisplayString() {
    return displayString;
  }

  @Override
  public String toString() {
    return displayString;
  }

  @Override
  public int hashCode() {
    return displayString.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof SimpleLocation
        && ((SimpleLocation) obj).displayString.equals(displayString);
  }
}
