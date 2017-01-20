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

import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.http.model.RestKind;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.io.PrintStream;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Contains pattern objects for rest method and segments.
 */
public class RestPatterns {

  /** Represents various valid method patterns. */
  @AutoValue
  public abstract static class MethodPattern {

    // The http method.
    public abstract MethodKind httpMethod();

    // A regular expression which the rpc name must match.
    public abstract Pattern nameRegexp();

    // A pattern which the last segment of the path must match.
    @Nullable
    public abstract SegmentPattern lastSegmentPattern();

    // The implied rest kind.
    @Nullable
    public abstract RestKind restKind();

    // The implied prefix to use for custom methods.
    abstract String customPrefix();

    // Documentation of the pattern.
    abstract String description();

    static MethodPattern create(MethodKind methodKind, String nameRegexp,
        SegmentPattern lastSegment, RestKind restKind, String customPrefix, String description) {
      return new AutoValue_RestPatterns_MethodPattern(methodKind, Pattern.compile(nameRegexp),
          lastSegment, restKind, customPrefix, description);
    }

    public static MethodPattern create(
        MethodKind methodKind,
        String nameRegexp,
        SegmentPattern lastSegment,
        RestKind restKind,
        String description) {
      return create(methodKind, nameRegexp, lastSegment, restKind, "", description);
    }

    // Produce readable output for this pattern. This is used in error messages, as in
    // 'rpc Get.* as HTTP GET <prefix>/<wildcard>'. We don't print the rest kind.
    @Override
    public String toString() {
      StringBuilder result = new StringBuilder();
      result.append("rpc ");
      result.append(nameRegexp().pattern());
      result.append(" as HTTP ");
      result.append(httpMethod().toString());
      if (lastSegmentPattern() != null) {
        result.append(" ");
        result.append(pathDisplay());
      }
      return result.toString();
    }

    private String pathDisplay() {
      if (lastSegmentPattern() != null) {
        switch (lastSegmentPattern()) {
          case VARIABLE:
            return "<prefix>/<wildcard>";
          case CUSTOM_VERB:
          case CUSTOM_VERB_WITH_COLON:
            return "<prefix>:<literal>";
          case LITERAL:
            return "<prefix>/<literal>";
        }
      }
      return "";
    }
  }

  // A pattern for a path segment.
  enum SegmentPattern {
    VARIABLE,
    LITERAL,
    // matches both legacy custom method segment which uses <prefix>/<literal> and standard
    // conforming custom method which uses <prefix>:<literal>.
    CUSTOM_VERB,
    // matches only one platform conforming custom method which uses <prefix>:<literal>.
    CUSTOM_VERB_WITH_COLON,
  }

  // Declares all the currently allowed rest method patterns. If none of those
  // matches, a warning will be produced.
  public static final ImmutableList<MethodPattern> METHOD_PATTERNS =
      // First list all standard methods. They have priority in matching.
      // Note that the only source of ambiguity here is a legacy custom
      // method which uses <prefix>/<literal> instead of <prefix>:<literal>, otherwise
      // our patterns would be unique.
      ImmutableList.of(
          MethodPattern.create(
              MethodKind.GET, "Get.*", SegmentPattern.VARIABLE, RestKind.GET, "Gets a resource."),
          MethodPattern.create(
              MethodKind.GET,
              "List.*",
              SegmentPattern.LITERAL,
              RestKind.LIST,
              "Lists all resources"),
          MethodPattern.create(
              MethodKind.PUT,
              "Update.*",
              SegmentPattern.VARIABLE,
              RestKind.UPDATE,
              "Update a resource."),
          MethodPattern.create(
              MethodKind.PUT,
              "(Create|Insert).*",
              SegmentPattern.VARIABLE,
              RestKind.CREATE,
              "Create a resource."),
          MethodPattern.create(
              MethodKind.PATCH,
              "(Update|Patch).*",
              SegmentPattern.VARIABLE,
              RestKind.PATCH,
              "Patch a resource."),
          MethodPattern.create(
              MethodKind.DELETE,
              "Delete.*",
              SegmentPattern.VARIABLE,
              RestKind.DELETE,
              "Delete a resource"),
          MethodPattern.create(
              MethodKind.POST,
              "(Create|Insert).*",
              SegmentPattern.LITERAL,
              RestKind.CREATE,
              "Create a resource"),
          // Next list all custom methods.
          MethodPattern.create(
              MethodKind.GET,
              "Get.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "get",
              "Custom get resource."),
          MethodPattern.create(
              MethodKind.GET,
              "List.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "list",
              "Custom list resources."),
          MethodPattern.create(
              MethodKind.GET,
              ".*",
              SegmentPattern.CUSTOM_VERB_WITH_COLON,
              RestKind.CUSTOM,
              "General custom get method."),
          MethodPattern.create(
              MethodKind.PUT,
              "Update.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "update",
              "Custom update resource."),
          MethodPattern.create(
              MethodKind.PUT,
              "Create.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "create",
              "Custom create resource."),
          MethodPattern.create(
              MethodKind.PATCH,
              "Patch.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "patch",
              "Custom patch resource."),
          MethodPattern.create(
              MethodKind.PATCH,
              "Update.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "update",
              "Custom update resource"),
          MethodPattern.create(
              MethodKind.DELETE,
              "Delete.*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "delete",
              "Custom delete resource"),
          MethodPattern.create(
              MethodKind.POST,
              ".*",
              SegmentPattern.CUSTOM_VERB,
              RestKind.CUSTOM,
              "General custom method."));

  /**
   * Main entry point for generating a table of supported REST patterns.
   */
  public static void main(String[] args) {
    PrintStream out = System.out;
    out.println("HTTP method | RPC name regexp | Path | REST verb | REST name | Remarks");
    out.println("------------|-----------------|------|-----------|-----------|--------");
    for (MethodPattern pattern : METHOD_PATTERNS) {
      out.print("`" + pattern.httpMethod() + "`");
      out.print(" | ");
      out.print("`" + pattern.nameRegexp().pattern().replace("|", "\\|") + "`");
      out.print(" | ");
      out.print("`" + pattern.pathDisplay() + "`");
      out.print(" | ");
      out.print("`" + pattern.restKind() + "`");
      out.print(" | ");
      out.print("`" + restNameDisplay(pattern) + "`");
      out.print(" | ");
      out.print(pattern.description());
      out.println();
    }
  }

  private static String restNameDisplay(MethodPattern pattern) {
    if (pattern.restKind() == RestKind.CUSTOM) {
      return pattern.customPrefix().isEmpty() ? "<literal>" : pattern.customPrefix() + "<Literal>";
    }
    return pattern.restKind().toString().toLowerCase();
  }
}
