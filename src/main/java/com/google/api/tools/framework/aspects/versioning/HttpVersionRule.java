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

package com.google.api.tools.framework.aspects.versioning;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.versioning.model.ApiVersionUtil;
import com.google.api.tools.framework.aspects.versioning.model.VersionAttribute;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.common.base.Strings;

/**
 * Style rule for HTTP path version.
 */
class HttpVersionRule extends LintRule<Method> {

  HttpVersionRule(ConfigAspectBase aspect) {
    super(aspect, "http-version-prefix", Method.class);
  }

  @Override public void run(Method method) {
    if (!method.hasAttribute(HttpAttribute.KEY)) {
      return;
    }
    HttpAttribute httpBinding = method.getAttribute(HttpAttribute.KEY);
    String version = null;
    PathSegment firstPathSeg = httpBinding.getPath().get(0);
    if (firstPathSeg instanceof LiteralSegment) {
      String firstPathSegLiteral = ((LiteralSegment) firstPathSeg).getLiteral();
      if (firstPathSegLiteral.startsWith("$")) {
        // Allow the first segment to start with $, which is used by system APIs like Discovery.
        return;
      }
      if (ApiVersionUtil.isValidMajorVersion(firstPathSegLiteral)) {
        version = firstPathSegLiteral;
        // Retrieve api version defined in service config.
        String apiVersion =
            ((Interface) method.getParent()).getAttribute(VersionAttribute.KEY).majorVersion();
        if (!Strings.isNullOrEmpty(apiVersion) && !version.equals(apiVersion)) {
          warning(method,
              "method '%s' has a different version prefix in HTTP path ('%s') than api "
              + "version '%s'.",
              method.getFullName(), version, apiVersion);
        }
      }
    }

    Object location = method;
    if (!httpBinding.isFromIdl()) {
      location = getLocationInConfig(
          httpBinding.getHttpRule(), httpBinding.getAnySpecifiedFieldInHttpRule());
    }
    if (version == null) {
      warning(
          location,
          "'method %s' has a HTTP path that does not start with version, which must match '%s'.",
          method.getFullName(),
          ApiVersionUtil.MAJOR_VERSION_REGEX_PATTERN.pattern());
    }
  }
}
