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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link ProtoFile} with no package.
 */
@RunWith(JUnit4.class)

public class ProtoFileNoPackageTest extends ProtoFileTest {

  @Override protected String getTestFileName() {
    return "proto_file_no_package_test.proto_bad";
  }

  @Override @Test public void testGetFullName_message() {
    MessageType outer = getOuterMessage();
    MessageType inner = getInnerMessage();

    assertEquals("OuterMessage", outer.getFullName());
    assertEquals("OuterMessage.InnerMessage", inner.getFullName());
  }
}
