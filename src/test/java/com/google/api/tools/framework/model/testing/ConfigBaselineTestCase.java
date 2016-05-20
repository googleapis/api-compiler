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

import com.google.api.DocumentationRule;
import com.google.api.Service;
import com.google.api.Service.Builder;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.snippet.Doc;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import com.google.protobuf.MessageOrBuilder;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A baseline test case which prepares a model from proto and yaml config
 * and handles printing the result of a test run to the baseline.
 */
public abstract class ConfigBaselineTestCase extends BaselineTestCase {

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Package names of known system core types.
   */
  private static final FluentIterable<String> SYSTEM_TYPE_PACKAGE_NAMES =
      FluentIterable.from(Lists.newArrayList("google.protobuf"));

  /**
   * The test configuration.
   */
  protected TestConfig testConfig;

  /**
   * Determine if location information from the {@link Diag} should be printed in the baseline
   * files.
   */
  protected boolean showDiagLocation = true;

  /**
   * List of suppression directives that should be added to the model.
   */
  protected List<String> suppressionDirectives = Lists.newArrayList("versioning-config");

  /**
   * The model on which the test runs.
   */
  protected Model model;

  /**
   * A text formatter which converts Any to clear text for baselines. Add any instances
   * which should be converted via
   * {@link TextFormatForTest#registerAnyInstance(String, Message)}.
   */
  protected TextFormatForTest formatter =
      new TextFormatForTest();

  /**
   * Run test specific logic. The returned object will be printed to the baseline if not null.
   * The object can be a map from string to object, in which case the map will be decomposed
   * for the baseline output. If a {@link Doc} appears it will be pretty printed before
   * writing it.
   */
  @Nullable protected abstract Object run() throws Exception;

  /**
   * Configures standard setup of processors and config aspects. Override to use a non-standard
   * setup.
   */
  protected void setupModel() {
    StandardSetup.registerStandardProcessors(model);
    StandardSetup.registerStandardConfigAspects(model);
  }

  /**
   * Whether to suppress outputting diags to the baseline file.
   */
  protected boolean suppressDiagnosis() {
    return false;
  }

  /**
   * Returns the test config.
   */
  protected final TestConfig getTestConfig() {
    return testConfig;
  }

  /**
   * Run a test for the given file base name(s). Collects all .proto and .yaml files with the given
   * base name (i.e. baseName.proto or baseName.yaml), constructs model, and calls {@link #run()}.
   * Post that, prints diags and the result of the run to the baseline.
   */
  protected void test(String... baseNames) throws Exception {
    // Determine proto and yaml files.
    List<String> protoFiles = Lists.newArrayList();
    List<String> yamlFiles = Lists.newArrayList();
    for (String baseName : baseNames) {
      String name = baseName + ".proto";
      URL url = getTestDataLocator().findTestData(name);
      if (url != null) {
        protoFiles.add(name);
      }
      name = baseName + ".yaml";
      url = getTestDataLocator().findTestData(name);
      if (url != null) {
        yamlFiles.add(name);
      }
    }
    if (protoFiles.isEmpty()) {
      throw new IllegalArgumentException("No proto files found");
    }
    this.testConfig = new TestConfig(getTestDataLocator(), tempDir.getRoot().getPath(), protoFiles);
    this.model = testConfig.createModel(yamlFiles);

    // Setup
    setupModel();
    if (suppressionDirectives !=  null) {
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
      testOutput().printf(
          String.format("%s: %s: %s", diag.getKind().toString(),
              getLocationWithoutFullPath(diag), message)
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

  /**
   * Fetches content from various values for a content source (File, Doc, etc.)
   */
  private String displayValue(Object value) throws IOException {
    if (value instanceof Doc) {
      return ((Doc) value).prettyPrint(100);
    } else if (value instanceof File) {
      return Files.toString((File) value, StandardCharsets.UTF_8);
    } else if (value instanceof MessageOrBuilder) {
      // Convert proto to text format, considering any instances.
      return formatter.printToString((MessageOrBuilder) value);
    } else {
      return value.toString();
    }
  }

  /**
   * Takes a service configuration and converts it into a string suitable for baseline
   * tests. This sanitizes the config scrubbing empty configuration sections, which are
   * semantically irrelevant.
   */
  public String toBaselineString(Service config) {
    Service.Builder builder = config.toBuilder();
    clearSystemDataDocumentation(builder);
    for (FieldDescriptor field : Service.getDescriptor().getFields()) {
      clearIfEmptyMessage(builder, field);
    }
    return formatter.printToString(builder);
  }

  public static Builder clearSystemDataDocumentation(Builder builder) {
    List<DocumentationRule> documentationRules = builder.getDocumentationBuilder().getRulesList();
    builder.getDocumentationBuilder().clearRules();
    for (com.google.api.DocumentationRule docRule : documentationRules) {
      final String selector = docRule.getSelector();
      if (!SYSTEM_TYPE_PACKAGE_NAMES.anyMatch(
          new Predicate<String>() {
            @Override
            public boolean apply(String systemTypePackageName) {
              return selector.startsWith(systemTypePackageName);
            }
          })) {
        builder.getDocumentationBuilder().addRules(docRule);
      }
    }
    return builder;
  }

  private static void clearIfEmptyMessage(Service.Builder builder, FieldDescriptor field) {
    if (!field.isRepeated() && field.getType() == FieldDescriptor.Type.MESSAGE
        && ((Message) builder.getField(field)).getAllFields().size() == 0) {
      builder.clearField(field);
    }
  }
}
