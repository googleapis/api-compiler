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

package com.google.api.tools.framework.yaml;

import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.testing.BaselineTestCase;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.api.tools.framework.model.testing.TextFormatForTest;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link YamlReader}
 */
@RunWith(JUnit4.class)

public class YamlReaderTest extends BaselineTestCase {

  private static final Map<String, Message> supportedConfigTypes =
      ImmutableMap.<String, Message>builder()
        .putAll(YamlReader.SUPPORTED_CONFIG_TYPES)
        .put(TestConfig.getDescriptor().getFullName(), TestConfig.getDefaultInstance())
        .build();

  private TestDataLocator testDataLocator = TestDataLocator.create(getClass());

  @Test public void normal() {
    testFromInputFile("normal.yaml");
  }

  @Test public void repeatedFields() {
    testFromInputFile("repeatedFields.yaml");
  }

  @Test public void failsForMergingCollections() {
    testFromInputFile("failsForMergingCollections.yaml");
  }

  @Test public void failsForOverridingFields() {
    testFromInputFile("failsForOverridingFields.yaml");
  }

  @Test public void failsForDuplicateCollections(){
    testFromInputFile("failsForDuplicateCollections.yaml");
  }

  @Test public void missingTypeConfig() {
    // Test for missing the root config for "type".
    String content = createContent(
        "name: blob.googleapis.com",
        "apis:\n",
        "- name: protiary.test.Storage\n");
    testFromInputString("missingTypeConfig", content);
  }

  @Test public void unknownTypeConfig() {
    // Test for config specified for "type" is unknown.
    String content = createContent(
        "type: google.api.SomethigElse",
        "name: blob.googleapis.com",
        "apis:",
        "- name: protiary.test.Storage");
    testFromInputString("unknownTypeConfig", content);
  }

  @Test public void unknownField() {
    // Test for config key specified is unknown to field of proto message type
    String content = createContent(
        "type: google.api.Service",
        "config_version: 1",
        "name: blob.googleapis.com",
        "dummy:",
        "- name: protiary.test.Storage");
    testFromInputString("unknownField", content);
  }

  @Test public void nonMapNodeForMessageType() {
    // Test for non Map node specified for proto message type.
    String content = createContent(
        "type: google.api.Service",
        "config_version: 1",
        "name: blob.googleapis.com",
        "apis:",
        "- protiary.test.Storage");
    testFromInputString("nonMapNodeForMessageType", content);
  }

  @Test public void mismatchedTypeValue() {
    String content = createContent(
        "type: google.api.Service",
        "config_version: stringInsteadOfInt"
        );
    testFromInputString("mismatchedTypeValue", content);
  }

  @Test public void testWrapperType() {
    String content = createContent(
        "type: google.api.Service",
        "config_version: 3");
    testFromInputString("testWrapperType", content);
  }

  private static String createContent(String... lines) {
    StringBuilder builder = new StringBuilder();
    for (String line : lines) {
      builder.append(line).append("\n");
    }
    return builder.toString();
  }

  private ConfigSource testFromInputString(String inputName, String content) {
    SimpleDiagCollector diag = new SimpleDiagCollector();
    ConfigSource config = YamlReader.readConfig(diag, inputName, content, supportedConfigTypes);
    if (config == null) {
      testOutput().println("errors!!");
      testOutput().println(diag);
    } else {
      testOutput().println(TextFormatForTest.INSTANCE.printToString(config.getConfig()));
    }
    return config;
  }

  private void testFromInputFile(String fileName) {
    testFromInputString(
        fileName, testDataLocator.readTestData(testDataLocator.findTestData(fileName)));
  }
}
