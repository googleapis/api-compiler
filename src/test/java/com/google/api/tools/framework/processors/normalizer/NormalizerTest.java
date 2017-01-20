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

package com.google.api.tools.framework.processors.normalizer;

import com.google.api.Service;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.model.testing.ConfigBaselineTestCase;

import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
* Baseline tests for {@link Normalizer}.
*/
@RunWith(JUnit4.class)

public class NormalizerTest extends ConfigBaselineTestCase {

  private boolean suppressAllWarnings = true;
  // TODO(user): because we are excluding defaults for descriptor options here in order
  // to have some more stable baseline tests, we need to add some specific UTs for just that
  // case.
  @Override
  protected Object run() throws Exception {

    if (suppressAllWarnings) {
      model.suppressAllWarnings();
    }

    // Establish stage.
    model.establishStage(Normalized.KEY);

    if (model.getDiagCollector().hasErrors()) {
      return null;
    }

    Service service = model.getNormalizedConfig();
    return toBaselineString(service);
  }

  @Before
  public void reset() {
    // Set current time to a fixed value, so that revision in the generated discovery doc will be a
    // fixed value (19700101).
    DateTimeZone.setDefault(DateTimeZone.UTC);
    DateTimeUtils.setCurrentMillisFixed(1L);
    suppressAllWarnings = true;
  }

  @After
  public void after() {
    // Reset the current time to return the system time.
    DateTimeUtils.setCurrentMillisSystem();
  }

  @Test public void normalization() throws Exception {
    test("normalization");
  }

  @Test public void custom_normalization() throws Exception {
    test("custom_normalization");
  }

  @Test public void annotations() throws Exception {
    test("annotations");
  }

  @Test public void additional_types() throws Exception {
    test("additional_types");
  }

  @Test public void invalid_additional_types() throws Exception {
    test("invalid_additional_types");
  }

  @Test public void invalid_type_wildcard() throws Exception {
    test("invalid_type_wildcard");
  }

  @Test public void no_package_name() throws Exception {
    test("no_package_name");
  }

  @Test
  public void config_warning_with_location() throws Exception {
    suppressAllWarnings = false;
    suppressionDirectives = null;
    test("config_warning_with_location");
  }

}
