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

package com.google.api.tools.framework.aspects.http.model;

import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * An object representing a REST collection, as derived from analyzing an Api's
 * services and methods. A list of those attributes is attached to the model object.
 */
public class CollectionAttribute extends Element {

  /** The key to access the collections of the model. */
  public static final Key<List<CollectionAttribute>> KEY =
      Key.get(new TypeLiteral<List<CollectionAttribute>>() {});

  private final Model model;
  private final String name;
  private final String version;
  private final ListMultimap<String, RestMethod> methods = ArrayListMultimap.create();
  private TypeRef resourceType;
  private CollectionAttribute parent;

  public CollectionAttribute(Model model, String name, String version) {
    this.model = model;
    this.name = name;
    this.version = version;
  }

  @Override
  public Model getModel() {
    return model;
  }

  @Override
  public Location getLocation() {
    return model.getLocation();
  }

  @Override
  public String getSimpleName() {
    String fullName = getFullName();
    return Strings.isNullOrEmpty(fullName) ? "" : fullName.substring(fullName.lastIndexOf('.') + 1);
  }

  @Override
  public String getFullName() {
    return versionedCollectionName(version, name);
  }

  public static String versionedCollectionName(String version, String baseCollectionName) {
    String sep =
        Strings.isNullOrEmpty(version) || Strings.isNullOrEmpty(baseCollectionName) ? "" : ".";
    return version + sep + baseCollectionName;
  }

  /** Returns the methods associated with this collection. */
  public Iterable<RestMethod> getMethods() {
    List<RestMethod> result = Lists.newArrayList();
    // Only return the last RestMethod object for each rest method name,
    // since the last one will override all the other RestMethods with
    // the same name.
    for (Collection<RestMethod> values : methods.asMap().values()) {
      result.add(Iterables.getLast(values));
    }
    Collections.sort(
        result,
        new Comparator<RestMethod>() {

          @Override
          public int compare(RestMethod o1, RestMethod o2) {
            return o1.getFullName().compareTo(o2.getFullName());
          }
        });
    return result;
  }

  /** @return names of all the rest methods inside this collection. */
  public Set<String> getRestMethodNames() {
    return Sets.newTreeSet(methods.keySet());
  }

  /**
   * @return rest methods within the collection for a given rest method name.
   *     <p>Ideally there should be only one {@link RestMethod} for a given rest method name.
   *     However, in case of invalid configuration, there can be duplicate {@link RestMethod}s for a
   *     a given rest method name.
   */
  public List<RestMethod> getRestMethodWithDuplicates(String restMethodName) {
    return methods.get(restMethodName);
  }

  /**
   * Returns the methods associated with this collection and reachable with the current scoper.
   */
  public ImmutableList<RestMethod> getReachableMethods() {
    return FluentIterable.from(getMethods())
        .filter(
            new Predicate<RestMethod>() {
              @Override
              public boolean apply(RestMethod method) {
                return method.isReachable();
              }
            })
        .toList();
  }

  /**
   * Returns the resource type, or null, if none assigned.
   */
  @Nullable
  public TypeRef getResourceType() {
    return resourceType;
  }

  /**
   * Sets the resource type.
   */
  public void setResourceType(@Nullable TypeRef resourceType) {
    this.resourceType = resourceType;
  }

  /**
   * Returns the parent collection, or null if none.
   */
  @Nullable
  public CollectionAttribute getParent() {
    return parent;
  }

  /*
   * Returns the version associated with the collection full name. If there is no version, the
   * default "v1" will be returned.
   */
  public String getVersionWithDefault() {
    return Strings.isNullOrEmpty(version) ? "v1" : version;
  }

  /** Returns the base collection name, without version. */
  public String getBaseName() {
    return name;
  }

  /**
   * Set parent collection.
   */
  public void setParent(CollectionAttribute collection) {
    this.parent = collection;
  }

  /** Add method to collection. Returns true if the collection changes; false otherwise. */
  public boolean addMethod(RestMethod method) {
    return methods.put(method.getRestMethodName(), method);
  }
}
