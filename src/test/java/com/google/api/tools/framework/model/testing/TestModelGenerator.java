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
package com.google.api.tools.framework.model.testing;

import com.google.api.tools.framework.model.Model;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import org.junit.rules.TemporaryFolder;

/** Utility for building a {@link Model} from a set of proto and yaml files */
public class TestModelGenerator {

  private final TestDataLocator testDataLocator;
  private final TemporaryFolder tempDir;

  public TestModelGenerator(TestDataLocator testDataLocator, TemporaryFolder tempDir) {
    this.testDataLocator = testDataLocator;
    this.tempDir = tempDir;
  }

  public ModelTestInfo buildModel(String... basenames) throws Exception {
    return buildModel(Arrays.asList(basenames));
  }

  public ModelTestInfo buildModel(Iterable<String> basenames) throws Exception {
    List<String> yamlFiles = getFilesWithSuffix(basenames, ".yaml");
    List<String> protoFiles = getFilesWithSuffix(basenames, ".proto");
    // Some tests might have proto files without .proto extensions. This
    // is to bypass certain presubmit check. Therefore, for such cases protoFiles
    // is defaulted to all files without any extensions.
    if (protoFiles.isEmpty()) {
      protoFiles = getFilesWithSuffix(basenames, "");
    }
    if (protoFiles.isEmpty()) {
      throw new IllegalArgumentException("No proto files found");
    }
    TestConfig testConfig = createTestConfig(tempDir.getRoot().getPath(), protoFiles);
    return ModelTestInfo.create(testConfig.createModel(yamlFiles), testConfig);
  }

  /**
   * Creates the Model object based on the input proto files.
   */
  protected TestConfig createTestConfig(String tempDir, List<String> protoFiles) {
    return new TestConfig(testDataLocator, tempDir, protoFiles);
  }

  protected TestDataLocator getTestDataLocator() {
    return testDataLocator;
  }

  private List<String> getFilesWithSuffix(Iterable<String> baseFileNames, String suffix) {
    List<String> files = Lists.newArrayList();
    for (String baseName : baseFileNames) {
      String name = baseName + suffix;
      URL url = testDataLocator.findTestData(name);
      if (url != null) {
        files.add(name);
      }
    }
    return files;
  }

  /**
   * Model generation info. Ideally we should kill this, but first need to stop people from using
   * {@link TestConfig} directly
   */
  @AutoValue
  public abstract static class ModelTestInfo {
    public static ModelTestInfo create(Model model, TestConfig testConfig) {
      return new AutoValue_TestModelGenerator_ModelTestInfo(model, testConfig);
    }

    public abstract Model getModel();

    public abstract TestConfig getTestConfig();
  }
}
