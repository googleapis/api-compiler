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

package com.google.api.tools.framework.snippet;

import com.google.auto.value.AutoValue;

/**
 * Represents a source location.
 */
@AutoValue
public abstract class Location {

  static final Location UNUSED = create("unused", 1);
  static final Location TOP_LEVEL = create("top level", 1);

  /**
   * The name of the input. This is a ':'-separated list of file names,
   * where the last component represents the actual input, and the preceding
   * ones from where this input was included.
   */
  public abstract String inputName();
  public abstract int lineNo();

  static Location create(String inputName, int lineNo) {
    return new AutoValue_Location(inputName, lineNo);
  }

  /**
   * Returns the base input name, without the inclusion context.
   */
  public String baseInputName() {
    String name = inputName();
    int i = name.lastIndexOf(':');
    if (i >= 0) {
      return name.substring(i + 1);
    }
    return name;
  }
}
