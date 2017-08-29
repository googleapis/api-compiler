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

package com.google.api.tools.framework.aspects.versioning.model;

import com.google.api.tools.framework.model.Model;
import com.google.inject.Key;

import java.util.Set;
import java.util.TreeSet;

/**
 * An attribute attached by this aspect to the {@link Model} representing the set of all rest
 * versions used in the API. The rest versions are determined from the path of each method.
 */
public class RestVersionsAttribute {

  public static final Key<RestVersionsAttribute> KEY = Key.get(RestVersionsAttribute.class);

  private final Set<String> versions;

  public RestVersionsAttribute(Set<String> versions) {
    // Use TreeSet so that the versions are sorted in natural ordering.
    this.versions = new TreeSet<>(versions);
  }

  public Set<String> getVersions() {
    return versions;
  }
}
