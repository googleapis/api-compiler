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

package com.google.api.tools.framework.tools.configgen;

import com.google.api.Service;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.stages.Normalized;
import com.google.api.tools.framework.model.testing.BaselineTestCase;
import com.google.api.tools.framework.model.testing.DiagUtils;
import com.google.api.tools.framework.model.testing.ServiceConfigTestingUtil;
import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.api.tools.framework.model.testing.TextFormatForTest;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.api.tools.framework.tools.ToolOptions;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Baseline tests for {@link ConfigGeneratorTool}.
 */
@RunWith(JUnit4.class)

public class ConfigGeneratorToolTest extends BaselineTestCase {
  private static final class ConfigGeneratorDriverForTest extends ConfigGeneratorDriver {

    protected ConfigGeneratorDriverForTest(ToolOptions options) {
      super(options);
    }

    public void runTest() throws IOException {
      run();
    }
  }

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Rule
  public TemporaryFolder extensionTempDir = new TemporaryFolder();

  @Test
  public void libraryConfigVersion0Test() throws Exception {
    test("library_config_version_0", null, false);
  }

  @Test
  public void libraryConfigVersion1Test() throws Exception {
    test("library_config_version_1", null, false);
  }

  @Test
  public void libraryConfigVersion2Test() throws Exception {
    test("library_config_version_2", null, false);
  }

  @Test
  public void libraryAbnormalDnsTest() throws Exception {
    test("library_abnormal_dns", null, true);
  }

  /**
   * A test to make sure that all lint warnings get suppressed when suppress_warnings flag is set to
   * true. There are several improper configuration in the proto and yaml, which is intended to
   * trigger lint warnings. There is still one warning being printed out in the baseline, which is
   * not emitted by linter.
   */
  @Test
  public void suppressWarningsTest() throws Exception {
    test("suppress_warnings", null, true);
  }

  private void test(String baseName, @Nullable String extensionName, boolean outputWarnings,
      List<String> experiments) throws Exception {
    TestDataLocator locator = TestDataLocator.create(getClass());

    ImmutableList.Builder<String> protoFilesBuilder = ImmutableList.builder();
    protoFilesBuilder.add(baseName + ".proto");
    TestConfig testConfig = new TestConfig(locator, tempDir.getRoot().getPath(),
        protoFilesBuilder.build());

    TestConfig extensionTestConfig = null;
    if (extensionName != null) {
      extensionTestConfig = new TestConfig(locator, extensionTempDir.getRoot().getPath(),
          ImmutableList.of(extensionName + ".proto"));
    }

    List<String> configFiles = new ArrayList<>();
    configFiles.add(baseName + ".yaml");
    final TestConfig testConfigCopy = testConfig;
    configFiles = FluentIterable.from(configFiles).transform(
        new Function<String, String>() {
          @Override
          public String apply(String file) {
            return testConfigCopy.copyTestDataAndGetPath(file).toString();
          }
        }).toList();

    ToolOptions options = ToolOptions.create();
    options.set(
        ConfigGeneratorDriver.TXT_OUT,
        (new File(tempDir.getRoot().getPath(), "textout")).getAbsolutePath());
    options.set(
        ConfigGeneratorDriver.BIN_OUT,
        (new File(tempDir.getRoot().getPath(), "binout")).getAbsolutePath());
    options.set(
        ConfigGeneratorDriver.JSON_OUT,
        (new File(tempDir.getRoot().getPath(), "jsonout")).getAbsolutePath());
    options.set(ToolOptions.DESCRIPTOR_SET, testConfig.getDescriptorFile().toString());
    options.set(ToolOptions.CONFIG_FILES, configFiles);
    options.set(
        ConfigGeneratorFromProtoDescriptor.SUPPRESS_WARNINGS, isSuppressWarningsTest(baseName));
    options.set(ToolOptions.EXPERIMENTS, experiments);
    if (extensionTestConfig != null) {
      options.set(
          ToolOptions.EXTENSION_DESCRIPTOR_SET, extensionTestConfig.getDescriptorFile().toString());
    }

    if (isLibraryWithError(baseName)) {
      options.set(ConfigGeneratorFromProtoDescriptor.NAME, "foobar");
    }
    if (isDnsNameTest(baseName)){
      options.set(ConfigGeneratorFromProtoDescriptor.NAME, "foobar");
    }
    if (isSuppressWarningsTest(baseName)){
      options.set(ConfigGeneratorFromProtoDescriptor.NAME, "test");
    }
    ConfigGeneratorDriverForTest tool = new ConfigGeneratorDriverForTest(options);
    tool.runTest();
    if (!tool.hasErrors()) {
      // Output diag into baseline file, if needed.
      if (outputWarnings) {
        printDiags(tool.getDiags(), true);
      }
      Service.Builder serviceBuilder =
          ServiceConfigTestingUtil.clearIrrelevantData(tool.getServiceConfig().toBuilder());
      testOutput().println(
          "============== file: normalized config without derived data ==============");
      testOutput().println(TextFormatForTest.INSTANCE.printToString(serviceBuilder.build()));

      // Regenerate the model from service config and ensure it builds without errors.
      Service generatedServiceConfig = tool.getServiceConfig();
      Model model = Model.create(generatedServiceConfig);
      StandardSetup.registerStandardConfigAspects(model);
      StandardSetup.registerStandardProcessors(model);
      model.establishStage(Normalized.KEY);
      if (model.getDiagCollector().hasErrors()) {
        printDiags(model.getDiagCollector().getDiags(), false);
      } else {
        testOutput().println(
            "============== Successfully regenerated service config ==============");
      }
    } else {
      printDiags(tool.getDiags(), true);
    }
  }

  private void test(String baseName, @Nullable String extensionName, boolean outputWarnings)
      throws Exception {
    test(baseName, extensionName, outputWarnings, Lists.<String>newArrayList());
  }

  private void printDiags(List<Diag> diags, boolean printWarnings) {
    for (Diag diag : diags) {
      if (diag.getKind() == Kind.WARNING && !printWarnings) {
        continue;
      }
      testOutput().printf("%s\n", DiagUtils.getDiagToPrint(diag, true));
    }
  }

  /**
   * Return true if the test name is "suppress_warnings".
   */
  private boolean isSuppressWarningsTest(String baseName) {
    return baseName.equals("suppress_warnings");
  }

  private boolean isDnsNameTest(String baseName){
    return baseName.equals("library_abnormal_dns");
  }

  private static boolean isLibraryWithError(String baseName) {
    return baseName.equals("library_with_error");
  }
}
