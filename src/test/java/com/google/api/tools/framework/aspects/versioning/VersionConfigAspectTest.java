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

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link VersionConfigAspect}.
 */
@RunWith(JUnit4.class)

public class VersionConfigAspectTest extends ConfigAspectBaselineTestCase {

  @Before
  public void setup() {
    showDiagLocation = false;
  }

  public VersionConfigAspectTest() {
    super(VersionConfigAspect.class);
  }

  @Test public void emptyconfigversion() throws Exception {
    test("emptyconfigversion");
  }

  @Test public void apiversion() throws Exception {
    test("apiversion");
  }

  @Test public void invalidconfigversion() throws Exception {
    showDiagLocation = true;
    test("invalidconfigversion");
  }

  @Test public void httpwarnings() throws Exception {
    showDiagLocation = true;
    test("httpwarnings");
  }
}
