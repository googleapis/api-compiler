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

package com.google.api.tools.framework.aspects.http;

import com.google.api.tools.framework.aspects.http.RestPatterns.MethodPattern;
import com.google.api.tools.framework.aspects.http.RestPatterns.SegmentPattern;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.WildcardSegment;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.model.Method;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.regex.Matcher;

/** Helper class to match a method against a method pattern. */
public class MethodMatcher {
  private final MethodPattern pattern;
  private final Method method;
  private final HttpAttribute httpConfig;
  private Matcher nameMatcher;
  private boolean matches;

  public MethodMatcher(MethodPattern pattern, Method method, HttpAttribute httpConfig) {
    this.pattern = pattern;
    this.method = method;
    this.httpConfig = httpConfig;

    matches = false;

    // Check http method.
    if (httpConfig.getMethodKind() != pattern.httpMethod()) {
      return;
    }

    // Check name regexp.
    nameMatcher = pattern.nameRegexp().matcher(method.getSimpleName());
    if (!nameMatcher.matches()) {
      return;
    }

    // Determine match on last segment.
    List<PathSegment> flatPath = httpConfig.getFlatPath();
    PathSegment lastSegment = Iterables.getLast(flatPath);
    switch (pattern.lastSegmentPattern()) {
      case CUSTOM_VERB_WITH_COLON:
        // Allow only standard conforming custom method which uses <prefix>:<literal>.
        matches =
            lastSegment instanceof LiteralSegment
                && ((LiteralSegment) lastSegment).isTrailingCustomVerb();
        break;
      case CUSTOM_VERB:
        // Allow both a custom verb literal and a regular literal, the latter is for supporting
        // legacy custom verbs.
        matches = lastSegment instanceof LiteralSegment;
        break;
      case VARIABLE:
        matches = lastSegment instanceof WildcardSegment;
        break;
      case LITERAL:
        matches =
            lastSegment instanceof LiteralSegment
                && !((LiteralSegment) lastSegment).isTrailingCustomVerb();
        break;
    }
  }

  // Creates a RestMethod from this matcher.
  RestMethod createRestMethod() {
    if (pattern.lastSegmentPattern() == SegmentPattern.CUSTOM_VERB
        || pattern.lastSegmentPattern() == SegmentPattern.CUSTOM_VERB_WITH_COLON) {
      return RestAnalyzer.createCustomMethod(method, httpConfig, pattern.customPrefix());
    }
    return RestMethod.create(
        method,
        pattern.restKind(),
        RestAnalyzer.buildCollectionName(httpConfig.getFlatPath()),
        null);
  }

  public boolean matches() {
    return matches;
  }
}
