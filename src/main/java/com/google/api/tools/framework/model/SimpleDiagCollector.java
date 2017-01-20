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

package com.google.api.tools.framework.model;

import com.google.api.tools.framework.model.Diag.Kind;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple implementation of {@link DiagCollector} used for general purpose tools.
 */
public class SimpleDiagCollector implements DiagCollector {
  private final List<Diag> diags = new ArrayList<>();
  private int errorCount;

  @Override
  public void addDiag(Diag diag) {
    diags.add(diag);
    if (diag.getKind() == Kind.ERROR) {
      errorCount++;
    }
  }

  @Override
  public int getErrorCount() {
    return errorCount;
  }

  @Override
  public boolean hasErrors() {
    return getErrorCount() > 0;
  }

  @Override
  public List<Diag> getDiags() {
    return diags;
  }

  @Override
  public String toString() {
    return Joiner.on("\n").join(diags);
  }

  public List<Diag> getErrors() {
    return FluentIterable.from(diags).filter(new Predicate<Diag>() {
      @Override public boolean apply(Diag input) {
        return input.getKind() == Kind.ERROR;
      }}).toList();
  }

  /** Helper to report an error. */
  public static void error(
      DiagCollector diagCollector,
      Object elementOrLocation,
      String diagPrefix,
      String message,
      Object... params) {
    diagCollector.addDiag(
        Diag.error(getLocation(elementOrLocation), Model.diagPrefix(diagPrefix) + message, params));
  }

  /** Helper to report a warning. */
  public static void warning(
      DiagCollector diagCollector,
      DiagSuppressor diagSuppressor,
      Object elementOrLocation,
      String diagPrefix,
      String message,
      Object... params) {
    Diag warningDiag =
        Diag.warning(
            getLocation(elementOrLocation), Model.diagPrefix(diagPrefix) + message, params);
    if (!diagSuppressor.isDiagSuppressed(warningDiag, elementOrLocation)) {
      diagCollector.addDiag(warningDiag);
    }
  }

  private static Location getLocation(Object elementOrLocation) {
    if (elementOrLocation instanceof Location) {
      return (Location) elementOrLocation;
    }
    if (elementOrLocation instanceof Element) {
      return ((Element) elementOrLocation).getLocation();
    }
    return SimpleLocation.TOPLEVEL;
  }
}
