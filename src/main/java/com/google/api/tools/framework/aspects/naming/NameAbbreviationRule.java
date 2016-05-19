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

package com.google.api.tools.framework.aspects.naming;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;

import javax.annotation.Nullable;

/**
 * Style rule to verify if the names of protoelements follow the abbreviation
 * recommendation from style guide.
 */
class NameAbbreviationRule extends LintRule<ProtoElement> {
  private static final ImmutableMap<String, String> NAME_ABBREVIATION_MAP =
      ImmutableMap.<String, String>builder()
          .put("identifier", "id")
          .put("configuration", "config")
          .put("specification", "spec")
          // Add more abbreviation.
          .build();

  NameAbbreviationRule(ConfigAspectBase aspect) {
    super(aspect, "name-abbreviation", ProtoElement.class);
  }

  @Override
  public void run(ProtoElement element) {
    // This rule applies to all ProtoElements except file names.
    if (!(element instanceof ProtoFile)) {
      final String simpleName = element.getSimpleName();
      if (!Strings.isNullOrEmpty(simpleName)) {
        FluentIterable<String> usedLongNames =
            FluentIterable.from(NAME_ABBREVIATION_MAP.keySet())
                .filter(new Predicate<String>() {
                  @Override
                  public boolean apply(String longName) {
                    return simpleName.toLowerCase().contains(longName);
                  }
                });
        if (!usedLongNames.isEmpty()) {
          FluentIterable<String> abbreviationsToUse =
              usedLongNames.transform(new Function<String, String>() {
                @Override
                @Nullable
                public String apply(@Nullable String longName) {
                  return NAME_ABBREVIATION_MAP.get(longName);
                }
              });
          warning(element,
              "Use of full name(s) '%s' in '%s' is not recommended, use '%s' instead.",
              Joiner.on(",").join(usedLongNames), element.getSimpleName(),
              Joiner.on(",").join(abbreviationsToUse));
        }
      }
    }
  }
}
