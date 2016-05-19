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

import java.util.List;

/**
 * An interface representing an object which collects diagnostics.
 */
public interface DiagCollector {

  /**
   * Adds a diagnosis.
   */
  void addDiag(Diag diag);

  /**
   * Returns the number of diagnosed proper errors.
   */
  int getErrorCount();

  /**
   * Returns true if there are any diagnosed proper errors; false otherwise.
   */
  boolean hasErrors();

  /**
   * Returns a collection of diagnostics in the order they were added.
   *
   * Can throw a RuntimeException if the implementation doesn't retain them (e.g. if it immediately
   * prints them out to a stream).
   */
  List<Diag> getDiags();
}
