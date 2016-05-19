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

import com.google.api.tools.framework.model.BoundedDiagCollector.TooManyDiagsException;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.common.collect.ImmutableMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link BoundedDiagCollector}.
 */
@RunWith(JUnit4.class)

public class BoundedDiagCollectorTest {

  private static final int ERROR_MAX_COUNT = 5;
  private static final int WARNING_MAX_COUNT = 10;
  private BoundedDiagCollector diagCollector;

  @Before
  public void before() {
    diagCollector =
        new BoundedDiagCollector(
            ImmutableMap.<Kind, Integer>builder()
                .put(Kind.ERROR, ERROR_MAX_COUNT)
                .put(Kind.WARNING, WARNING_MAX_COUNT)
                .build());
  }

  @Test
  public void testTooManyWarnings() {

    // It should stop accumulating warnings after `count` of them.
    for (int i = 0; i < WARNING_MAX_COUNT + 5; i++) {
      diagCollector.addDiag(
          Diag.warning(
              new SimpleLocation(String.format("testTooManyWarnings:%d", i)),
              "%s",
              "This is a warning message"));
    }
    // We should have `count` warnings plus the "too many" warning in the diagnostics.
    Assert.assertEquals(WARNING_MAX_COUNT + 1, diagCollector.getDiags().size());
    Assert.assertEquals(0, diagCollector.getErrorCount());
  }

  @Test
  public void testTooManyErrors() {

    try {
      for (int i = 0; i < ERROR_MAX_COUNT + 5; i++) {
        diagCollector.addDiag(
            Diag.error(
                new SimpleLocation(String.format("testTooManyErrors:%d", i)),
                "%s",
                "This is an error message"));
      }
      Assert.fail("Expected TooManyDiagsException was not thrown");
    } catch (TooManyDiagsException ex) {
      // fall through to assertions
    }

    // We should have `count` errors plus the "too many" error in the diagnostics.
    Assert.assertEquals(ERROR_MAX_COUNT + 1, diagCollector.getDiags().size());
    Assert.assertEquals(ERROR_MAX_COUNT + 1, diagCollector.getErrorCount());
  }

  @Test
  public void testTooManyMixedDiags() {
    try {
      for (int i = 0; i < WARNING_MAX_COUNT + 5; i++) {
        diagCollector.addDiag(
            Diag.warning(
                new SimpleLocation(String.format("testTooManyMixedDiags:%d", i)),
                "%s",
                "This is a warning message"));
      }

      for (int i = 0; i < ERROR_MAX_COUNT + 5; i++) {
        diagCollector.addDiag(
            Diag.error(
                new SimpleLocation(String.format("testTooManyMixedDiags:%d", i)),
                "%s",
                "This is an error message"));
      }
      Assert.fail("Expected TooManyDiagsException was not thrown");
    } catch (TooManyDiagsException ex) {
      // fall through to assertions
    }

    // We should have `warnCount` warnings plus the "too many" warning in the diagnostics, plus
    // `errorCount` errors and the "too many" error.
    Assert.assertEquals(
        WARNING_MAX_COUNT + 1 + ERROR_MAX_COUNT + 1, diagCollector.getDiags().size());
    Assert.assertEquals(ERROR_MAX_COUNT + 1, diagCollector.getErrorCount());
  }
}
