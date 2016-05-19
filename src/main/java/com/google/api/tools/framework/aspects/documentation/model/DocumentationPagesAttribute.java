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

package com.google.api.tools.framework.aspects.documentation.model;

import com.google.api.Page;
import com.google.auto.value.AutoValue;
import com.google.inject.Key;

import java.util.List;

/**
 * An attribute attached to the model to represent normalized documentation pages specified through
 * the documentation configuration.
 */
@AutoValue
public abstract class DocumentationPagesAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<DocumentationPagesAttribute> KEY =
      Key.get(DocumentationPagesAttribute.class);

  /**
   * Returns the top level pages of the docset.
   * Note that comment filtering is not applied to this text; use
   * {@link DocumentationUtil#getScopedToplevelPages} for that.
   */
  public abstract List<Page> toplevelPages();

  /**
   * Create this attribute.
   */
  public static DocumentationPagesAttribute create(List<Page> toplevelPages) {
    return new AutoValue_DocumentationPagesAttribute(toplevelPages);
  }
}
