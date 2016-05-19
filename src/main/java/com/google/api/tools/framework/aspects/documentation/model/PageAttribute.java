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

import com.google.auto.value.AutoValue;
import com.google.inject.Key;

/**
 * Attribute for an associated documentation page. The effective page for an element
 * is either directly attached to the element, or to the enclosing proto file.
 */
@AutoValue
public abstract class PageAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<PageAttribute> KEY = Key.get(PageAttribute.class);

  /**
   * The name of the page this element is associated with.
   */
  public abstract String page();

  /**
   * Create attribute.
   */
  public static PageAttribute create(String doc) {
    return new AutoValue_PageAttribute(doc);
  }
}
