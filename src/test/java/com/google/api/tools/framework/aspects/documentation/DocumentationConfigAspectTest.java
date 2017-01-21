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

package com.google.api.tools.framework.aspects.documentation;

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;
import com.google.api.tools.framework.aspects.http.HttpConfigAspect;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link DocumentationConfigAspect}.
 */
@RunWith(JUnit4.class)

public class DocumentationConfigAspectTest extends ConfigAspectBaselineTestCase {

  public DocumentationConfigAspectTest() {
    super(DocumentationConfigAspect.class);
    addBaselineAspect(HttpConfigAspect.class);
  }

  @Test public void docpresence() throws Exception {
    test("docpresence");
  }

  @Test
  public void doc_pages_nested() throws Exception {
    test("doc_pages_nested");
  }

  @Test public void doc_pages_name_conflict() throws Exception {
    test("doc_pages_name_conflict");
  }

  @Test public void deprecation_desc_proto() throws Exception {
    test("deprecation_desc_proto");
  }

  @Test public void deprecation_desc_yaml() throws Exception {
    test("deprecation_desc_yaml");
  }

  @Test public void deprecation_desc_mask() throws Exception {
    test("deprecation_desc_mask");
  }

  @Test public void deprecation_desc_ml() throws Exception {
    test("deprecation_desc_ml");
  }
}
