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

package com.google.api.tools.framework.tools;

import com.google.auto.value.AutoValue;
import com.google.common.io.Files;
import com.google.protobuf.ByteString;
import java.io.File;
import java.io.IOException;

/** Wrapper for a Config file (filename and contents) */
@AutoValue
public abstract class FileWrapper {

  /**
   * Creates a new {@link FileWrapper} from a given filename, either absolute or relative.
   *
   * @throws IOException if the file does not exist or cannot be read.
   */
  public static FileWrapper from(String fileName) throws IOException {
    return create(fileName, ByteString.copyFrom(Files.toByteArray(new File(fileName))));
  }

  public static FileWrapper create(String filename, ByteString fileContents) {
    return new AutoValue_FileWrapper(filename, fileContents);
  }
  
  public static FileWrapper create(String filename, String fileContentsUtf8) {
    return create(filename, ByteString.copyFromUtf8(fileContentsUtf8));
  }

  public abstract String getFilename();

  public abstract ByteString getFileContents();
}
