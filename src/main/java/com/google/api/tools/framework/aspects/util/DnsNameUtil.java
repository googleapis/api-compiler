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

package com.google.api.tools.framework.aspects.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for service DNS name.
 */
public class DnsNameUtil {
  // Pattern for extracting API base name.
  // The non-prod expected DNS name pattern has changed from
  // {apiname}-{env}.sandbox.* to {env}-{apiname}.sandbox.*
  private static final Pattern SERVICE_NAME_PATTERN =
      Pattern.compile("^(staging-|test-|local-)?(?<corp>.+)(\\.corp|-corp)(\\.sandbox)?\\.[^.]+\\.[^.]+$"
          + "|^(staging-|test-|local-)(?<sandboxed>.+)\\.sandbox\\.[^.]+\\.[^.]+$"
          + "|^(?<legacySandboxed>.+)(-staging\\.sandbox|-test\\.sandbox|-local\\.sandbox)\\.[^.]+\\.[^.]+$"
          + "|^(?<regular>.+)\\.[^.]+\\.[^.]+$");

  public static boolean matchServiceNamePattern(String serviceName) {
    return SERVICE_NAME_PATTERN.matcher(serviceName).matches();
  }

  /**
   * Returns the API name as derived from a service name.
   */
  public static String deriveApiNameFromServiceName(String serviceName) {
    Matcher matcher = SERVICE_NAME_PATTERN.matcher(serviceName);
    if (matcher.matches()) {
      serviceName = matcher.group("sandboxed");
      if (serviceName == null) {
        serviceName = matcher.group("corp");
        if (serviceName != null) {
          // Add a corp_ prefix to the corp APIs.
          serviceName = "corp_" + serviceName;
        }
      }
      if (serviceName == null) {
        serviceName = matcher.group("legacySandboxed");
      }
      if (serviceName == null) {
        serviceName = matcher.group("regular");
      }
    }
    return serviceName.replace('.', '_').replace('-', '_');
  }
}

