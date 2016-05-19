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

import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;

/**
 * Represent the header of a section.
 */
public class SectionHeader extends SourceElement {

  private final int level;
  private final String text;

  public SectionHeader(int level, String text, int startIndex, int endIndex,
      DiagCollector diagCollector, Location sourceLocation) {
    super(startIndex, endIndex, diagCollector, sourceLocation);
    this.level = level;
    this.text = text;
  }

  /**
   * Returns the heading level.
   */
  public int getLevel() {
    return level;
  }

  /**
   * Returns the header text.
   */
  public String getText() {
    return text;
  }
}
