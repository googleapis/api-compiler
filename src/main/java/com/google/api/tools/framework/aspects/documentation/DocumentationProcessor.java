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

package com.google.api.tools.framework.aspects.documentation;

import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.api.tools.framework.model.Element;

/**
 * Interface defines processor that processes documentation source.
 */
interface DocumentationProcessor {

  /**
   * Runs the processor for the given source. Returns the processed source string if no error found.
   * Otherwise, the original string should be returned.
   *
   * @param source the documentation source to be processed.
   * @param sourceLocation the location of the documentation source.
   * @param element the element for which the documentation source is being processed
   */
  String process(String source, LocationContext sourceLocation, Element element);
}
