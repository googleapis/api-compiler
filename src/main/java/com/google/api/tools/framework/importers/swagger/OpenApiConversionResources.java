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

import com.google.api.tools.framework.importers.swagger.aspects.AspectBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.HttpRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.ProtoApiFromOpenApi;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.auth.AuthRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.authz.AuthzBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.backend.BackendBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.endpoint.EndpointBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.quota.MetricRuleGenerator;
import com.google.api.tools.framework.importers.swagger.aspects.quota.QuotaBuilder;
import com.google.api.tools.framework.importers.swagger.aspects.type.TypeBuilder;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import io.swagger.models.Swagger;
import java.util.List;

/** Conversion resources for a single swagger file. */
@AutoValue
public abstract class OpenApiConversionResources {

  public abstract List<AspectBuilder> aspectBuilders();

  public abstract ProtoApiFromOpenApi apiFromSwagger();

  public abstract OpenApiImporterDiagCollector diagCollector();

  public static OpenApiConversionResources create(
      Swagger swagger, String filename, String methodNamespace, String typeNamespace) {
    OpenApiImporterDiagCollector diagCollector = new OpenApiImporterDiagCollector(filename);
    TypeBuilder typeBuilder = new TypeBuilder(swagger, typeNamespace, diagCollector);
    HttpRuleGenerator httpRuleGenerator =
        new HttpRuleGenerator(methodNamespace, swagger.getBasePath(), diagCollector);
    AuthRuleGenerator authRuleGenerator = new AuthRuleGenerator(methodNamespace, diagCollector);
    AuthBuilder authBuilder = new AuthBuilder(methodNamespace, diagCollector, authRuleGenerator);
    MetricRuleGenerator metricRuleGenerator =
        new MetricRuleGenerator(methodNamespace, diagCollector);
    QuotaBuilder quotaBuilder = new QuotaBuilder(diagCollector);
    EndpointBuilder endpointBuilder = new EndpointBuilder(diagCollector);
    AuthzBuilder authzBuilder = new AuthzBuilder(diagCollector);
    BackendBuilder backendBuilder = new BackendBuilder(methodNamespace, diagCollector);
    ProtoApiFromOpenApi apiBuilder =
        new ProtoApiFromOpenApi(
            diagCollector,
            typeBuilder,
            filename,
            methodNamespace,
            httpRuleGenerator,
            authRuleGenerator,
            metricRuleGenerator,
            authBuilder);
    final List<AspectBuilder> aspectBuilders = Lists.newArrayList(
        typeBuilder, authBuilder, backendBuilder, endpointBuilder, quotaBuilder, authzBuilder);
    return new AutoValue_OpenApiConversionResources(aspectBuilders, apiBuilder, diagCollector);
  }
}
