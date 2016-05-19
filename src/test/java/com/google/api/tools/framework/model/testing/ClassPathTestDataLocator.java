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

package com.google.api.tools.framework.model.testing;

import com.google.common.io.Resources;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

/**
 * {@link TestDataLocator} which fetches test data as a resource from the class path.
 */
public class ClassPathTestDataLocator extends TestDataLocator {

  private final ClassLoader classLoader;

  /**
   * Creates a new test data locator which locates test data relative to the given class.
   */
  public ClassPathTestDataLocator(Class<?> classContext) {
     super(classContext);
     this.classLoader = classContext.getClassLoader();
  }

  @Override
  @Nullable
  public URL resolveTestData(String name) {
    return classLoader.getResource(name);
  }

  @Override
  public String fetchTestData(URL url) {
    try {
      return Resources.toString(url, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalArgumentException(String.format("Cannot read resource '%s': %s",
          url, e.getMessage()));
    }
  }
}
