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

import com.google.api.tools.framework.model.Interface;
import com.google.auto.value.AutoValue;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.protobuf.Mixin;

import java.util.List;

/**
 * Attribute attached to each interface which implements meta API interfaces. Contains the list of
 * mixins declared for this interface.
 */
@AutoValue
public abstract class MixinAttribute {

  public static final Key<List<MixinAttribute>> KEY =
      Key.get(new TypeLiteral<List<MixinAttribute>>(){});

  /**
   * The interface being mixed-in.
   */
  public abstract Interface iface();

  /**
   * The associated mixin configuration.
   */
  public abstract Mixin config();

  public static MixinAttribute create(Interface iface, Mixin config) {
    return new AutoValue_MixinAttribute(iface, config);
  }
}