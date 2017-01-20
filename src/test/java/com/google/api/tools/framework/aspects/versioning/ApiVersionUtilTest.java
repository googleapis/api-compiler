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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.tools.framework.aspects.versioning.model.ApiVersionUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ApiVersionUtil}.
 */
@RunWith(JUnit4.class)

public class ApiVersionUtilTest {
  @Test
  public void testExtractDefaultMajorVersionFromPackageName() {
    assertEquals("v2",
        ApiVersionUtil.extractDefaultMajorVersionFromPackageName("google.feature.v2"));
    assertEquals("v2b1",
        ApiVersionUtil.extractDefaultMajorVersionFromPackageName("google.feature.v2b1"));
    assertEquals("v1",
        ApiVersionUtil.extractDefaultMajorVersionFromPackageName("google.feature"));
    assertEquals("v1",
        ApiVersionUtil.extractDefaultMajorVersionFromPackageName("google.v2.feature"));
  }

  @Test
  public void textExtractMajorVersionFromSemanticVersion() {
    // Valid semantic versions
    assertEquals("v2",
        ApiVersionUtil.extractMajorVersionFromSemanticVersion("v2"));
    assertEquals("v2b1",
        ApiVersionUtil.extractMajorVersionFromSemanticVersion("v2b1"));
    assertEquals("v2beta1",
        ApiVersionUtil.extractMajorVersionFromSemanticVersion("v2beta1"));
    assertEquals("v2b1",
        ApiVersionUtil.extractMajorVersionFromSemanticVersion("v2b1.10"));
    assertEquals("v2",
        ApiVersionUtil.extractMajorVersionFromSemanticVersion("v2.10.3"));
    assertEquals("2", ApiVersionUtil.extractMajorVersionFromSemanticVersion("2"));

    // Invalid semantic versions
    assertNull(ApiVersionUtil.extractMajorVersionFromSemanticVersion("V2"));

    assertNull(ApiVersionUtil.extractMajorVersionFromSemanticVersion("abc"));
    assertNull(ApiVersionUtil.extractMajorVersionFromSemanticVersion("v1.2.3.4"));
    assertNull(ApiVersionUtil.extractMajorVersionFromSemanticVersion(""));
  }

  @Test
  public void testIsValidApiVersion() {
    // Valid api version
    assertTrue(ApiVersionUtil.isValidApiVersion("v2"));
    assertTrue(ApiVersionUtil.isValidApiVersion("v2b1"));
    assertTrue(ApiVersionUtil.isValidApiVersion("v2.10"));
    assertTrue(ApiVersionUtil.isValidApiVersion("v2.10.3"));
    assertTrue(ApiVersionUtil.isValidApiVersion("2"));

    // Invalid api version
    assertTrue(!ApiVersionUtil.isValidApiVersion("abc"));
    assertTrue(!ApiVersionUtil.isValidApiVersion("v1.2.3.4"));
    assertTrue(!ApiVersionUtil.isValidApiVersion(""));
  }
}
