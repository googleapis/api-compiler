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

package com.google.api.tools.framework.model.stages;

import com.google.inject.Key;

/**
 * A marker class representing the stage that type resolution and basic consistency checking has
 * been performed on the api.
 *
 * <p>
 * See the usage of {@code Requires(Resolved.class)} in the model for information that is dependent
 * on this stage.
 */
public class Resolved {

  /**
   * The key representing the resolved stage.
   */
  public static final Key<Resolved> KEY = Key.get(Resolved.class);
}
