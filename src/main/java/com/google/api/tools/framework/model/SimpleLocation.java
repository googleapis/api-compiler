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

/**
 * A simple location with a directly specified display string.
 */
public class SimpleLocation implements Location {

  public static final Location TOPLEVEL = new SimpleLocation("toplevel");
  public static final Location UNKNOWN = new SimpleLocation("unknown location");

  private final String displayString;
  private final String containerName;

  /**
   * Creates a simple location with just the display string. {@link SimpleLocation#containerName}
   * is set to displayString.
   */
  public SimpleLocation(String displayString) {
    super();
    this.displayString = Preconditions.checkNotNull(displayString);
    this.containerName = displayString;
  }

  /**
   * Creates a simple location with display string and the container name.
   */
  public SimpleLocation(String displayString, String containerName) {
    super();
    this.displayString = Preconditions.checkNotNull(displayString);
    this.containerName = Preconditions.checkNotNull(containerName);
  }

  @Override
  public String getDisplayString() {
    return displayString;
  }

  @Override
  public String getContainerName() {
    return containerName;
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
