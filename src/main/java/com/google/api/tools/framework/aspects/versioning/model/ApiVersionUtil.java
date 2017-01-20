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

package com.google.api.tools.framework.aspects.versioning.model;

import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * Util class that handles api version in //google/protobuf/api.proto
 */
public class ApiVersionUtil {
  public static final Pattern MAJOR_VERSION_REGEX_PATTERN = Pattern.compile("^(v?\\d+(\\w+)?)$");

  public static final Pattern SEMANTIC_VERSION_REGEX_PATTERN =
      Pattern.compile("^(?<majorversion>v?"
        + "(?<majornumber>\\d+)"
        + "((?<releaselevelname>[a-zA-Z_]+)"
        + "(?<releaselevelnumber>[0-9]*)"
        + "(?<releaseleveltrailing>[a-zA-Z_]\\w*)?)?)"
        + "(\\.\\d+){0,2}$"); // major-version.minor-version.patch

  /**
   * Extract major version from package name. The major version is reflected in the package name of
   * the API, which must end in `v{major-version}`, as in `google.feature.v1`. For major versions 0
   * and 1, the suffix can be omitted. For that case, `v1` is returned.
   */
  public static String extractDefaultMajorVersionFromPackageName(String packageName) {
    String[] segs = packageName.split("\\.");
    String lastSeg = segs[segs.length - 1];
    Matcher matcher = MAJOR_VERSION_REGEX_PATTERN.matcher(lastSeg);
    if (matcher.find()) {
      return matcher.group(1);
    } else {
      return "v1";
    }
  }

  /**
   * Extract major version from REST element name. Rest element name has the form of "v1.foo.bar"
   * or "foo.bar". For the later case, "v1" will be returned.
   */
  public static String extractDefaultMajorVersionFromRestName(String restName) {
    Preconditions.checkNotNull(restName);
    String [] segs = restName.split("\\.");
    if (segs.length == 0) {
      return "v1";
    }
    Matcher matcher = MAJOR_VERSION_REGEX_PATTERN.matcher(segs[0]);
    return matcher.find() ? matcher.group(1) : "v1";
  }

  /**
   * Returns the REST name with version prefix stripped if it has. Returns empty if the rest name
   * only contains the version.
   */
  public static String stripVersionFromRestName(String restName) {
    Preconditions.checkNotNull(restName);
    String version = extractDefaultMajorVersionFromRestName(restName);

    String[] segs = restName.split("\\.");
    if (segs.length > 1) {
      version = version + ".";
    }

    return restName.startsWith(version) ? restName.substring(version.length()) : restName;
  }

  /**
   * Return major version of the given semantic version. For example, `v2` is returned from `v2.10`.
   * Return null if major version cannot be extracted.
   */
  @Nullable
  public static String extractMajorVersionFromSemanticVersion(String semanticVersion) {
    Matcher matcher = SEMANTIC_VERSION_REGEX_PATTERN.matcher(semanticVersion);
    if (matcher.find()) {
      return matcher.group("majorversion");
    } else {
      return null;
    }
  }

  /**
   *  Return true if apiVersion is a valid semantic version (http://semver.org). Otherwise, false.
   */
  public static boolean isValidApiVersion(String apiVersion) {
    return SEMANTIC_VERSION_REGEX_PATTERN.matcher(apiVersion).matches();
  }

  /**
   *  Return true if apiVersion has a valid major version format.
   */
  public static boolean isValidMajorVersion(String apiVersion) {
    return MAJOR_VERSION_REGEX_PATTERN.matcher(apiVersion).matches();
  }

  /**
   * Append version suffix, if defined, to the given apiVersion.
   */
  public static String appendVersionSuffix(String apiVersion, String versionSuffix) {
    return Strings.isNullOrEmpty(versionSuffix) ? apiVersion : apiVersion + versionSuffix;
  }

  /**
   * Return the list of API versions for all reachable API methods.
   */
  public static List<String> getReachableRestVersions(Model model) {
    Set<String> versions = Sets.newLinkedHashSet();
    for (Interface iface : model.getSymbolTable().getInterfaces()) {
      for (Method method : iface.getReachableMethods()) {
        if (method.hasAttribute(HttpAttribute.KEY)) {
          versions.add(
              extractDefaultMajorVersionFromRestName(
                  method.getAttribute(HttpAttribute.KEY).getRestMethod().getRestFullMethodName()));
        }
      }
    }
    List<String> versionsList = Lists.newArrayList(versions);
    Collections.sort(versionsList, Collections.reverseOrder(new VersionComparator()));
    return versionsList;
  }
}
