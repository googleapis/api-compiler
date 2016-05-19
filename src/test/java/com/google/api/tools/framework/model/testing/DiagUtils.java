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

package com.google.api.tools.framework.model.testing;

import com.google.api.tools.framework.model.Diag;

/**
 * Utility class to handle {@link Diag} printing for baseline testing.
 */
public class DiagUtils {

  // Gets the string representation of diag.
  public static String getDiagToPrint(final Diag diag, boolean trimLocationFullPath) {
    String message = DiagUtils.getDiagMessage(diag);
    return String.format(
        "%s: %s: %s",
        diag.getKind().toString(),
        trimLocationFullPath
            ? getLocationWithoutFullPath(diag)
            : diag.getLocation().getDisplayString(),
        message);
  }

  private static String getLocationWithoutFullPath(final Diag diag) {
    // /tmp/temp_dir/testFile.json becomes testFile.json
    String location = diag.getLocation().getDisplayString();
    int lastSlashIndex = location.lastIndexOf("/");
    if (lastSlashIndex != -1) {
      return location.substring(lastSlashIndex + 1);
    }
    return location;
  }

  // Gets the message part of the diag.
  public static String getDiagMessage(final Diag diag) {
    String message = diag.getMessage();
    return message;
  }

}
