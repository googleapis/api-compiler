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
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;

/**
 * Root of the Markdown source structure based on Markdown heading levels.
 * Header and associated contents compose a {@link SourceSection}. A document
 * root can contain top level contents that are not enclosed by any headers. For example:
 * <pre>
 *   Top Level content.
 *   # Header1
 * </pre>
 */
public class SourceRoot extends SourceElement {

  private final List<ContentElement> topLevelContents = Lists.newArrayList();
  private final List<SourceSection> sections = Lists.newArrayList();

  public SourceRoot(int startIndex, int endIndex, DiagCollector diagCollector,
      Location sourceLocation) {
    super(startIndex, endIndex, diagCollector, sourceLocation);
  }

  /**
   * Add top level contents to the document.
   */
  public void addTopLevelContents(Collection<ContentElement> contents) {
    topLevelContents.addAll(contents);
  }

  /**
   * Add a section to the document.
   */
  public void addSection(SourceSection section) {
    sections.add(section);
  }

  /**
   * Returns top level contents of the document.
   */
  public Iterable<ContentElement> getTopLevelContents() {
    return topLevelContents;
  }

  /**
   * Returns sections of the document.
   */
  public Iterable<SourceSection> getSections() {
    return sections;
  }
}
