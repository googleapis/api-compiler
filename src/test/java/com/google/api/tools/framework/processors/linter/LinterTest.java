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

package com.google.api.tools.framework.processors.linter;

import com.google.api.tools.framework.model.stages.Linted;
import com.google.api.tools.framework.model.testing.ConfigBaselineTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
* Baseline tests for {@link Linter} which do not fit into a specific aspect.
*/
@RunWith(JUnit4.class)

public class LinterTest extends ConfigBaselineTestCase {

  private String warningFilter;

  @Override
  protected Object run() throws Exception {

    if (warningFilter != null) {
      model.setWarningFilter(warningFilter);
    }

    // Establish stage.
    model.establishStage(Linted.KEY);

    return null;
  }

  @Test public void ignoremapentry() throws Exception {
    warningFilter = "documentation";
    test("ignoremapentry");
  }

  @Test public void httpconfig() throws Exception {
    warningFilter = "http|versioning";
    test("httpconfig");
  }
}
