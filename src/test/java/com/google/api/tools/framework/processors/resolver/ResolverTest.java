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

package com.google.api.tools.framework.processors.resolver;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.api.tools.framework.model.testing.StageValidator;
import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import java.io.IOException;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Resolver}.
 */
@RunWith(JUnit4.class)

public class ResolverTest {

  private static final String TEST_SOURCE =
      "syntax = \"proto2\"; "
      + "package test.pack;"
      + "message M {"
      + "  optional int32 i = 1;"
      + "  required N n = 2;"
      + "  optional E e = 3;"
      + "}"
      + "message N {"
      + "  optional string s = 1;"
      + "  message X {"
      + "    optional string f = 1;"
      + "  }"
      + "}"
      + "enum E {"
      + "  RED = 1;"
      + "  BLUE = 2;"
      + "}"
      + "service S {"
      + "  rpc Rpc(M) returns (N);"
      + "}"
      ;

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();
  private FileDescriptorSet descriptors;

  @Before public void before() throws IOException, Exception {
    TestDataLocator locator = TestDataLocator.create(getClass());
    locator.injectVirtualTestData("source.proto", TEST_SOURCE);
    descriptors =
        new TestConfig(locator, tempDir.getRoot().getPath(),
            ImmutableList.of("source.proto")).getDescriptor();
  }

  @Test public void resolvesOkFromProtoc() {
    Model testApi = Model.create(descriptors);
    testApi.registerProcessor(new Resolver());
    Assert.assertTrue(testApi.establishStage(Resolved.KEY));
    Assert.assertFalse(testApi.getDiagCollector().hasErrors());
    StageValidator.assertStages(ImmutableList.<Key<?>>of(Resolved.KEY), testApi);
  }

  @Test public void resolvesOkWithPartialNames() {
    // Modify the descriptor. Protoc generates full names, and
    // we want to check whether we can also deal with partial names.
    FileDescriptorSet.Builder builder = descriptors.toBuilder();
    builder.getFileBuilder(0)
        .getMessageTypeBuilder(0)
        .getFieldBuilder(1) // required N n
        .setTypeName("N");
    Model testApi = Model.create(builder.build());
    testApi.registerProcessor(new Resolver());
    Assert.assertTrue(testApi.establishStage(Resolved.KEY));
    Assert.assertFalse(testApi.getDiagCollector().hasErrors());
  }

  @Test public void resolvesWithErrors() {
    // Modify the descriptor injecting some errors.
    FileDescriptorSet.Builder builder = descriptors.toBuilder();
    builder.getFileBuilder(0)
        .getMessageTypeBuilder(0)
        .getFieldBuilder(1) // required N n
        .setTypeName("undef_N");
    builder.getFileBuilder(0)
        .getMessageTypeBuilder(0)
        .getFieldBuilder(2) // optional E e
        .setTypeName("undef_E");
    Model testApi = Model.create(builder.build());
    testApi.registerProcessor(new Resolver());
    Assert.assertFalse(testApi.establishStage(Resolved.KEY));
    Assert.assertEquals(2, testApi.getDiagCollector().getErrorCount());
    assertThat(testApi.getDiagCollector().getDiags().get(0).toString()).contains("undef_N");
    assertThat(testApi.getDiagCollector().getDiags().get(1).toString()).contains("undef_E");
  }
}
