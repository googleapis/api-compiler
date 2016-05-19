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

package com.google.api.tools.framework.model.testing;

import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Smoke tests for {@link BaselineTestCase}.
 */
@RunWith(JUnit4.class)

public class BaselineTestCaseTest extends BaselineTestCase {

  @Test
  public void empty() {}

  @Test
  public void failure() {
    try {
      for (int i = 0; i < 10; i++) {
        testOutput().printf("Line %d%n", i);
      }
      verify();
    } catch (BaselineComparisonError e) {
      Truth.assertThat(e.getMessage()).contains(
          "< Line 44\n"
          + "> Line 4\n");
      Truth.assertThat(e.getMessage()).contains(
          "< Line 88\n"
          + "> Line 8");
    }
  }
}
