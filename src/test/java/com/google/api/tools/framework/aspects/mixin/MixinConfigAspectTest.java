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

package com.google.api.tools.framework.aspects.mixin;

import com.google.api.tools.framework.aspects.ConfigAspectBaselineTestCase;
import com.google.api.tools.framework.aspects.documentation.DocumentationConfigAspect;
import com.google.api.tools.framework.aspects.http.HttpConfigAspect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link MixinConfigAspect}.
 */
@RunWith(JUnit4.class)

public class MixinConfigAspectTest extends ConfigAspectBaselineTestCase {

  public MixinConfigAspectTest() {
    super(MixinConfigAspect.class);
    addBaselineAspect(HttpConfigAspect.class);
    addBaselineAspect(DocumentationConfigAspect.class);
  }

  @Test public void mixin() throws Exception {
    test("mixin");
  }

  @Test public void errors_mixin() throws Exception {
    test("errors_mixin");
  }

  @Test public void dependency_order() throws Exception {
    test("dependency_order");
  }
}
