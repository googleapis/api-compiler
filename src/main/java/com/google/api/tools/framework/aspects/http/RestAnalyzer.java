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

package com.google.api.tools.framework.aspects.http;

import com.google.api.tools.framework.aspects.documentation.model.ResourceAttribute;
import com.google.api.tools.framework.aspects.http.RestPatterns.MethodPattern;
import com.google.api.tools.framework.aspects.http.model.CollectionAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.http.model.RestKind;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rest analyzer. Determines {@link RestMethod} associated with method and http binding. Also
 * computes collections and estimates their resource types.
 *
 * <p>Rest conformance violating warnings are reported in the lint phase, this module will never
 * produce errors or warnings and always returns well-defined output for each method.
 */
public class RestAnalyzer {
  private final HttpConfigAspect aspect;
  private final Map<String, CollectionAttribute> collectionMap = new TreeMap<>();

  /**
   * Creates a rest analyzer which reports errors via the given aspect.
   */
  RestAnalyzer(HttpConfigAspect aspect) {
    this.aspect = aspect;
  }

  /**
   * Finalizes rest analysis, delivering the collections used.
   */
  List<CollectionAttribute> finalizeAndGetCollections() {
    // Compute the resource types for each collection. We need to have all collections fully
    // built before this can be done.
    //
    // In the first pass, we walk over all messages and collect information from the
    // resource attribute as derived from a doc instruction. In the second pass, for those
    // collections which still have no resource, we run a heuristic to identify the resource.
    Map<String, TypeRef> definedResources = Maps.newLinkedHashMap();

    for (TypeRef type : aspect.getModel().getSymbolTable().getDeclaredTypes()) {
      if (!type.isMessage()) {
        continue;
      }
      MessageType message = type.getMessageType();
      List<ResourceAttribute> definitions = message.getAttribute(ResourceAttribute.KEY);
      if (definitions != null) {
        for (ResourceAttribute definition : definitions) {
          definedResources.put(definition.collection(), type);
        }
      }
    }

    ImmutableList.Builder<CollectionAttribute> result = ImmutableList.builder();
    for (CollectionAttribute collection : collectionMap.values()) {
      TypeRef type = definedResources.get(collection.getFullName());
      if (type == null) {
        // No defined resource association, run heuristics.
        type = new ResourceTypeSelector(aspect.getModel(),
            collection.getMethods()).getCandiateResourceType();
      }
      collection.setResourceType(type);
      result.add(collection);
    }
    return result.build();
  }

  /**
   * Analyzes the given method and http config and returns a rest method.
   */
  RestMethod analyzeMethod(Method method, HttpAttribute httpConfig) {
    // First check whether this is a special method.
    RestMethod restMethod = createSpecialMethod(method, httpConfig);

    if (restMethod == null) {
      // Search for the first matching method pattern.
      MethodMatcher matcher = null;
      for (MethodPattern pattern : RestPatterns.METHOD_PATTERNS) {
        matcher = new MethodMatcher(pattern, method, httpConfig);
        if (matcher.matches()) {
          break;
        }
        matcher = null;
      }
      if (matcher != null) {
        restMethod = matcher.createRestMethod();
      } else {
        restMethod = createCustomMethod(method, httpConfig, "");
        restMethod.setHasValidRestPattern(false);
      }
    }

    // Add method to collection.
    String collectionName = restMethod.getRestCollectionName();
    CollectionAttribute collection = collectionMap.get(collectionName);
    if (collection == null) {
      collection = new CollectionAttribute(aspect.getModel(), collectionName);
      collectionMap.put(collectionName, collection);
    }
    collection.addMethod(restMethod);
    return restMethod;
  }

  // Determines whether to create a special rest method. Returns null if no special rest method.
  private RestMethod createSpecialMethod(Method method, HttpAttribute httpConfig) {
    if (httpConfig.getMethodKind() == MethodKind.NONE) {
      // Not an HTTP method. Create a dummy rest method.
      return RestMethod.create(method, RestKind.CUSTOM, "", method.getFullName());
    }
    return null;
  }

  // Create a custom rest method. If the last path segment is a literal, it will be used
  // as the verb for the custom method, otherwise the custom prefix or the rpc's name.
  static RestMethod createCustomMethod(
      Method method, HttpAttribute httpConfig, String customNamePrefix) {
    ImmutableList<PathSegment> path = httpConfig.getFlatPath();
    PathSegment lastSegment = path.get(path.size() - 1);

    // Determine base name.
    String customName = "";
    if (lastSegment instanceof LiteralSegment) {
      customName = ((LiteralSegment) lastSegment).getLiteral();
      path = path.subList(0, path.size() - 1);
    } else {
      if (method.getModel().getConfigVersion() > 1) {
        // From version 2 on, we generate a meaningful name here.
        customName = method.getSimpleName();
      } else if (customNamePrefix.isEmpty()){
        // Older versions use the prefix or derive from the http method.
        customName = httpConfig.getMethodKind().toString().toLowerCase();
      }
    }

    // Prepend prefix.
    if (!customNamePrefix.isEmpty()
        && !customName.toLowerCase().startsWith(customNamePrefix.toLowerCase())) {
      customName = customNamePrefix + ensureUpperCase(customName);
    }

    // Ensure effective start is lower case.
    customName = ensureLowerCase(customName);

    return RestMethod.create(method, RestKind.CUSTOM, buildCollectionName(path), customName);
  }

  private static String ensureUpperCase(String name) {
    if (!name.isEmpty() && Character.isLowerCase(name.charAt(0))) {
      return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  private static String ensureLowerCase(String name) {
    if (!name.isEmpty() && Character.isUpperCase(name.charAt(0))) {
      return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
    return name;
  }

  // Builds the collection name from a path.
  static String buildCollectionName(Iterable<PathSegment> segments) {
    return Joiner.on('.').skipNulls().join(FluentIterable.from(segments).transform(
        new Function<PathSegment, String>() {
          @Override
          public String apply(PathSegment segm) {
            if (!(segm instanceof LiteralSegment)) {
              return null;
            }
            LiteralSegment literal = (LiteralSegment) segm;
            if (literal.isTrailingCustomVerb()) {
              return null;
            }
            return literal.getLiteral();
          }
        }));
  }
}
