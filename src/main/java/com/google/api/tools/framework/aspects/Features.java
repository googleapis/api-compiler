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

package com.google.api.tools.framework.aspects;

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages a collection of {@link Feature} objects that together define the feature handling for a
 * particular service config element.
 */
public class Features {
  private static final String FEATURE_DISABLE_CHARACTER_PREFIX = "~";
  private final Map<String, Feature> featuresByName;

  public Features(Feature... features) {
    ImmutableMap.Builder<String, Feature> featuresByNameBuilder = ImmutableMap.builder();
    for (Feature feature : features) {
      feature.addSelfAndChildren(featuresByNameBuilder);
    }
    featuresByName = featuresByNameBuilder.build();
  }

  public static String getFeatureNameStringToDisable(String featureName) {
    return FEATURE_DISABLE_CHARACTER_PREFIX + featureName;
  }

  /**
   * Evaluates a collection of feature strings provided by the service owner, taking into account
   * the feature definitions and the current config version, and returns the canonicalized set of
   * enabled features.  Any issues are reported to the given DiagCollector.
   *
   * @param input a collection of strings that are either feature names (indicating that the named
   *   feature should be enabled), or a '~' followed by a feature name (indicating that the named
   *   feature should be disabled).
   */
  public Set<String> evaluate(
      Collection<String> input, int configVersion, DiagCollector diags, Location location) {
    Set<String> enabled = new TreeSet<>();

    // Accumulate features that are enabled by default.
    for (Feature feature : featuresByName.values()) {
      if (feature.isDefaultIn(configVersion)) {
        feature.accumulate(enabled, false /* remove */, configVersion, diags, location);
      }
    }

    // Accumulate features that are explicitly specified.
    for (String featureString : input) {
      boolean remove = featureString.startsWith("~");
      String featureName = remove ? featureString.substring(1) : featureString;
      Feature feature = featuresByName.get(featureName);
      if (feature == null) {
        diags.addDiag(Diag.error(
            location,
            "No such feature %s supported for this element. "
            + "Supported features for this config version are: [%s].",
            featureName,
            Joiner.on(", ").join(getSupportedFeatureNames(configVersion))));
      } else {
        feature.accumulate(enabled, remove, configVersion, diags, location);
      }
    }
    return enabled;
  }

  private Set<String> getSupportedFeatureNames(int configVersion) {
    Set<String> supportedFeatures = new TreeSet<>();
    for (Feature feature : featuresByName.values()) {
      if (feature.isSupportedIn(configVersion)) {
        supportedFeatures.add(feature.getName());
      }
    }
    return supportedFeatures;
  }
}
