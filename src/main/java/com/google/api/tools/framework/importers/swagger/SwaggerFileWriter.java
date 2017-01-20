/*
 * Copyright (C) 2016 Google, Inc.
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
package com.google.api.tools.framework.importers.swagger;

import com.google.api.tools.framework.tools.FileWrapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.nio.charset.Charset;

/** File write utilities for Swagger to service conversion. */
public class SwaggerFileWriter {
  /** Saves the file contents on the disk and returns the saved file paths. */
  public static Map<String, FileWrapper> saveFilesOnDisk(List<FileWrapper> inputFiles) {

    ImmutableMap.Builder<String, FileWrapper> savedFiles = ImmutableMap.builder();
    File tempDir = Files.createTempDir();
    String tmpDirLocation = tempDir.getAbsolutePath();
    for (FileWrapper inputFile : inputFiles) {
      String filePath = inputFile.getFilename().replaceAll("[\\\\/:]", "_");

      Preconditions.checkState(
          !Strings.isNullOrEmpty(inputFile.getFileContents().toString()),
          "swagger spec file contents empty");
      Preconditions.checkState(
          !Strings.isNullOrEmpty(filePath), "swagger spec file path not provided");

      String filePathToSave =
          File.separator
              + tmpDirLocation
              + File.separator
              + "swagger_spec_files"
              + File.separator
              + filePath;
      FileWrapper fileToSave = FileWrapper.create(filePathToSave, inputFile.getFileContents());
      try {
        saveFileOnDisk(fileToSave);
        savedFiles.put(inputFile.getFilename(), fileToSave);
      } catch (IOException ex) {
        throw new IllegalStateException(
            String.format(
                "Unable to save the swagger spec contents on the disk at %s", filePathToSave),
            ex);
      }
    }
    return savedFiles.build();
  }

  /** Saves the individual file on disk with the fileContent. */
  private static void saveFileOnDisk(FileWrapper fileWrapper) throws IOException {
    File file = new File(fileWrapper.getFilename());
    Files.createParentDirs(file);
    Files.write(fileWrapper.getFileContents().toStringUtf8(), file, Charset.defaultCharset());
  }
}
