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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.tools.framework.model.SimpleLocation;
import io.swagger.models.parameters.Parameter;

/** Utilities for creating {@link SimpleLocation}s from Swagger parameters. */
public class SwaggerLocations {

  public static SimpleLocation createParameterLocation(
      Parameter parameter, String operationType, String path) {
    return new SimpleLocation(
        String.format(
            "Parameter '%s' in operation '%s' in path '%s'",
            parameter.getName(), operationType, path));
  }

  public static SimpleLocation createOperationLocation(String operationType, String path) {
    return new SimpleLocation(String.format("Operation '%s' in path '%s'", operationType, path));
  }
}
