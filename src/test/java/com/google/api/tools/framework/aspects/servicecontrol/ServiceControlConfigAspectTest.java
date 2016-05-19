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

package com.google.api.tools.framework.aspects.servicecontrol;

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ServiceControlConfigAspect}.
 */
@RunWith(JUnit4.class)

public class ServiceControlConfigAspectTest extends ConfigAspectBaselineTestCase {

  public ServiceControlConfigAspectTest() {
    super(ServiceControlConfigAspect.class);
  }

  @AfterClass
  public static void setUp() {
    // For the test purposes set maximum list length to 3 and maximum name or label length to 10.
    ServiceControlConfigValidator.configure(new ServiceControlConfigBounds.Builder().build());
  }

  @Test
  public void servicecontrolconfig() throws Exception {
    ServiceControlConfigValidator.configure(new ServiceControlConfigBounds.Builder()
        .setMaxMonitoredResources(3)
        .setMaxMetrics(3)
        .setMaxLogs(3)
        .setMaxLabels(3)
        .setMaxStringLength(10)
        .build());
    test("servicecontrolconfig");
  }
}
