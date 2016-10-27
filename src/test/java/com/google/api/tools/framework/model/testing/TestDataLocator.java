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

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Provides a locator for test data. Implementations may use different strategies, for example,
 * searching for a resource in the class path, or fetching the data from the file system.
 */
public abstract class TestDataLocator {

  public static TestDataLocator create(Class<?> classContext) {
    return new ClassPathTestDataLocator(classContext);
  }

  private final List<Class<?>> classContexts = new ArrayList<>();
  private final List<String> testDataDirs = new ArrayList<>();
  private final Map<String, String> injectedTestData = new HashMap<>();

  /**
   * Creates a new test data locator which locates test data relative to the given class.
   */
  protected TestDataLocator(Class<?> classContext) {
    classContexts.add(classContext);
    testDataDirs.add("testdata");
  }

  /**
   * Adds a new test data source. Test data will be first searched in this new source and
   * then in the currently configured one.
   */
  public void addTestDataSource(Class<?> classContext, String testDataDir) {
    classContexts.add(classContext);
    testDataDirs.add(testDataDir);
  }

  /**
   * Injects virtual test data which can be retrieved as it was stored as a file. This can
   * be used to generate test data content on-the-fly.
   */
  public void injectVirtualTestData(String name, String content) {
    injectedTestData.put(name, content);
  }

  /**
   * Injects virtual test data based on a list of files found at the given root. The simple
   * name of a file is used in the virtual test data mapping.
   */
  public void injectVirtualTestData(Path sourceDir, Iterable<Path> dataFiles) {
    for (Path file : dataFiles) {
      try {
        injectVirtualTestData(file.getFileName().toString(),
            new String(Files.readAllBytes(sourceDir.resolve(file)), StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Injects virtual test data based on a list of files found at the given root. The simple
   * name of a file is used in the virtual test data mapping.
   */
  public void injectVirtualTestData(Path sourceDir, String... dataFiles) {
    injectVirtualTestData(sourceDir,
        FluentIterable.from(ImmutableList.copyOf(dataFiles)).transform(
            new Function<String, Path>() {
              @Override
              public Path apply(String input) {
                return Paths.get(input);
              }
            }));
  }

  /**
   * Returns a URL representing the test data, or null if it can't be found.
   *
   * <p>The value of the URL should be treated as opaque, and only used for subsequent
   * calls to {@link #readTestData(URL)}.
   *
   * <p>The passed name can be relative or absolute (starting with {@code /}. If its absolute,
   * it is directly used. If it is relative, this method first searches relative to the package path
   * of the class context, then tries the package path with appended test data directory,
   * and finally tries the name directly.
   */
  @Nullable
  public URL findTestData(String name) {
    // Check for injected test data.
    if (injectedTestData.containsKey(name)) {
      try {
        return new URL("file", "injected", name);
      } catch (MalformedURLException e) {
        throw new RuntimeException(e);
      }
    }

    // Search for test data.
    List<String> candidates = new ArrayList<>();
    if (Paths.get(name).isAbsolute()) {
      candidates.add(name.substring(1));
    } else {
      for (int i = classContexts.size() - 1; i >= 0; i--) {
        Class<?> classContext = classContexts.get(i);
        String testDataDir = testDataDirs.get(i);
        String relativeToThis = classContext.getPackage().getName().replace(".", "/");
        candidates.add(relativeToThis + "/" + name);
        candidates.add(relativeToThis + "/" + testDataDir + "/" + name);
        candidates.add(name);
      }
    }
    for (String path : candidates) {
      URL url = resolveTestData(path);
      if (url != null) {
        return url;
      }
    }
    return null;
  }

  /**
   * Returns test data based on a URL as returned by {@link #findTestData(String)}.
   */
  public String readTestData(URL url) {
    if ("injected".equals(url.getHost())) {
      return Preconditions.checkNotNull(injectedTestData.get(url.getFile()));
    }
    return fetchTestData(url);
  }

  /**
   * Makes the test data available as a temporary file in the file system and returns a path to it.
   */
  public Path getTestDataAsFile(String filePath) throws IOException {
    URL data = findTestData(filePath);
    if (data == null) {
      throw new IllegalArgumentException("Cannot locate test data: " + filePath);
    }
    int lastSlashIndex = filePath.lastIndexOf(File.separator);
    String fileName = lastSlashIndex == -1 ? filePath : filePath.substring(lastSlashIndex + 1);
    Path tempFile = Files.createTempFile(fileName, fileName);
    Files.write(tempFile, readTestData(data).getBytes());
    return tempFile;
  }

  /**
   * Resolves a test data name relative to the root of all test data, returning a URL to
   * represent it.
   */
  @Nullable
  protected abstract URL resolveTestData(String name);

  /**
   * Fetches test data based on a URL as returned by {@link #resolveTestData(String)}.
   */
  protected abstract String fetchTestData(URL url);
}
