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

package com.google.api.tools.framework.importers.swagger.merger;

import com.google.api.Service;
import com.google.api.tools.framework.importers.swagger.MultiSwaggerParser.SwaggerFile;
import java.util.List;

/** Merger for converted Swagger to {@link Service} objects. */
public class ServiceMerger {

  /** Merges multiple built {@link SwaggerFile}s into a single {@link Service} */
  public Service merge(List<Service.Builder> serviceBuliders) {
    Service.Builder serviceBuilder = serviceBuliders.get(0);
    serviceBuilder.addAllTypes(TypesBuilderFromDescriptor.createAdditionalServiceTypes());
    serviceBuilder.addAllEnums(TypesBuilderFromDescriptor.createAdditionalServiceEnums());
    // Currently all SwaggerFiles use the same Service.proto to build, so we just pull the first one
    // TODO (adwright): Separate this to build multiple service.proto and have a merging phase.
    return serviceBuilder.build();
  }
}
