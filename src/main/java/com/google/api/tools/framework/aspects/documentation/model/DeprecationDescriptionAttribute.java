/*
 * Copyright (C) 2016 Google, Inc.
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
 * An attribute attached by this aspect to elements, representing resolved deprecation description
 * documentation. For protocol elements, this represents the deprecation documentation of the
 * element, merged from proto comments and doc provided via the service config. It is only valid
 * to attach deprecation documentation to an element if its `deprecated` option is set to `true`.
 */
@AutoValue
public abstract class DeprecationDescriptionAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<DeprecationDescriptionAttribute> KEY =
      Key.get(DeprecationDescriptionAttribute.class);

  /**
   * The content of the deprecation_description this element is associated with.
   */
  public abstract String deprecationDescription();

  /**
   * Create attribute.
   */
  public static DeprecationDescriptionAttribute create(String doc) {
    return new AutoValue_DeprecationDescriptionAttribute(doc);
  }
}
