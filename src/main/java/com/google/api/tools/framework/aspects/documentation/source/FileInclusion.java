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

package com.google.api.tools.framework.aspects.documentation.source;

import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;

/**
 * Represents Docgen file inclusion instruction.
 */
public class FileInclusion extends ContentElement {
  private static final String SUPPORTED_FILE_EXTENSION = "md";

  private final String relativeFilePath;
  private final String resolvedFilePath;
  private final int sectionLevel;
  private final String content;

  public FileInclusion(String docPath, String relativeFilePath, int sectionLevel, int startIndex,
      int endIndex, DiagCollector diagCollector, Location sourceLocation) {
    super(startIndex, endIndex, diagCollector, sourceLocation);
    this.relativeFilePath = relativeFilePath;
    this.sectionLevel = sectionLevel;
    if (!Files.getFileExtension(relativeFilePath).equals(SUPPORTED_FILE_EXTENSION)) {
      error("Not supported file extension: '%s'.", relativeFilePath);
      this.resolvedFilePath = null;
      this.content = null;
    } else {
      this.resolvedFilePath = resolveFilePath(docPath, relativeFilePath);
      if (resolvedFilePath == null) {
        error("File not found, relative path '%s', inside root '%s'.", relativeFilePath, docPath);
        this.content = null;
      } else {
        this.content = readFileContent();
      }
    }
  }

  /**
   * Returns the included file path relative to the root of data files.
   */
  public String getRelativeFilePath() {
    return relativeFilePath;
  }

  /**
   * Returns the included file name.
   */
  public String getFileName() {
    int index = relativeFilePath.lastIndexOf("/");
    return index < 0 ? relativeFilePath
        : relativeFilePath.substring(index + 1, relativeFilePath.length());
  }

  /**
   * Returns the content of included file if file path is valid.
   * Otherwise, returns null.
   */
  @Override
  @Nullable
  public String getContent() {
    return content;
  }

  /**
   * Returns the section level the file should be included.
   */
  public int getSectionLevel() {
    return sectionLevel;
  }

  private String readFileContent() {
    try {
      return Files.asCharSource(new File(resolvedFilePath), StandardCharsets.UTF_8).read();
    } catch (IOException e) {
      error("Failed to read file: '%s'.", resolvedFilePath);
      return null;
    }
  }

  private static String resolveFilePath(String docPath, String relativeFilePath) {
    for (String base : Splitter.on(File.pathSeparator).split(docPath)) {
      File file = new File(base, relativeFilePath);
      if (file.canRead()) {
        return file.getPath();
      }
    }
    return null;
  }
}
