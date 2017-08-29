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

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;

/**
 * Represents section in Markdown content based on Markdown header boundary.
 */
public class SourceSection extends SourceElement {

  private final SectionHeader header;
  private final List<ContentElement> contents = Lists.newArrayList();

  public SourceSection(SectionHeader header, int startIndex, int endIndex) {
    super(startIndex, endIndex);
    this.header = header;
  }

  /**
   * Adds content to the section.
   */
  public void addContents(Collection<ContentElement> contents) {
    this.contents.addAll(contents);
  }

  /**
   * Returns header of the section.
   */
  public SectionHeader getHeader() {
    return header;
  }

  /**
   * Returns contents of the section.
   */
  public Iterable<ContentElement> getContents() {
    return contents;
  }
}
