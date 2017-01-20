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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.Charset;
import junit.framework.AssertionFailedError;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * Test fixture for baseline testing.
 *
 * <p>Test cases of this class compare a recorded text with a current result text.
 * They write the result to the {@link #testOutput()} from this base class. At the end of the test
 * run, the test automatically verifies the result with the previous recorded one.
 *
 * <p>The baseline data is typically stored in a sub-directory {@code testdata} of the classes
 * source directory, or an equivalent location in the resources tree, and located via
 * {@link TestDataLocator}.
 *
 * When a comparison failure happens, this class creates a diff report and furthermore stores
 * a copy of the actual result at the location
 * {@code /tmp/packagename_testdata/testname}, from where it can be picked up
 * to update the baseline.
 */
public abstract class BaselineTestCase {

  /**
   * An interface to the differ used by the test case to display baseline differences.
   */
  public interface Differ {
    String diff(String expected, String actual);
  }

  /**
   * An error which is thrown when baseline test comparison fails.
   */
  public static class BaselineComparisonError extends AssertionFailedError {

    private final String testName;
    private final String expected;
    private final String actual;
    private final String actualLocation;
    private final String baselineFileName;

    /**
     * Constructs a baseline comparison error.
     *
     * @param testName the test which failed.
     * @param expected the expected result i.e. baseline.
     * @param actual the actual result.
     * @param actualLocation a hint where the actual result is stored.
     */
    public BaselineComparisonError(String testName, String baselineFileName,
        String expected, String actual, String actualLocation) {
      this.testName = testName;
      this.expected = expected;
      this.actual = actual;
      this.actualLocation = actualLocation;
      this.baselineFileName = baselineFileName;
    }

    @Override
    public String getMessage() {
      if (expected == null) {
        return String.format("No recorded baseline for '%s'%nFile: %s%n%s",
            testName, baselineFileName, actualLocation);
      }
      BaselineDiffer differ = new BaselineDiffer();
      String resultMessage = String.format(
          "Expected for '%s' differs from actual:%n%n\"******New baseline content is******%n%s%n"
              + "Expected File: %s%nActual File: %s%nDiff:\n%s",
          testName, actual, baselineFileName, actualLocation, differ.diff(expected, actual));
      return resultMessage;
    }

    /**
     * Returns the name of test.
     */
    public String getTestName() {
      return testName;
    }

    /**
     * Returns the expected result, i.e. baseline.
     */
    public String getExpected() {
      return expected;
    }

    /**
     * Returns the actual result.
     */
    public String getActual() {
      return actual;
    }
  }

  private static final String DEFAULT_BASELINE_SUFFIX = ".baseline";

  private static final String DIRECTORY_TO_COPY_NEW_BASELINE;
  private static final boolean RETAIN_DIRECTORY_TREE_FOR_BASELINE_OUTPUT;

  static {
    if (!Strings.isNullOrEmpty(System.getenv("COPY_BASELINE_TO_DIR"))) {
      DIRECTORY_TO_COPY_NEW_BASELINE = System.getenv("COPY_BASELINE_TO_DIR");
    } else {
      DIRECTORY_TO_COPY_NEW_BASELINE = "/tmp";
    }
  }

  static {
    String retainDirectoryTree = System.getenv("RETAIN_DIRECTORY_TREE_FOR_BASELINE_OUTPUT");
    RETAIN_DIRECTORY_TREE_FOR_BASELINE_OUTPUT = shouldRetainDirectoryTree(retainDirectoryTree);
  }

  private static boolean shouldRetainDirectoryTree(String retainDirectoryTree) {
    return !Strings.isNullOrEmpty(retainDirectoryTree) && isTrue(retainDirectoryTree);
  }

  private static boolean isTrue(String str) {
    return str.trim().toLowerCase().equals("true");
  }

  @Rule public TestName testName = new TestName();

  private OutputStream output;
  private PrintWriter writer;
  private boolean isVerified;

  private final TestDataLocator testDataLocator = TestDataLocator.create(this.getClass());

  /**
   * Returns a print writer to which test results are written.
   */
  protected PrintWriter testOutput() {
    return writer;
  }

  /**
   * Returns the underlying output stream to which test results are written.
   */
  protected OutputStream testOutputStream() {
    return output;
  }

  /**
   * Returns test data locator used by this test. Can be overridden to change how test data
   * is found.
   */
  protected TestDataLocator getTestDataLocator() {
    return testDataLocator;
  }

  /**
   * Setups the test case.
   */
  @Before public void before() throws Exception {
    output = new ByteArrayOutputStream();
    writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(output, UTF_8)));
  }

  /**
   * A test watcher which calls baseline verification if the test succeeded. This is like @After,
   * but verification is only done if there haven't been other errors.
   */
  @Rule public TestWatcher failerWatcher = new TestWatcher() {
    @Override
    protected void succeeded(Description d) {
      verify();
    }
  };

  /**
   * Gets the relative name the base line data file should have. Can be overriden.
   */
  protected String baselineFileName() {
    return testName.getMethodName() + DEFAULT_BASELINE_SUFFIX;
  }

  /**
   * Verifies the recorded content against the baseline.
   */
  protected void verify() {
    if (isVerified) {
      return;
    }
    try {
      isVerified = true;
      writer.flush();
      output.flush();
      String actual = new String(((ByteArrayOutputStream) output).toByteArray(), UTF_8);

      URL expectedUrl = getTestDataLocator().findTestData(baselineFileName());
      String expected = expectedUrl != null ? getTestDataLocator().readTestData(expectedUrl) : null;
      if (expected == null) {
        String actualLocation = tryCreateNewBaseline(actual);
        throw new BaselineComparisonError(
            testName.getMethodName(), baselineFileName(), null, actual, actualLocation);
      }
      if (!expected.trim().equals(actual.trim())) {
        String actualLocation = tryCreateNewBaseline(actual);
        throw new BaselineComparisonError(testName.getMethodName(), baselineFileName(),
            expected, actual, actualLocation);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a baseline file that will need to be used to make the current test pass.
   *
   * <p>If the test is failing for a valid reason (e.g. developer changed some of the output text),
   * then this file provides a convenient way for the developer to overwrite the old baseline
   * and keep the test passing.
   *
   *  <p>The created file is stored under /tmp or location specified by the environment
   *  variable DIRECTORY_TO_COPY_NEW_BASELINE. Information where the file is stored is returned
   *  as a string.
   *  <p>The directory structure of the package where the baseline file belongs can be retained by
   *  setting environment variable RETAIN_DIRECTORY_TREE_FOR_BASELINE_OUTPUT to true. If this
   *  variable is not set, the new baseline file is dropped inside a folder name
   *  {package name}_testdata
   *
   *  <p>This method might be overridden to provide a different way to store a new baseline.
   */
  protected String tryCreateNewBaseline(String actual) throws IOException {
    File file = new File(File.separator + DIRECTORY_TO_COPY_NEW_BASELINE
        + File.separator + getSubDirectoryPathForNewBaseline() + File.separator
        + baselineFileName());
    Files.createParentDirs(file);
    Files.asCharSink(file, Charset.defaultCharset()).write(actual);
    return file.toString();
  }

  private String getSubDirectoryPathForNewBaseline() {
    if (RETAIN_DIRECTORY_TREE_FOR_BASELINE_OUTPUT) {
      return Joiner.on(File.separator)
              .join(Splitter.on(".").split(this.getClass().getPackage().getName()))
          + File.separator
          + "testdata";

    } else {
      return this.getClass().getPackage().getName() + "_testdata";
    }
  }
}
