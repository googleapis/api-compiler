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
import com.google.inject.TypeLiteral;

import java.util.List;

/**
 * Attribute indicating a user defined resource-to-collection association. A message type
 * can contain a list of such attributes. Used to override the default heuristic for
 * determining the resource of a collection.
 */
@AutoValue
public abstract class ResourceAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<List<ResourceAttribute>> KEY =
      Key.get(new TypeLiteral<List<ResourceAttribute>>() {});

  /**
   * The collection the attributed message type is a resource for.
   */
  public abstract String collection();

  /**
   * Create attribute.
   */
  public static ResourceAttribute create(String collection) {
    return new AutoValue_ResourceAttribute(collection);
  }
}
