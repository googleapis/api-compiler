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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;

/**
 * Represent an abstract element of Markdown source.
 */
abstract class SourceElement {

  private final int startIndex;
  private final int endIndex;
  private final DiagCollector diagCollector;
  private final Location sourceLocation;

  public SourceElement(int startIndex, int endIndex, DiagCollector diagCollector,
      Location sourceLocation) {
    this.startIndex = startIndex;
    this.endIndex = endIndex;
    this.diagCollector = diagCollector;
    this.sourceLocation = sourceLocation;
  }

  /**
   * The start index (inclusive) of the content the node represents from the source.
   */
  public int getStartIndex() {
    return startIndex;
  }

  /**
   * The end index (exclusive) of the content the node represents from the source.
   */
  public int getEndIndex() {
    return endIndex;
  }

  /**
   * Reports error message.
   */
  public void error(String message, Object... params) {
    diagCollector.addDiag(Diag.error(sourceLocation, message, params));
  }
}
