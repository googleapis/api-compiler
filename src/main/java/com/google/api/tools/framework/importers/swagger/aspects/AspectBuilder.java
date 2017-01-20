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

package com.google.api.tools.framework.importers.swagger.aspects;

import com.google.api.Service;
import io.swagger.models.Swagger;

/**
 * Interface for individual aspects that build out a piece of an {@link Service} based on fields in
 * an {@link Swagger} object.
 */
public interface AspectBuilder {

  /** Set fields in the associated {@link Service} object based on the input {@link Swagger} */
  public void addFromSwagger(Service.Builder serviceBuilder, Swagger swagger);
}
