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

package com.google.api.tools.framework.aspects.endpoint;

import com.google.api.Endpoint;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.Features;
import com.google.api.tools.framework.aspects.endpoint.model.EndpointUtil;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.protobuf.Api;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration aspect for the endpoint section.
 */
public class EndpointConfigAspect extends ConfigAspectBase {

  private static final Features ENDPOINT_FEATURES = new Features(
      );

  private static final String GOOGLEAPIS_DNS_SUFFIX = ".googleapis.com";

  /**
   * Creates endpoint config aspect.
   */
  public static ConfigAspectBase create(Model model) {
    return new EndpointConfigAspect(model);
  }

  private EndpointConfigAspect(Model model) {
    super(model, "endpoint");
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  public void startMerging() {
    Service service = getModel().getServiceConfig();

    List<Endpoint> sourceEndpoints = service.getEndpointsCount() != 0
        ? service.getEndpointsList()
        : createDefaultEndpoints(service);

    checkForDuplicates(sourceEndpoints);
    List<Endpoint> processedEndpoints = populateDefaults(sourceEndpoints, service);
    getModel().putAttribute(EndpointUtil.ENDPOINTS_KEY, processedEndpoints);
  }

  private List<Endpoint> createDefaultEndpoints(Service service) {
    return !Strings.isNullOrEmpty(service.getName())
        // Add the service name as an implicit endpoint.
        ? Lists.newArrayList(Endpoint.newBuilder().setName(service.getName()).build())
        : ImmutableList.<Endpoint>of();
  }

  @Override
  public void merge(ProtoElement element) {
  }

  private List<Endpoint> populateDefaults(List<Endpoint> sourceEndpoints, Service service) {
    ImmutableList.Builder<Endpoint> results = ImmutableList.builder();
    for (Endpoint sourceEndpoint : sourceEndpoints) {
      if (!validate(sourceEndpoint)) {
        continue;
      }
      Endpoint.Builder builder = sourceEndpoint.toBuilder();
      if (sourceEndpoint.getName().endsWith(GOOGLEAPIS_DNS_SUFFIX)) {
        // Use a set to prevent duplicates (this can happen when the service config contains
        // on of the google api aliases). Use a LinkedHashSet for deterministic order that
        // can be depended on in tests.
        LinkedHashSet<String> aliases =
            Sets.newLinkedHashSet(createGoogleapisAliases(sourceEndpoint.getName()));
        aliases.addAll(builder.getAliasesList());
        builder.clearAliases();
        builder.addAllAliases(aliases);
      }
      if (sourceEndpoint.getApisCount() == 0) {
        for (Api api : service.getApisList()) {
          builder.addApis(api.getName());
        }
      }
      builder.clearFeatures();
      builder.addAllFeatures(ENDPOINT_FEATURES.evaluate(sourceEndpoint.getFeaturesList(),
          getModel().getConfigVersion(), getModel().getDiagCollector(),
          getLocationInConfig(sourceEndpoint, "name")));
      results.add(builder.build());
    }
    return results.build();
  }

  /**
   * Generates aliases for services in the googleapis.com namespace.
   */
  private Iterable<String> createGoogleapisAliases(String endpointName) {
    Preconditions.checkArgument(endpointName.endsWith(".googleapis.com"));
    String googleapisSuffix;
    String googleSuffix;
    if (endpointName.endsWith(".sandbox.googleapis.com")) {
      googleapisSuffix = ".sandbox.googleapis.com";
      googleSuffix = "-googleapis.sandbox.google.com";
    } else {
      googleapisSuffix = ".googleapis.com";
      googleSuffix = ".clients6.google.com";
    }

    String baseName = endpointName.substring(0, endpointName.length() - googleapisSuffix.length());
    ImmutableList.Builder<String> results = ImmutableList.builder();
    results.add(String.format("%s%s", baseName, googleSuffix));
    results.add(String.format("content-%s%s", baseName, googleapisSuffix));

    return results.build();
  }

  @Override
  public void startNormalization(Service.Builder builder) {
    builder.clearEndpoints();
    builder.addAllEndpoints(getModel().getAttribute(EndpointUtil.ENDPOINTS_KEY));
  }

  /**
   * Checks if any endpoint/alias occurs more than once in the service config.
   */
  private void checkForDuplicates(List<Endpoint> endpoints) {
    Set<String> unique = Sets.newHashSet();
    Set<String> dupes = Sets.newLinkedHashSet();

    for (Endpoint endpoint : endpoints) {
      String name = endpoint.getName();
      if (unique.contains(name)) {
        dupes.add(name);
      } else {
        unique.add(name);
      }
      for (String alias : endpoint.getAliasesList()) {
        if (unique.contains(alias)) {
          dupes.add(alias);
        } else {
          unique.add(alias);
        }
      }
    }

    if (dupes.size() > 0) {
      error(getModel(), "The following endpoints/aliases occur multiple times: %s.", dupes);
    }
  }

  /**
   * Validates the DNS name(s) of an endpoint.
   */
  private boolean validateDns(String dns, String errorPrefix) {
    if (Strings.isNullOrEmpty(dns)) {
      error(getModel(), "%s in service '%s' must not be empty.", errorPrefix,
          getModel().getServiceConfig().getName());
      return false;
    }
    return true;
  }

  /**
   * Validates all properties of an endpoint.
   */
  private boolean validate(Endpoint endpoint) {
    boolean result = validateDns(endpoint.getName(), "Endpoint name");
    for (String alias : endpoint.getAliasesList()) {
      result = result && validateDns(alias, "Endpoint '" + endpoint.getName() + "''s alias");
    }
    return result;
  }

}
