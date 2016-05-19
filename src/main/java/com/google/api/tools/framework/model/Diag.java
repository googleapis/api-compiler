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

package com.google.api.tools.framework.model;

import com.google.auto.value.AutoValue;

/**
 * An object representing a diagnostic message (error, warning, hint).
 */
@AutoValue
public abstract class Diag {

  /**
   * Creates an error diagnosis.
   */
  public static Diag error(Location location, String message, Object... args) {
    return create(location, message, Kind.ERROR, args);
  }

  /**
   * Creates a warning diagnosis.
   */
  public static Diag warning(Location location, String message, Object... args) {
    return create(location, message, Kind.WARNING, args);
  }

  public static Diag create(Location location, String message, Kind kind, Object... args) {
    return new AutoValue_Diag(kind, location, String.format(message, args));
  }

  /**
   * Represents diagnosis kind.
   */
  public enum Kind {
    WARNING,
    ERROR,
  }

  /**
   * @return the kind
   */
  public abstract Kind getKind();

  /**
   * @return the location
   */
  public abstract Location getLocation();

  /**
   * @return the message
   */
  public abstract String getMessage();

  @Override
  public String toString() {
    return String.format("%s: %s: %s",
        getKind().toString(),
        getLocation().getDisplayString(),
        getMessage());
  }
}
