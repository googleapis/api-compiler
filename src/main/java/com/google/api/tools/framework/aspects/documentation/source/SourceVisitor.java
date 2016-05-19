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

import com.google.api.tools.framework.util.Accepts;
import com.google.api.tools.framework.util.GenericVisitor;

/**
 * Base visitor which defines the logic to visit {@link SourceElement}.
 */
public abstract class SourceVisitor extends GenericVisitor<SourceElement> {

  protected SourceVisitor() {
    super(SourceElement.class);
  }

  @Accepts
  public void accept(SourceRoot root) {
    for (ContentElement content : root.getTopLevelContents()) {
      visit(content);
    }
    for (SourceSection section : root.getSections()) {
      visit(section);
    }
  }

  @Accepts
  public void accept(SourceSection section) {
    visit(section.getHeader());
    for (ContentElement content : section.getContents()) {
      visit(content);
    }
  }
}
