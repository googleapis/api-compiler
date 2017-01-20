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

package com.google.api.tools.framework.aspects.endpoint.model;

import com.google.api.Endpoint;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.api.tools.framework.model.Scoper;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Utilities for working with multiple endpoints.
 */
public class EndpointUtil {
  /**
   * A key that references the primary endpoint name which is used for scoping the model.
   */
  public static final Key<String> ENDPOINT_FILTER_KEY =
      Key.get(String.class, Names.named("endpoint"));

  /**
   * A key to access normalized endpoint config as attached to model.
   */
  public static final Key<List<Endpoint>> ENDPOINTS_KEY =
      Key.get(new TypeLiteral<List<Endpoint>>() {});

  /**
   * Scopes down a model based on a specified endpoint name.
   */
  public static void scopeModel(final Model model, @Nullable String endpoint) {
    model.establishStage(Merged.KEY);
    endpoint = Strings.isNullOrEmpty(endpoint) ? model.getServiceConfig().getName() : endpoint;
    if (endpoint == null) {
      // If the endpoint is still null, that means the service config has no name, which means it
      // is a test config, and we should do nothing.
      return;
    }
    validateEndpoint(model, endpoint);
    model.putAttribute(ENDPOINT_FILTER_KEY, endpoint);
    model.setScoper(model.getScoper().restrict(new Predicate<ProtoElement>() {
      @Override
      public boolean apply(ProtoElement element) {
        return inEndpoint(model, element);
      }
    }, ""));
  }

  public static <A> A withScopedModel(
      final Model model, @Nullable String endpoint, Supplier<A> action) {

    Scoper savedScoper = model.getScoper();
    String savedEndpoint = model.getAttribute(ENDPOINT_FILTER_KEY);
    try {
      scopeModel(model, endpoint);
      return action.get();
    } finally {
      model.setScoper(savedScoper);
      if (savedEndpoint != null) {
        model.putAttribute(ENDPOINT_FILTER_KEY, savedEndpoint);
      }
    }
  }

  private static boolean inEndpoint(Model model, ProtoElement element) {
    String endpoint = model.getAttribute(ENDPOINT_FILTER_KEY);
    if (element instanceof ProtoFile) {
      return true;
    } else if (element instanceof Interface) {
      Endpoint endpointConfig = getEndpointConfig(model, endpoint);
      if (endpointConfig != null) {
        for (String api : endpointConfig.getApisList()) {
          if (api.equals(element.getFullName())) {
            return true;
          }
        }
      }
      return false;
    } else {
      return inEndpoint(model, element.getParent());
    }
  }

  private static void validateEndpoint(Model model, final String endpoint) {
    if (getEndpointConfig(model, endpoint) == null) {
      model.getDiagCollector().addDiag(Diag.error(SimpleLocation.TOPLEVEL,
          "endpoint: Primary endpoint name '%s' does not exist in the config.", endpoint));
    }
  }

  private static Endpoint getEndpointConfig(Model model, final String endpoint) {
    List<Endpoint> endpointConfigs = model.getAttribute(ENDPOINTS_KEY);
    return Iterables.tryFind(endpointConfigs, new Predicate<Endpoint>() {
      @Override public boolean apply(Endpoint input) {
        return endpoint.equals(input.getName());
      }
    }).orNull();
  }
}
