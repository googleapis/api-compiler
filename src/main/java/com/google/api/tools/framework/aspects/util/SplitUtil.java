/*
 * Copyright 2017 Google Inc.
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

package com.google.api.tools.framework.aspects.util;

import com.google.common.base.Splitter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class to split strings based on various separators. */
public class SplitUtil {

  /** The supported separators. */
  public enum Separator {
    COMMA,
    DOT,
  }

  private static final Splitter commaSplitter = Splitter.on(',').trimResults().omitEmptyStrings();
  private static final Splitter dotSplitter = Splitter.on('.').trimResults().omitEmptyStrings();

  // Split given string to a list of distinct sub strings based on given separator.
  public static List<String> splitDistinct(Separator separator, String string) {
    Splitter splitter;
    switch (separator) {
      case COMMA:
        splitter = commaSplitter;
        break;
      case DOT:
        splitter = dotSplitter;
        break;
      default:
        return Collections.emptyList();
    }
    return splitter.splitToList(string).stream().distinct().collect(Collectors.<String>toList());
  }

  // Split given string to a list of sub strings based on given separator.
  public static List<String> splitToList(Separator separator, String string) {
    switch (separator) {
      case COMMA:
        return commaSplitter.splitToList(string);
      case DOT:
        return dotSplitter.splitToList(string);
      default:
        return Collections.emptyList();
    }
  }
}
