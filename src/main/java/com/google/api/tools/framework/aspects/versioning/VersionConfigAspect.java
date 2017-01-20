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
import com.google.api.tools.framework.aspects.http.HttpConfigAspect;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.versioning.model.ApiVersionUtil;
import com.google.api.tools.framework.aspects.versioning.model.RestVersionsAttribute;
import com.google.api.tools.framework.aspects.versioning.model.VersionAttribute;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.google.protobuf.Api;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Configuration aspect for versioning.
 */
public class VersionConfigAspect extends ConfigAspectBase {

  public static final Key<String> KEY = Key.get(String.class, Names.named("version"));

  private final Set<ProtoElement> roots = Sets.newHashSet();

  public static VersionConfigAspect create(Model model) {
    return new VersionConfigAspect(model);
  }

  private VersionConfigAspect(Model model) {
    super(model, "versioning");
    registerLintRule(new ConfigVersionRule(this));
    registerLintRule(new HttpVersionRule(this));
  }

  /**
   * Returns dependencies. Depends on http attributes used for determining the version
   * from the HTTP path.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.<Class<? extends ConfigAspect>>of(HttpConfigAspect.class);
  }

  @Override
  public void startMerging() {
    // Load the roots here since roots in the model was empty when VersionConfigAspect was created.
    for (ProtoElement root: getModel().getRoots()) {
      roots.add(root);
    }

    // Verify the config_version is explicitly specified in the service config.
    if (!getModel().getServiceConfig().hasConfigVersion()) {
      error(SimpleLocation.TOPLEVEL, "config_version is not specified in the service config file.");
    }

    // Detect config version location.
    Location configVersionLocation = getLocationInConfig(
        getModel().getServiceConfig().getConfigVersion(), "value");

    if (getModel().getConfigVersion() > Model.getDevConfigVersion()) {
      error(
          configVersionLocation,
          String.format("config_version %s is invalid, the latest config_version is %s.",
              getModel().getConfigVersion(), Model.getDevConfigVersion()));
    }
  }

  @Override
  public void merge(ProtoElement element) {
    if (element instanceof Interface) {
      merge((Interface) element);
    }
    if (element instanceof Method) {
      merge((Method) element);
    }
  }

  private void merge(Interface iface) {
    Api api = iface.getConfig();
    if (api == null) {
        return;
    }
    // Get user-defined api version, which is optional.
    String apiVersion = api.getVersion();
    String packageName = iface.getFile().getFullName();
    if (Strings.isNullOrEmpty(apiVersion)) {
      // If version is not provided by user, extract major version from package name.
      apiVersion = ApiVersionUtil.extractDefaultMajorVersionFromPackageName(packageName);
    } else {
      // Validate format of user-defined api version .
      if (!ApiVersionUtil.isValidApiVersion(apiVersion)) {
        error(getLocationInConfig(api, "version"),
            "Invalid version '%s' defined in API '%s'.", apiVersion, api.getName());
      }

      // Validate that the version in the package name is consistent with what user provides.
      String apiVersionFromPackageName =
          ApiVersionUtil.extractDefaultMajorVersionFromPackageName(packageName);
      // Workaround for OpenApi builds which do not use package names and set version
      // explicitly.
      if (!packageName.isEmpty()
          && !apiVersionFromPackageName.equals(
              ApiVersionUtil.extractMajorVersionFromSemanticVersion(apiVersion))) {
        error(iface,
            "User-defined api version '%s' is inconsistent with the one in package name '%s'.",
            apiVersion, packageName);
      }
    }
    iface.putAttribute(VersionAttribute.KEY, VersionAttribute.create(apiVersion));
  }

  private void merge(Method method) {
    String restVersion = deriveApiVersion(method);
    method.putAttribute(VersionAttribute.KEY, VersionAttribute.create(restVersion));
    // UM uses the logical version with a suffix appended, if defined.
    String versionSuffix = method.getModel().getApiV1VersionSuffix();
    method.putAttribute(VersionAttribute.USAGE_MANAGER_KEY,
        VersionAttribute.create(ApiVersionUtil.appendVersionSuffix(restVersion, versionSuffix)));

    // Add the rest version into RestVersionsAttribute only if parent of the method is included in
    // the model roots.
    if (roots.contains(method.getParent())) {
      if (getModel().hasAttribute(RestVersionsAttribute.KEY)) {
        getModel().getAttribute(RestVersionsAttribute.KEY).getVersions().add(restVersion);
      } else {
        getModel().putAttribute(RestVersionsAttribute.KEY,
            new RestVersionsAttribute(new LinkedHashSet<>(ImmutableList.of(restVersion))));
      }
    }
  }

  @SuppressWarnings("deprecation")
  private String deriveApiVersion(Method element) {

    // Derive the version from the prefix of the http path. Validation of
    // syntax of version in path happens elsewhere, so we take just the first path segment
    // literal. If none is given, assume 'v1'.
    HttpAttribute http = element.getAttribute(HttpAttribute.KEY);
    if (http == null || http.getPath().isEmpty()
        || !(http.getPath().get(0) instanceof LiteralSegment)) {
      return "v1";
    }
    return ((LiteralSegment) http.getPath().get(0)).getLiteral();
  }
}
