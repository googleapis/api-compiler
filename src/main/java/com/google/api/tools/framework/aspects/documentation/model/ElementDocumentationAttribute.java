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
 * An attribute attached by this aspect to elements, representing resolved documentation.
 * For the model itself, this attribute represents the overview documentation. For
 * protocol elements, this represents the documentation of the element, merged from
 * proto comments and doc provided via the service config.
 */
@AutoValue
public abstract class ElementDocumentationAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<ElementDocumentationAttribute> KEY =
      Key.get(ElementDocumentationAttribute.class);

  /**
   * The processed documentation of the proto element, with documentation directives resolved.
   * Note that comment filtering is not applied to this text; use
   * {@link DocumentationUtil#getScopedDescription} for that.
   */
  public abstract String documentation();

  /**
   * The deprecation documentation of the proto element, if present.
   */
  public abstract String deprecationDescription();

  /**
   * Create attribute.
   */
  public static ElementDocumentationAttribute create(String doc) {
    return create(doc, "");
  }

  public static ElementDocumentationAttribute create(String doc, String deprecationDesc) {
    return new AutoValue_ElementDocumentationAttribute(doc.trim(), deprecationDesc.trim());
  }
}
