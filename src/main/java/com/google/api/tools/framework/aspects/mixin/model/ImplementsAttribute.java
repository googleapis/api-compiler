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

package com.google.api.tools.framework.aspects.mixin.model;

import com.google.api.tools.framework.model.Method;
import com.google.auto.value.AutoValue;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

import java.util.List;

/**
 * Attribute attached to each method which implements a meta API method. Contains the list of meta
 * API methods implemented by this method.
 */
@AutoValue
public abstract class ImplementsAttribute {

  public static final Key<List<ImplementsAttribute>> KEY =
      Key.get(new TypeLiteral<List<ImplementsAttribute>>() {});

  /**
   * The implemented meta API method.
   */
  public abstract Method method();

  public static ImplementsAttribute create(Method method) {
    return new AutoValue_ImplementsAttribute(method);
  }
}