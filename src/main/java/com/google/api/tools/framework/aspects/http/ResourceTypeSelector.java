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

import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.TypeRef;
import com.google.common.collect.Maps;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Helper class to select the candidate of resource type from a collection of methods.
 */
class ResourceTypeSelector {

  private final Map<TypeRef, Integer> typeRanks = Maps.newHashMap();
  private final Model model;

  ResourceTypeSelector(Model model, Iterable<RestMethod> methods) {
    this.model = model;
    for (RestMethod method : methods) {
      switch (method.getRestKind()) {
        case GET:
          // Response type of REST GET method is likely a resource type.
          incrementRank(method.getBaseMethod().getOutputType());
          break;
        case CREATE:
        case UPDATE:
          // Request body type of REST CREATE or UPDATE method is likely a resource type.
          TypeRef type = getSingleBodyType(method.getBaseMethod());
          if (type != null) {
            incrementRank(type);
          }
          break;
        default:
          break;
      }
    }
  }

  /**
   * Returns the type with the highest rank as the candidate of resource type.
   * If there is ranking tie, returns null.
   */
  @Nullable TypeRef getCandiateResourceType() {
    TypeRef topRankedType = null;
    int topRank = 0;
    for (Map.Entry<TypeRef, Integer> typeRank : typeRanks.entrySet()) {
      int currentRank = typeRank.getValue();
      if (topRank == currentRank) {
        // There is tie for type ranks.
        topRankedType = null;
      } else if (topRank < currentRank) {
        topRank = currentRank;
        topRankedType = typeRank.getKey();
      }
    }
    return topRankedType;
  }

  /**
   * Returns the body type iff the body associates with exactly one message type.
   * Otherwise, returns null.
   */
  @Nullable
  private TypeRef getSingleBodyType(Method method) {
    HttpAttribute httpConfig = method.getAttribute(HttpAttribute.KEY);
    if (httpConfig == null) {
      return null;
    }
    if (!httpConfig.bodyCapturesUnboundFields() && httpConfig.getBodySelectors().size() == 1) {
      TypeRef type = httpConfig.getBodySelectors().get(0).getType();
      if (type.isMessage()) {
        // Get the TypeRef of the declared MessageType that is referenced in the body
        // selector field.
        // TODO(user): If it is common use pattern, consider moving it to where it belongs.
        return model.getSymbolTable().lookupType(type.getMessageType().getFullName());
      }
    }
    return null;
  }

  private void incrementRank(TypeRef type) {
    int rank = typeRanks.containsKey(type) ? typeRanks.get(type) + 1 : 1;
    typeRanks.put(type, rank);
  }
}