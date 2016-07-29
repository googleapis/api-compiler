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

import static org.junit.Assert.assertEquals;

import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link ProtoFile}
 */
@RunWith(JUnit4.class)

public class ProtoFileTest {

  private List<String> protoLines;
  private ProtoFile proto;
  private TestConfig testApi;

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  protected String getTestFileName() {
    return "proto_file_test.proto";
  }

  @Before public void before() throws Exception {
    testApi = new TestConfig(TestDataLocator.create(getClass()), tempDir.getRoot().getPath(),
        ImmutableList.of(getTestFileName()));
    proto = testApi.createModel(ImmutableList.<String>of()).getFiles().get(0);

    // Buffer proto file content to list of strings.
    String content =  testApi.readTestData(getTestFileName());
    protoLines = Lists.newArrayList(content.split("\\n"));
  }

  @Test public void testGetLocation_service() {
    Interface service = getService();
    verifyLocation("service Service {", service.getLocation());
  }

  @Test public void testGetLocation_method() {
    Interface service = getService();
    Method method = service.getMethods().get(0);

    verifyLocation("rpc Method(OuterMessage) returns (OuterMessage.InnerMessage);",
        method.getLocation());
  }

  @Test public void testGetFullName_message() {
    MessageType outer = getOuterMessage();
    MessageType inner = getInnerMessage();

    assertEquals("test.ProtoFileTest.OuterMessage", outer.getFullName());
    assertEquals("test.ProtoFileTest.OuterMessage.InnerMessage", inner.getFullName());
  }

  @Test public void testGetLocation_message() {
    MessageType outer = getOuterMessage();
    MessageType inner = getInnerMessage();

    verifyLocation("message OuterMessage {", outer.getLocation());
    verifyLocation("message InnerMessage {", inner.getLocation());
  }

  @Test public void testGetLocation_field() {
    Field field1 = getOuterMessage().getFields().get(0);
    Field field3 = getInnerMessage().getFields().get(0);

    verifyLocation("optional string field1 = 1; // Field.", field1.getLocation());
    verifyLocation("optional string field3 = 3;", field3.getLocation());
  }

  @Test public void testGetLocation_enum() {
    EnumType outer = getOuterEnum();
    EnumType inner = getInnerEnum();

    verifyLocation("enum OuterEnum {", outer.getLocation());
    verifyLocation("enum InnerEnum {", inner.getLocation());
  }

  @Test public void testGetLocation_enumValue() {
    EnumValue outerValue = getOuterEnum().getValues().get(0);
    EnumValue innerValue = getInnerEnum().getValues().get(0);

    verifyLocation("VALUE1 = 1;", innerValue.getLocation());
    verifyLocation("VALUE2 = 2;", outerValue.getLocation());
  }

  @Test public void testGetDocumentation() {
    String blockDocument = getIdlDocumentation(getOuterMessage());
    String leadingDocument = getIdlDocumentation(getInnerMessage());
    String trailingDocument = getIdlDocumentation(getOuterMessage().getFields().get(0));
    String fileDocument = getIdlDocumentation(proto);

    assertEquals(" Outer Message.\n Contains inner Message. ", blockDocument);
    assertEquals(" Inner Message.\n", leadingDocument);
    assertEquals(" Field.\n", trailingDocument);
    assertEquals("", fileDocument);
  }

  private String getIdlDocumentation(ProtoElement element) {
    return element.getFile().getDocumentation(element);
  }

  // Verify given location of source proto file has the expected string.
  private void verifyLocation(String exp, Location location) {
    String[] locations = location.getDisplayString().split(":");
    int line = Integer.valueOf(locations[1]) - 1;
    int column = Integer.valueOf(locations[2]) - 1;
    String res = protoLines.get(line).substring(column);

    assertEquals(exp, res);
  }

  private Interface getService() {
    return proto.getInterfaces().get(0);
  }

  protected MessageType getOuterMessage() {
    return proto.getMessages().get(0);
  }

  protected MessageType getInnerMessage() {
    return getOuterMessage().getMessages().get(0);
  }

  private EnumType getOuterEnum() {
    return proto.getEnums().get(0);
  }

  private EnumType getInnerEnum() {
    return getOuterMessage().getEnums().get(0);
  }
}
