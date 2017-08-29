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

package com.google.api.tools.framework.aspects.documentation.source;

/** Represent an abstract element of Markdown source. */
abstract class SourceElement {

  private final int startIndex;
  private final int endIndex;

  public SourceElement(int startIndex, int endIndex) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;
  }

  /** The start index (inclusive) of the content the node represents from the source. */
  public int getStartIndex() {
    return startIndex;
  }

  /** The end index (exclusive) of the content the node represents from the source. */
  public int getEndIndex() {
    return endIndex;
  }
}
