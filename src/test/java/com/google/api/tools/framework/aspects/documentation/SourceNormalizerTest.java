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

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.testing.BaselineTestCase;
import com.google.api.tools.framework.model.testing.DiagUtils;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.common.base.Joiner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Baseline tests for {@link SourceNormalizer}
 */
@RunWith(JUnit4.class)

public class SourceNormalizerTest extends BaselineTestCase {
  private static final String TESTDATA_PATH =
      SourceNormalizerTest.class.getPackage().getName().replace(".", File.separator)
      + File.separator + "testdata";

  private static final Joiner NEWLINE_JOINER = Joiner.on('\n');
  // Set element to null as the SourcecNormalizer does not use it.
  private static final ProtoElement element = null;
  private SourceNormalizer normalizer;
  private SimpleDiagCollector diagCollector;
  private TestDataLocator testDataLocator = TestDataLocator.create(getClass());

  @Before
  public void setup() {
    diagCollector = new SimpleDiagCollector();
  }

  @Test
  public void documentation_normalize() throws IOException {
    Path tempFileFullPath = null;
    String relativeFilePath = TESTDATA_PATH + "/documentation_normalize_include.md";

    tempFileFullPath = testDataLocator.getTestDataAsFile(relativeFilePath);
    String includeFile = tempFileFullPath.getFileName().toString();
    String source = createFromLines(
        "Top level line.",
        "# Header1 #",
        "Header1 content line1.",
        "Header1 content line2.",
        "",
        "## Header2.",
        "Header2 content line1.",
        "(== include " + includeFile + "==)");
    runTest(source, tempFileFullPath.getParent().toString());
  }

  @Test
  public void documentation_normalize_setextHeader() {
    String source = createFromLines(
        "SETEXT-Style Header1",
        "========",
        "Header1 content line1.",
        "SETEXT-Style Header2",
        "--------",
        "Header2 content line1.");
    runTest(source, "");
  }

  @Test
  public void documentation_normalize_invalidInclusion() {
    String invalidFilePath = TESTDATA_PATH + "/dummy.md";
    String invalidExtension = TESTDATA_PATH + "/invalid.dummy";
    String source = createFromLines(
        "Top level line.",
        "# Header1 #",
        "(== include " + invalidExtension + "==)",
        "## Header2.",
        "Header2 content line1.",
        "(== include " + invalidFilePath + "==)");
    runTest(source, "");
  }

  private String createFromLines(String... lines) {
    return NEWLINE_JOINER.join(lines);
  }

  /**
   * Runs the test for normalizing given source and compare the result with baseline result.
   */
  private void runTest(String source, String docPath) {
    normalizer = new SourceNormalizer(diagCollector, docPath);
    testOutput().println(normalizer.process(source, SimpleLocation.TOPLEVEL, element));
    for (Diag diag : diagCollector.getErrors()) {
      testOutput().println(DiagUtils.getDiagToPrint(diag, true));
    }
  }
}
