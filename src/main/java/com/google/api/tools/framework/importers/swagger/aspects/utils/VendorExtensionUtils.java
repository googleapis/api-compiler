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

package com.google.api.tools.framework.importers.swagger.aspects.utils;

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Utilities for fetching Vendor Extensions from Swagger. */
public class VendorExtensionUtils {

  /** Returns name of the vendor extension used. */
  public static <T> String usedExtension(
      DiagCollector diagCollector,
      Map<String, Object> vendorExtensions,
      String extensionName,
      String... legacyNamesForExtension) {
    if (vendorExtensions != null) {
      warnOnLegacyExtensions(
          legacyNamesForExtension, vendorExtensions, extensionName, diagCollector);

      Iterable<String> allNamesForExtension =
          ImmutableList.<String>builder().add(legacyNamesForExtension).add(extensionName).build();

      List<String> usedExtensionNames =
          getExtensionsNamesUsed(vendorExtensions, allNamesForExtension);

      if (usedExtensionNames.size() > 1) {
        diagCollector.addDiag(
            Diag.error(
                new SimpleLocation(extensionName),
                "OpenAPI spec is invalid since multiple "
                    + "extension definitions '%s' for the same extension are used. Please provide "
                    + "only one extension definition with name '%s'.",
                Joiner.on(",").join(usedExtensionNames),
                extensionName));
        return "";
      } else if (usedExtensionNames.size() == 1) {
        return Iterables.getOnlyElement(usedExtensionNames);
      }
    }
    return "";
  }

  @Nullable
  public static <T> T getExtensionValue(
      Map<String, Object> extensions,
      Class<T> clazz,
      DiagCollector diagCollector,
      String extensionName) {
    Object extensionValue = extensions.get(extensionName);
    Preconditions.checkNotNull(extensionValue, "extensionValue should not be null");
    if (clazz.isInstance(extensionValue)) {
      return clazz.cast(extensionValue);
    } else {
      diagCollector.addDiag(
          Diag.error(
              new SimpleLocation(extensionName),
              "Extension %s has invalid type. Valid type is %s",
              extensionName,
              clazz.getName()));
      return null;
    }
  }

  private static List<String> getExtensionsNamesUsed(
      Map<String, Object> vendorExtensions, Iterable<String> allNamesForExtensions) {
    List<String> result = Lists.newArrayList();
    for (String extensionName : allNamesForExtensions) {
      Object extensionValue = vendorExtensions.get(extensionName);
      if (extensionValue != null) {
        result.add(extensionName);
      }
    }

    return result;
  }

  private static void warnOnLegacyExtensions(
      String[] legacyNamesForExtension,
      Map<String, Object> vendorExtensions,
      String extensionName,
      DiagCollector diagCollector) {
    for (String legacyNameForExtension : legacyNamesForExtension) {
      Object extensionValue = vendorExtensions.get(legacyNameForExtension);
      if (extensionValue != null) {
        diagCollector.addDiag(
            Diag.warning(
                new SimpleLocation(legacyNameForExtension),
                "Extension name %s has been deprecated, please rename it to %s.",
                legacyNameForExtension,
                extensionName));
      }
    }
  }
}
