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

package com.google.api.tools.framework.importers.swagger;

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;

import java.util.Map;

class VendorExtensionUtils {

  static final String X_GOOGLE_ALLOW = "x-google-allow";

  /**
   * Returns whether vendor extension contains the particular extension.
   */
  static <T> boolean hasExtension(
      Map<String, Object> vendorExtensions,
      String extensionName,
      Class<T> clazz,
      DiagCollector diagCollector) {
    if (vendorExtensions != null) {
      Object extensionValue = vendorExtensions.get(extensionName);
      if (extensionValue != null) {
        if (clazz.isInstance(extensionValue)) {
          return true;
        } else {
          diagCollector.addDiag(
              Diag.error(
                  new SimpleLocation(extensionName),
                  "Extension %s has invalid type. Valid type is %s",
                  extensionName,
                  clazz.getName()));
          return false;
        }
      }
    }
    return false;
  }
}
