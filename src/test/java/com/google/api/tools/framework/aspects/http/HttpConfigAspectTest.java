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

package com.google.api.tools.framework.aspects.http;

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link HttpConfigAspectTest}.
 */
@RunWith(JUnit4.class)

public class HttpConfigAspectTest extends ConfigAspectBaselineTestCase {
  public HttpConfigAspectTest() {
    super(HttpConfigAspect.class);
  }

  @Test public void http() throws Exception {
    test("http");
  }

  @Test public void recursive_bindings() throws Exception {
    test("recursive_bindings");
  }

  @Test public void recursive_selector() throws Exception {
    test("recursive_selector");
  }

  @Test public void param_reserved_keyword() throws Exception {
    test("param_reserved_keyword");
  }

  @Test public void protobuf_types_in_response_body() throws Exception {
    test("protobuf_types_in_response_body");
  }

  @Test public void same_name_collection_and_method() throws Exception {
    test("same_name_collection_and_method");
  }

  @Test public void custom_post_invalid_body() throws Exception {
    test("custom_post_invalid_body");
  }

  @Test public void bad_field_binding() throws Exception {
    test("bad_field_binding");
  }

  @Test public void body_non_top_level() throws Exception {
    test("body_non_top_level");
  }

}
