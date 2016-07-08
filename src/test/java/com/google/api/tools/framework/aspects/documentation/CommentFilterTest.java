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

package com.google.api.tools.framework.aspects.documentation;

import com.google.api.tools.framework.aspects.documentation.model.DocumentationUtil;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.Truth;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Set;

/**
 * Tests for {@link DocumentationUtil#filter}.
 */
@RunWith(JUnit4.class)
public class CommentFilterTest {
  private static final Joiner NEWLINE_JOINER = Joiner.on('\n');
  // Set element to null as the CommentFilter does not use it.
  private final SimpleDiagCollector diagCollector = new SimpleDiagCollector();
  private final Set<String> visibilityLabels = Sets.newHashSet("TRUSTED_TESTER");
  private final Location location = SimpleLocation.TOPLEVEL;

  private String filter(String comment, Location location) {
    return DocumentationUtil.filter(diagCollector, visibilityLabels, location, comment);
  }

  @Test
  public void testFilter_noTags() {
    String comment = "";
    assertResult("", filter(comment, location));

    comment = "No tags";
    assertResult(comment, filter(comment, location));
  }

  @Test
  public void testFilter_oneSingleTag() {
    String comment = "(--One single \ntag--)";
    assertResult("", filter(comment, location));
  }

  @Test
  public void testFilter_trimSpaces() {
    String comment = "Start of line\n(-- internal --) test";
    assertResult("Start of line\ntest", filter(comment, location));

    comment = "End of line(-- internal1 --) ";
    assertResult("End of line", filter(comment, location));

    comment = "In the middle  (-- internal --) (-- internal2 --) test";
    assertResult("In the middle test", filter(comment, location));
  }

  @Test
  public void testFilter_multiplePerLine() {
    String comment = "Multiple tags (-- internal1 --) test (-- internal2 --) ";
    assertResult("Multiple tags test", filter(comment, location));

    comment = "(-- internal1 --) Multiple tags (-- internal2 --) test";
    assertResult("Multiple tags test", filter(comment, location));
  }

  @Test
  public void testFilter_multipleLines() {
    String comment = "Multiple lines (-- line1\nline2\n line3 --) test";
    assertResult("Multiple lines test", filter(comment, location));
  }

  @Test
  public void testFilter_removeInternalOnlyLines() {
    String comment = "Single line\n (--internal1--)\ntest";
    assertResult("Single line\ntest", filter(comment, location));

    comment = "Multiple lines (-- internal1 \n internal2 --)\ntest";
    assertResult("Multiple lines test", filter(comment, location));

    comment = "Keep reserved newline\n (-- internal --)\n\ntest";
    assertResult("Keep reserved newline\n\ntest", filter(comment, location));
  }

  @Test
  public void testFilter_withVisibility() {
    String comment = "Single line\n (--TRUSTED_TESTER: trusted tester--)\ntest";
    assertResult("Single line\ntrusted tester\ntest", filter(comment, location));

    comment = "Single line\n (--OTHER_SCOPE: trusted tester--)\ntest";
    assertResult("Single line\ntest", filter(comment, location));
  }

  @Test
  public void testFilter_nestedTags() {
    String comment = "Nested tag (-- \n(--TRUSTED_TESTER: valid nested --) --)";
    assertResult("Nested tag valid nested", filter(comment, location));
  }

  @Test
  public void testFilter_escapedNotInternal() {
    String comment = "hello \\(-- not internal \\--) world";
    assertResult("hello (-- not internal --) world", filter(comment, location));
  }

  @Test
  public void testFilter_escapeWithinInternal() {
    String comment = "hello (-- internal \\--) still internal --) world";
    assertResult("hello world", filter(comment, location));
  }

  @Test
  public void testFilter_missingStartTag() {
    filter("Missing \n start tag --)", location);
    expectError("ERROR: toplevel (at document line 2): Unexpected end tag '--)' with missing "
        + "begin tag.");
  }

  @Test
  public void testFilter_missingEndTag() {
    filter("Missing end tag (--", location);
    expectError("ERROR: toplevel (at document line 1): Did not find associated end tag for the "
        + "begin tag '(--'");
  }

  @Test
  public void testFilter_escapedMissingEndTag() {
    filter("\\(-- error --)", location);
    expectError("ERROR: toplevel (at document line 1): Unexpected end tag '--)' with missing "
        + "begin tag.");
  }

  /**
   * Asserts if filtered result equals expected string. Asserts failure if there are errors detected
   * during the filtering.
   */
  private void assertResult(String expected, String result) {
    if (diagCollector.getErrorCount() > 0) {
      Truth.assertWithMessage("Errors detected while filtering comment")
          .fail(NEWLINE_JOINER.join(diagCollector.getErrors()));
    } else {
      Truth.assertThat(result).isEqualTo(expected);
    }
  }

  private void expectError(String message) {
    if (diagCollector.getErrorCount() == 0) {
      Truth.assertWithMessage("No error found.").fail("Expected error message %s", message);
    } else if (diagCollector.getErrorCount() > 1) {
      Truth.assert_().fail("Found more than one error: \n%s",
          NEWLINE_JOINER.join(diagCollector.getErrors()));
    } else {
      Truth.assertThat(Iterables.getOnlyElement(diagCollector.getErrors()).toString())
          .isEqualTo(message);
    }
  }

}
