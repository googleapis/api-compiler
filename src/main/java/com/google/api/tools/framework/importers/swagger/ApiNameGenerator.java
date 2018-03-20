/*
 * Copyright (C) 2017 Google Inc.
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
package com.google.api.tools.framework.importers.swagger;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.commons.lang3.CharUtils;

/**
 * Generates canonical API names in the form "[version].[api-name]" where:
 *
 * If the service version string (info.version) conforms to semantic versioning,
 * the API version is the major version.
 * In all other cases, the API version is the entire service version string.
 *
 * The API name may be explicitly specified by the user through the `x-google-api-name` OpenAPI
 * extension. If absent, the API name is derived from the service hostname, with all non ASCII
 * alphanumeric characters replaced with {@link ApiNameGenerator#API_NAME_FILLER_CHAR}.
 *
 * Adds '_' at start if hostname is empty or starts with non alpha character,
 * since API names can only start with alpha or '_'.
 */
public class ApiNameGenerator {
  private static final Pattern RE_API_NAME = Pattern.compile("[a-z][a-z0-9]{0,39}+");
  private static final char API_NAME_FILLER_CHAR = '_';
  private static final char SEPARATOR = '.';
  private static final int SEMANTIC_VERSION_EXPECTED_PIECES = 3; //semantic version is in form x.y.z

  /**
   * Generates canonical API name based on hostname/api-name/version combination.
   * The API name may be explicitly specified through the x-google-api-name OpenAPI extension.
   * If absent, the API name is derived from the service name.
   */
  public static String generate(String hostname, String googleApiName, String version)
      throws OpenApiConversionException {
    String cleanedHostName = replaceNonAlphanumericChars(hostname, false);
    if (!startsWithAlphaOrUnderscore(cleanedHostName)) {
      cleanedHostName = API_NAME_FILLER_CHAR + cleanedHostName;
    }

    if (!googleApiName.isEmpty() && !isValidApiName(googleApiName)) {
      throw new OpenApiConversionException(
          String.format("Invalid API name '%s' : API names much conform to %s.",
              googleApiName, RE_API_NAME.pattern()));
    }

    String apiName = googleApiName;
    if (apiName.isEmpty()) {
      apiName = cleanedHostName;
    }

    String cleanedMajorVersion = replaceNonAlphanumericChars(parseMajorVersion(version), true);
    if (cleanedMajorVersion.isEmpty()) {
      return apiName;
    } else {
      return String.format("%s%s%s", cleanedMajorVersion, SEPARATOR, apiName);
    }
  }

  /**
   * Returns the 'major_version' part of a version string. For strings that do not follow 'semantic
   * versions', the entire version string is considered the major version.
   */
  private static String parseMajorVersion(String version) {
    List<String> versionParts =
        Lists.newArrayList(Splitter.on(SEPARATOR).omitEmptyStrings().split(version));
    if (versionParts.size() == SEMANTIC_VERSION_EXPECTED_PIECES) {
      // Return first piece (major version)
      return versionParts.get(0);
    }
    return version;
  }

  /**
   * Replaces all non alphanumeric characters in input string with {@link
   * ApiNameGenerator#API_NAME_FILLER_CHAR}
   */
  private static String replaceNonAlphanumericChars(String input, boolean allowDots) {
    StringBuilder alphaNumeric = new StringBuilder();
    for (char hostnameChar : input.toCharArray()) {
      if (CharUtils.isAsciiAlphanumeric(hostnameChar) || (allowDots && hostnameChar == SEPARATOR)) {
        alphaNumeric.append(hostnameChar);
      } else {
        alphaNumeric.append(API_NAME_FILLER_CHAR);
      }
    }
    return alphaNumeric.toString();
  }

  private static boolean startsWithAlphaOrUnderscore(String string) {
    if (Strings.isNullOrEmpty(string)) {
      return false;
    }
    char firstCharacter = string.charAt(0);
    return CharUtils.isAsciiAlpha(firstCharacter) || firstCharacter == API_NAME_FILLER_CHAR;
  }

  private static boolean isValidApiName(String apiName) {
    return RE_API_NAME.matcher(apiName).matches();
  }
}
