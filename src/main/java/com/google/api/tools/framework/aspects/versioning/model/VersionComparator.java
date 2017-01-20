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
package com.google.api.tools.framework.aspects.versioning.model;

import java.util.Comparator;
import java.util.regex.Matcher;

/**
 * Compares versions. Release level is compared lexicographically.
 * E.g., v1alpha1 < v1beta1 < v1 < v2beta1 < v2
 */
public class VersionComparator implements Comparator<String> {
  @Override
  public int compare(String version1, String version2) {
    Matcher matcher1 = ApiVersionUtil.SEMANTIC_VERSION_REGEX_PATTERN.matcher(version1);
    if (!matcher1.matches()) {
      return -1;
    }
    Matcher matcher2 = ApiVersionUtil.SEMANTIC_VERSION_REGEX_PATTERN.matcher(version2);
    if (!matcher2.matches()) {
      return 1;
    }

    int majorDiff =
        compareWithNull(parseOrNull(matcher1.group("majornumber")),
                        parseOrNull(matcher2.group("majornumber")), false);
    if (majorDiff != 0) {
      return majorDiff;
    }

    int releaseNameDiff =
        compareWithNull(matcher1.group("releaselevelname"),
                        matcher2.group("releaselevelname"), true);
    if (releaseNameDiff != 0) {
      return releaseNameDiff;
    }

    int releaseNumberDiff =
        compareWithNull(parseOrNull(matcher1.group("releaselevelnumber")),
                        parseOrNull(matcher2.group("releaselevelnumber")), false);
    if (releaseNumberDiff != 0) {
      return releaseNumberDiff;
    }

    return compareWithNull(matcher1.group("releaseleveltrailing"),
        matcher2.group("releaseleveltrailing"), false);
  }

  // Applies the Comparable interface, but accepts null values. `nullGreater` specifies whether
  // a null value should be evaluate to greater than any non-null value.
  private static <T> int compareWithNull(Comparable<T> versionA, T versionB, boolean nullGreater) {
    if (versionA == null) {
      if (versionB == null) {
        return 0;
      }
      return nullGreater ? 1 : -1;
    } else if (versionB == null) {
      return nullGreater ? -1 : 1;
    } else {
      return versionA.compareTo(versionB);
    }
  }

  private static Integer parseOrNull(String s) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
