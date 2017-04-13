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

import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.common.base.Preconditions;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;
import java.util.List;
import java.util.Set;

/**
 * Feature represents a platform behavior that may not be suitable for all services. For example, a
 * feature may control whether an API proxy should automatically serve API description (discovery)
 * documents.
 *
 * <p>A feature:
 *
 * <ul>
 *   <li>has a name composed of '/' separated parts, starting with a namespace identifier
 *   <li>may be enabled or disabled by default at a particular config version
 *   <li>only be available at a limited range of config versions
 *   <li>may have child features that are enabled when the parent is enabled
 * </ul>
 */
public class Feature {
  private final String featureName;
  private final List<ChildBinding> childBindings;
  private final Range<Integer> supportedVersionRange;
  private final Range<Integer> defaultVersionRange;

  private Feature(Builder builder) {
    Preconditions.checkArgument(
        !builder.supportedVersionRange.canonical(DiscreteDomain.integers()).isEmpty(),
        "supportedVersionRange must not be empty");
    featureName = builder.featureName;
    childBindings = builder.childBindings.build();
    supportedVersionRange = builder.supportedVersionRange;
    defaultVersionRange = builder.defaultVersionRange;
  }

  /** Returns true if the feature is enabled by default in the given config version. */
  boolean isDefaultIn(int configVersion) {
    return defaultVersionRange.contains(configVersion);
  }

  /** Returns true if the feature is supported in the given config version. */
  boolean isSupportedIn(int configVersion) {
    return supportedVersionRange.contains(configVersion);
  }

  /**
   * Adds or removes this feature from the given set of enabled features. Validates that this
   * feature is available in the given config version.
   *
   * <p>If this feature has children bound at the given config version, then those are accumulated
   * as well.
   */
  void accumulate(
      Set<String> enabled,
      boolean remove,
      int configVersion,
      DiagReporter diagReporter,
      LocationContext location) {
    if (!remove && !isSupportedIn(configVersion)) {
      diagReporter.reportError(
          location, "Feature %s not available in config version %s.", this, configVersion);
    }
    if (!remove) {
      if (!enabled.add(featureName)) {
        diagReporter.reportWarning(
            location,
            "Enabling feature %s had no effect because the feature was already enabled.",
            this);
      }
    } else {
      if (!enabled.remove(featureName)) {
        diagReporter.reportWarning(
            location,
            "Disabling feature %s had no effect because the feature was already disabled.",
            this);
      }
    }
    for (ChildBinding binding : childBindings) {
      if (binding.range.contains(configVersion)) {
        binding.child.accumulate(enabled, remove, configVersion, diagReporter, location);
      }
    }
  }

  /** Retrieve all child names. (For validation in Features). */
  Iterable<String> getChildNames() {
    ImmutableList.Builder<String> names = ImmutableList.builder();
    for (ChildBinding binding : childBindings) {
      names.add(binding.child.getName());
    }
    return names.build();
  }

  void addSelfAndChildren(ImmutableMap.Builder<String, Feature> featuresByNameBuilder) {
    featuresByNameBuilder.put(featureName, this);
    for (ChildBinding binding : childBindings) {
      binding.child.addSelfAndChildren(featuresByNameBuilder);
    }
  }

  /** Gets the name of the feature. */
  String getName() {
    return featureName;
  }

  @Override
  public String toString() {
    return featureName;
  }

  /** Creates a new Builder with the given feature name. */
  public static Builder builder(String featureName) {
    return new Builder(featureName);
  }

  private static class ChildBinding {
    private final Feature child;
    private final Range<Integer> range;

    ChildBinding(Feature child, Range<Integer> range) {
      this.child = child;
      this.range = range;
    }
  }

  /** Builder for {@link Feature}s. */
  public static class Builder {
    private final String featureName;
    private Range<Integer> supportedVersionRange = Range.all();
    private Range<Integer> defaultVersionRange = Range.openClosed(0, 0); // Empty
    private ImmutableList.Builder<ChildBinding> childBindings = ImmutableList.builder();

    private Builder(String featureName) {
      Preconditions.checkNotNull(featureName);
      this.featureName = featureName;
    }

    /** Sets the range of versions where this feature is supported. */
    public Builder withSupportedRange(Range<Integer> versionRange) {
      Preconditions.checkNotNull(versionRange);
      supportedVersionRange = versionRange;
      return this;
    }

    /** Sets the range of versions where this feature is enabled by default. */
    public Builder withDefaultRange(Range<Integer> versionRange) {
      Preconditions.checkNotNull(versionRange);
      defaultVersionRange = versionRange;
      return this;
    }

    /** Automatically includes the given child when the config version is in the given range. */
    public Builder withChild(Feature childFeature, Range<Integer> versionRange) {
      Preconditions.checkNotNull(childFeature);
      Preconditions.checkNotNull(versionRange);
      Preconditions.checkArgument(
          childFeature.getName().startsWith(featureName + "/"),
          "Child feature name %s must start with parent feature name %s + '/'",
          childFeature.getName(),
          featureName);
      String childPart = childFeature.getName().substring(featureName.length() + 1);
      Preconditions.checkArgument(
          childPart.indexOf('/') == -1, "Child feature name must not contain '/'");
      childBindings.add(new ChildBinding(childFeature, versionRange));
      return this;
    }

    private static void checkEncloses(
        Range<Integer> outer, Range<Integer> inner, String format, Object... additionalArgs) {
      Object[] formatArgs = new Object[additionalArgs.length + 2];
      formatArgs[0] = outer;
      formatArgs[1] = inner;
      System.arraycopy(additionalArgs, 0, formatArgs, 2, additionalArgs.length);
      Preconditions.checkArgument(outer.encloses(inner) || inner.isEmpty(), format, formatArgs);
    }

    /** Returns the feature with the previously given configuration. */
    public Feature build() {
      checkEncloses(
          supportedVersionRange,
          defaultVersionRange,
          "supportedVersionRange %s does not enclose defaultVersionRange %s on %s",
          featureName);
      for (ChildBinding binding : childBindings.build()) {
        checkEncloses(
            supportedVersionRange,
            binding.range,
            "parent supportedVersionRange %s does not include binding range %s for child %s",
            binding.child.featureName);
        checkEncloses(
            binding.child.supportedVersionRange,
            binding.range,
            "child supportedVersionRange %s does not include binding range %s for child %s",
            binding.child.featureName);
      }
      return new Feature(this);
    }
  }
}
