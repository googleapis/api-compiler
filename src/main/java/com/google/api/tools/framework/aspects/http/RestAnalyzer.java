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
import com.google.api.tools.framework.aspects.http.model.CollectionName;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.http.model.RestKind;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.aspects.versioning.model.ApiVersionUtil;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.Collection;
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

  /**
   * If experiment is set, the RestAnalyzer will shorten collection names using a heuristic.
   */
  private static final String SHORTEN_COLLECTION_NAMES =
      "shorten-collection-names";

  private final HttpConfigAspect aspect;
  private final Map<String, CollectionAttribute> intermediateCollectionMap = new TreeMap<>();
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

    if (aspect.getModel().getExperiments().isExperimentEnabled(SHORTEN_COLLECTION_NAMES)) {
      assignShortNames(intermediateCollectionMap.values());
    }
    for (CollectionAttribute collection : intermediateCollectionMap.values()) {
      // note: nested loop needed to reach duplicate REST methods
      for (String restMethodName : collection.getRestMethodNames()) {
        for (RestMethod restMethod : collection.getRestMethodWithDuplicates(restMethodName)) {
          addMethodToCollection(
              collectionMap, restMethod, restMethod.getBaseRestCollectionName());
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
    List<CollectionAttribute> collections = result.build();
    return collections;
  }

  /**
   * Analyzes the given method and http config and returns a rest method.
   */
  RestMethod analyzeMethod(Method method, HttpAttribute httpConfig) {
    // First check whether this is a special method.
    RestMethod restMethod = createSpecialMethod(method, httpConfig);
    String configuredCollectionName = "";
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
        restMethod = matcher.createRestMethod(configuredCollectionName);
      } else {
        restMethod = createCustomMethod(method, httpConfig, "");
        restMethod.setHasValidRestPattern(false);
      }
    }

    // Add method to collections and name-to-collection maps.
    // If name is manually configured from the HttpRule, use the final collection map so
    // that the configured name will not be shortened if the shorten-collection-names experiment is
    // enabled. Otherwise, use an intermediate map, which is potentially subject to name shortening.
    if (Strings.isNullOrEmpty(configuredCollectionName)) {
      addMethodToCollection(
          intermediateCollectionMap, restMethod, restMethod.getBaseRestCollectionName());
    } else {
      addMethodToCollection(collectionMap, restMethod, configuredCollectionName);
    }
    return restMethod;
  }

  private void addMethodToCollection(Map<String, CollectionAttribute> collectionMap,
      RestMethod restMethod, String baseCollectionName) {
    String version = restMethod.getVersion();
    String versionedCollectionName = version + "." + baseCollectionName;
    CollectionAttribute collection = collectionMap.get(versionedCollectionName);
    if (collection == null) {
      collection = new CollectionAttribute(aspect.getModel(), baseCollectionName, version);
      collectionMap.put(versionedCollectionName, collection);
    }
    collection.addMethod(restMethod);
  }

  // Determines whether to create a special rest method. Returns null if no special rest method.
  private RestMethod createSpecialMethod(Method method, HttpAttribute httpConfig) {
    String restMethodName = "";
    if (httpConfig.getMethodKind() == MethodKind.NONE) {
      // Not an HTTP method. Create a dummy rest method.
      return RestMethod.create(method, RestKind.CUSTOM, "", method.getFullName(), restMethodName);
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

    String restMethodName = "";

    CollectionName collectionName = RestAnalyzer.buildCollectionName(path, method.getModel());

    return RestMethod.create(
        method, RestKind.CUSTOM, collectionName, customName, restMethodName);
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
  static CollectionName buildCollectionName(Iterable<PathSegment> segments, Model model) {
    String version = null;

    String baseName = Joiner.on('.').skipNulls().join(FluentIterable.from(segments).transform(
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

    if (Strings.isNullOrEmpty(version)) {
      version = ApiVersionUtil.extractMajorVersionFromRestName(baseName);
      baseName = ApiVersionUtil.stripVersionFromRestName(baseName);
    }

    return CollectionName.create(baseName, version);
  }

  /**
   * Assigns this RestGroup's short names to its collections and Rest methods.
   */
  private static void assignShortNames(Collection<CollectionAttribute> collections) {
    ImmutableMap<CollectionAttribute, String> collectionshortNames =
        generateShortNames(collections);
    for (Map.Entry<CollectionAttribute, String> collectionshortName
        : collectionshortNames.entrySet()) {
      String name = collectionshortName.getValue();
      CollectionAttribute collection = collectionshortName.getKey();
      collection.setName(name);
      for (RestMethod method : collection.getMethods()) {
        method.setBaseCollectionName(name);
      }
    }
  }

  private static ImmutableMap<CollectionAttribute, String> generateShortNames(
      Collection<CollectionAttribute> collections) {
    BiMap<String, CollectionAttribute> intermediateMap = HashBiMap.create();
    for (CollectionAttribute collection : collections) {
      String baseName = collection.getBaseName();
      String shortName = baseName.substring(baseName.lastIndexOf(".") + 1);
      insertOrDisambiguate(intermediateMap, shortName, collection);
    }
    return ImmutableMap.copyOf(intermediateMap.inverse());
  }

  /**
   * Attempts to insert key-value pair into map; disambiguate if the key is already present.
   */
  private static void insertOrDisambiguate(Map<String, CollectionAttribute> map, String key,
      CollectionAttribute value) {
    CollectionAttribute oldValue = map.remove(key);
    if (oldValue == null) {
      map.put(key, value);
      return;
    }
    insertOrDisambiguate(map, disambiguate(key, value), value);
    insertOrDisambiguate(map, disambiguate(key, oldValue), oldValue);
  }

  /*
   * Appends an additional token onto the name of the collection. If no more tokens are available,
   * returns the name unchanged.
   */
  private static String disambiguate(String name, CollectionAttribute collection) {
    String[] tokens = collection.getBaseName().split("\\.");
    int level = tokens.length - 2;
    String candidate = tokens[tokens.length - 1];
    for (; level >= 0; level--) {
      if (candidate.equals(name)) {
        break;
      }
      candidate = appendCamelCase(candidate, tokens[level]);
    }
    return level >= 0 ? appendCamelCase(candidate, tokens[level]) : candidate;
  }

  private static String appendCamelCase(String s, String toAppend) {
    if (Strings.isNullOrEmpty(s)) {
      return toAppend;
    }
    return toAppend + s.substring(0, 1).toUpperCase() + s.substring(1);
  }
}
