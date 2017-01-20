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
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.collect.Lists;
import java.util.List;

/**
 * An {@link DiagCollector} with default logic for locations in Swagger files.
 */
public class SwaggerImporterDiagCollector implements DiagCollector {

  private final List<Diag> diags = Lists.newArrayList();
  private final Location swaggerFileLocation;
  private final String swaggerFileName;
  private int errorCount = 0;

  public SwaggerImporterDiagCollector(String swaggerFileName) {
    this.swaggerFileName = swaggerFileName;
    this.swaggerFileLocation = new SimpleLocation(swaggerFileName);
  }

  public void appendAllDiags(DiagCollector diagCollector) {
    diags.addAll(diagCollector.getDiags());
    errorCount += diagCollector.getErrorCount();
  }

  /**
   * Accumulates errors and warning encountered during import.
   */
  @Override
  public void addDiag(Diag diag) {
    Location loc = SimpleLocation.UNKNOWN;
    if (diag.getLocation() == SimpleLocation.UNKNOWN
        || diag.getLocation() == SimpleLocation.TOPLEVEL) {
      loc = swaggerFileLocation;
    } else {
      loc = new SimpleLocation(
          String.format("%s: %s", swaggerFileName, diag.getLocation().toString()));
    }
    diag = Diag.create(loc, diag.getMessage(), diag.getKind());

    diags.add(diag);
    if (diag.getKind() == Diag.Kind.ERROR) {
      errorCount++;
    }
  }

  /**
   * Returns the number of errors and warnings.
   */
  @Override
  public int getErrorCount() {
    return errorCount;
  }

  /**
   * Returns true if there are any diagnosed proper errors; false otherwise
   */
  @Override
  public boolean hasErrors() {
    return getErrorCount() > 0;
  }

  /**
   * Returns the diagnosis accumulated.
   */
  @Override
  public List<Diag> getDiags() {
    return diags;
  }
}

