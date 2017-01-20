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

import com.google.api.tools.framework.importers.swagger.aspects.ApiFromSwagger;
import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.HttpRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.extensions.TopLevelExtensionsBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.type.TypeBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import io.swagger.models.Swagger;
import java.util.List;

/** Conversion resources for a single swagger file. */
@AutoValue
public abstract class SwaggerConversionResources {

  public abstract List<AspectBuilder> aspectBuilders();

  public abstract ApiFromSwagger apiFromSwagger();

  public abstract SwaggerImporterDiagCollector diagCollector();

  public static SwaggerConversionResources create(
      Swagger swagger, String filename, String methodNamespace, String typeNamespace) {
    SwaggerImporterDiagCollector diagCollector = new SwaggerImporterDiagCollector(filename);
    TypeBuilder typeBuilder = new TypeBuilder(swagger, typeNamespace);
    HttpRuleGenerator httpRuleGenerator =
        new HttpRuleGenerator(methodNamespace, swagger.getBasePath(), diagCollector);
    AuthRuleGenerator authRuleGenerator = new AuthRuleGenerator(methodNamespace, diagCollector);
    AuthBuilder authBuilder = new AuthBuilder(methodNamespace, diagCollector, authRuleGenerator);
    ApiFromSwagger apiBuilder =
        new ApiFromSwagger(
            diagCollector,
            typeBuilder,
            filename,
            methodNamespace,
            httpRuleGenerator,
            authRuleGenerator,
            authBuilder);
    TopLevelExtensionsBuilder topLevelExtensionBuilder =
        new TopLevelExtensionsBuilder(diagCollector);
    List<AspectBuilder> aspectBuilders =
        Lists.newArrayList(typeBuilder, authBuilder, topLevelExtensionBuilder);
    return new AutoValue_SwaggerConversionResources(
        aspectBuilders, apiBuilder,  diagCollector);
  }
}
