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

package com.google.api.tools.framework.aspects.versioning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.tools.framework.aspects.versioning.model.VersionComparator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link VersionComparator}.
 */
@RunWith(JUnit4.class)

public class VersionComparatorTest {
  private final VersionComparator versionComparator = new VersionComparator();

  @Test
  public void testEqualVersions() {
    assertEquals(0, versionComparator.compare("v1", "v1"));
    assertEquals(0, versionComparator.compare("v2beta3", "v2beta3"));
  }

  @Test
  public void testDistinguishMajorVersion() {
    compareVersion("v2", "v1");
    compareVersion("v11", "v2");
  }

  @Test
  public void testDistinguishMinorVersion() {
    compareVersion("v1", "v1beta1");
    compareVersion("v1beta1", "v1alpha1");
    compareVersion("v1beta2", "v1alpha1");
    compareVersion("v1beta2", "v1alpha3");
    compareVersion("v1beta2", "v1beta1");
    compareVersion("v1beta11", "v1beta2");
    compareVersion("v1alpha1b", "v1alpha1a");
    compareVersion("v1alpha1a", "v1alpha1");
    compareVersion("v1alpha11a", "v1alpha1a");
  }

  @Test
  public void testMajorPreceedsMinor() {
    compareVersion("v2alpha1", "v1");
  }

  private void compareVersion(String version1, String version2) {
    assertTrue("Expected '" + version1 + "' to be greater than '" + version2 + "'",
        versionComparator.compare(version1, version2) > 0);
    assertTrue("Expected '" + version2 + "' to be less than '" + version1 + "'",
        versionComparator.compare(version2, version1) < 0);
  }
}
