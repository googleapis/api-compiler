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

import com.google.api.Service;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.testing.TestModelGenerator.ModelTestInfo;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.snippet.Doc;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.protobuf.MessageOrBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

/**
 * A baseline test case which prepares a model from proto and yaml config and handles printing the
 * result of a test run to the baseline.
 */
public abstract class ConfigBaselineTestCase extends BaselineTestCase {

  private static final ImmutableList<String> EXPERIMENTS_ENABLED_FOR_TESTS =
      ImmutableList.of("use-new-visibility-derived-data");

  private final List<String> experiments = Lists.newArrayList(EXPERIMENTS_ENABLED_FOR_TESTS);

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /** The test configuration. */
  protected TestConfig testConfig;

  /**
   * Determine if location information from the {@link Diag} should be printed in the baseline
   * files.
   */
  protected boolean showDiagLocation = true;

  /** List of suppression directives that should be added to the model. */
  protected List<String> suppressionDirectives = Lists.newArrayList("versioning-config");

  /** The model on which the test runs. */
  protected Model model;

  /**
   * A text formatter which converts Any to clear text for baselines. Add any instances which should
   * be converted via {@link TextFormatForTest#registerAnyInstance(String, Message)}.
   */
  protected TextFormatForTest formatter =
      new TextFormatForTest();

  /**
   * Run test specific logic. The returned object will be printed to the baseline if not null. The
   * object can be a map from string to object, in which case the map will be decomposed for the
   * baseline output. If a {@link Doc} appears it will be pretty printed before writing it.
   */
  @Nullable
  protected abstract Object run() throws Exception;

  /**
   * Configures standard setup of processors and config aspects. Override to use a non-standard
   * setup.
   */
  protected void setupModel() {
    StandardSetup.registerStandardProcessors(model);
    StandardSetup.registerStandardConfigAspects(model);
  }

  /** Whether to suppress outputting diags to the baseline file. */
  protected boolean suppressDiagnosis() {
    return false;
  }

  /**
   * Add the given experiment on the model.
   */
  public void enableExperiment(String experiment) {
    experiments.add(experiment);
  }

  /** Returns the test config. */
  protected final TestConfig getTestConfig() {
    return testConfig;
  }

  /**
   * Run a test, using defaults for proto compilation, etc.
   */
  protected void test(String... baseNames) throws Exception {
    test(new TestModelGenerator(getTestDataLocator(), tempDir), baseNames);
  }

  /**
   * Run a test for the given file base name(s). Collects all .proto and .yaml files with the given
   * base name (i.e. baseName.proto or baseName.yaml), constructs model, and calls {@link #run()}.
   * Post that, prints diags and the result of the run to the baseline.
   */
  protected void test(
      TestModelGenerator testModelGenerator, String... baseNames) throws Exception {
    test(testModelGenerator, Arrays.asList(baseNames));
  }
  protected void test(
      TestModelGenerator testModelGenerator, Iterable<String> baseNames) throws Exception {
    String firstBaseName = baseNames.iterator().next();
    ModelTestInfo modelTestInfo = testModelGenerator.buildModel(baseNames);
    this.model = modelTestInfo.getModel();
    this.testConfig = modelTestInfo.getTestConfig();

    // Setup
    setupModel();

    // Enable the experiments on the model.
    for (String experiment : experiments) {
      model.enableExperiment(experiment);
    }

    if (suppressionDirectives != null) {
      for (String suppressionDirective : suppressionDirectives) {
        model.addSupressionDirective(model, suppressionDirective);
      }
    }

    // Run test specific logic.
    Object result = run();

    // Output diag into baseline file.
    if (!suppressDiagnosis()) {
      for (Diag diag : model.getDiagCollector().getDiags()) {
        printDiag(diag);
      }
    }

    if (!model.getDiagCollector().hasErrors() && result != null) {
      // Output the result depending on its type.
      if (result instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
          testOutput().printf("============== file: %s ==============%n", entry.getKey());
          testOutput().println(displayValue(entry.getValue()));
        }
      } else {
        testOutput().println(displayValue(result));
      }
    }
  }

  /**
   * Prints diag to the testOutput.
   *
   */
  protected void printDiag(final Diag diag) {
    String message = DiagUtils.getDiagMessage(diag);
    if (showDiagLocation) {
      testOutput()
          .printf(
              String.format(
                      "%s: %s: %s",
                      diag.getKind().toString(), getLocationWithoutFullPath(diag), message)
                  + "%n");
    } else {
      testOutput().printf("%s: %s%n", diag.getKind(), message);
    }
  }

  private String getLocationWithoutFullPath(final Diag diag) {
    String location = diag.getLocation().getDisplayString();
    int firstSlashIndex = location.indexOf("/");
    int lastSlashIndex = location.lastIndexOf("/");
    if (firstSlashIndex != -1) {
      String toReplace = location.substring(firstSlashIndex, lastSlashIndex + 1);
      location = location.replace(toReplace, "");
    }
    return location;
  }

  /** Fetches content from various values for a content source (File, Doc, etc.) */
  private String displayValue(Object value) throws IOException {
    if (value instanceof Doc) {
      return ((Doc) value).prettyPrint(100);
    } else if (value instanceof File) {
      return Files.asCharSource((File) value, StandardCharsets.UTF_8).read();
    } else if (value instanceof MessageOrBuilder) {
      // Convert proto to text format, considering any instances.
      return formatter.printToString((MessageOrBuilder) value);
    } else {
      return value.toString();
    }
  }

  /**
   * Takes a service configuration and converts it into a string suitable for baseline tests. This
   * sanitizes the config scrubbing empty configuration sections, which are semantically irrelevant.
   */
  public String toBaselineString(Service config) {
    Service.Builder builder = ServiceConfigTestingUtil.clearIrrelevantData(config.toBuilder());
    return formatter.printToString(builder);
  }
}
