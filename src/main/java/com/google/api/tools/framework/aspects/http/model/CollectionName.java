/*
 * Copyright 2017 Google, Inc.
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

package com.google.api.tools.framework.aspects.http.model;

import com.google.auto.value.AutoValue;

/**
 * Represents a full REST collection name, consisting of a base name for the collection and the
 * REST version it is used with.
 */
@AutoValue
public abstract class CollectionName {
  public static CollectionName create(String baseName, String version) {
    return new AutoValue_CollectionName(baseName, version);
  }

  public abstract String baseName();

  public abstract String version();
}
