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

package com.google.api.tools.framework.aspects.control;

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ControlConfigAspect}.
 */
@RunWith(JUnit4.class)

public class ControlConfigAspectTest extends ConfigAspectBaselineTestCase{

  public ControlConfigAspectTest() {
    super(ControlConfigAspect.class);
  }

  @Test public void missing_control_env_quota_present() throws Exception {
    test("missing_control_env_quota_present");
  }

  @Test public void missing_control_env() throws Exception {
    test("missing_control_env");
  }

  @Test public void private_service_validation() throws Exception {
    test("private_service_validation");
  }
}
