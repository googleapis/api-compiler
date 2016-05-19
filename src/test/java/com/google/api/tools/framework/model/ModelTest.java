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

package com.google.api.tools.framework.model;

import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.common.collect.ImmutableList;
import com.google.inject.Key;
import com.google.inject.name.Names;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Api}.
 */
@RunWith(JUnit4.class)

public class ModelTest {

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

  private Model testModel;

  @Before public void before() throws Exception {
    TestDataLocator locator = TestDataLocator.create(getClass());
    locator.injectVirtualTestData("source.proto", TEST_SOURCE);
    testModel = new TestConfig(locator, tempDir.getRoot().getPath(),
        ImmutableList.of("source.proto")).createModel(ImmutableList.<String>of());
  }

  @Test public void smokeTestApiConstruction() {
    Assert.assertEquals(1,  testModel.getFiles().size());
    ProtoFile file = testModel.getFiles().get(0);
    Assert.assertEquals(2, file.getMessages().size());
    Assert.assertEquals(1, file.getEnums().size());
    Assert.assertEquals(3, file.getMessages().get(0).getFields().size());
    Assert.assertEquals(1, file.getMessages().get(1).getMessages().size());
    Assert.assertEquals(1, file.getInterfaces().size());
  }

  @Test public void testStageProcessing() {
    final StringBuffer log = new StringBuffer();
    final Key<Boolean> stage1 = Key.get(Boolean.class, Names.named("stage1"));
    final Key<Boolean> stage2 = Key.get(Boolean.class, Names.named("stage2"));
    testModel.registerProcessor(new Processor() {
      @Override public ImmutableList<Key<?>> requires() {
        return ImmutableList.of();
      }
      @Override public Key<?> establishes() {
        return stage1;
      }
      @Override public boolean run(Model model) {
        log.append("stage1");
        testModel.putAttribute(stage1, true);
        return true;
      }
    });

    testModel.registerProcessor(new Processor() {
      @Override public ImmutableList<Key<?>> requires() {
        return ImmutableList.<Key<?>>of(stage1);
      }
      @Override public Key<?> establishes() {
        return stage2;
      }
      @Override public boolean run(Model model) {
        log.append("stage2");
        testModel.putAttribute(stage2, true);
        return true;
      }
    });

    testModel.establishStage(stage2);
    Assert.assertEquals("stage1stage2", log.toString());
    testModel.establishStage(stage1);
    Assert.assertEquals("stage1stage2", log.toString());
    testModel.establishStage(stage2);
    Assert.assertEquals("stage1stage2", log.toString());
  }

  @Test public void testPrivateServiceNames() {
    Assert.assertFalse(Model.isPrivateService("foobar.googleapis.com"));
    Assert.assertTrue(Model.isPrivateService("foobar-pa.googleapis.com"));
    Assert.assertTrue(Model.isPrivateService("foobar.corp.googleapis.com"));
    Assert.assertTrue(Model.isPrivateService("test-foobar.sandbox.googleapis.com"));
  }
}
