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

package com.google.api.tools.framework.tools.util;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility functions for writing various objects to an output file.
 */
public class OutputUtil {
  private static final Pattern SERVICE_BASE_NAME_PATTERN = Pattern.compile(
      "^(?<sandboxed>.+)\\.sandbox\\.googleapis\\.com$"
      + "|^(?<corp>.+)\\.corp\\.googleapis\\.com$"
      + "|^(?<regular>.+)\\.googleapis\\.com$");

  /**
   * Writes string content to a file.
   */
  public static void writeToFile(File outputFile, String content) throws IOException {
    Preconditions.checkNotNull(outputFile);
    Preconditions.checkNotNull(content);

    try (BufferedWriter outWriter = Files.newWriter(outputFile, StandardCharsets.UTF_8)) {
      outWriter.write(content);
    }
  }

  /**
   * Writes a proto to a file in text format.
   */
  public static void writeProtoTextToFile(File outputFile, Message proto)
      throws IOException {
    try (BufferedWriter outWriter = Files.newWriter(outputFile, StandardCharsets.UTF_8)) {
      TextFormat.print(proto, outWriter);
    }
  }

  /**
   * Writes a proto to a file in binary format.
   */
  public static void writeProtoBinaryToFile(File outputFile, Message proto)
      throws IOException {
    try (OutputStream prodOutputStream = new FileOutputStream(outputFile)) {
      proto.writeTo(prodOutputStream);
    }
  }
  
  /**
   * Returns the service base name as derived from the full service name, or null if derivation is
   * not successful.
   */
  public static String getServiceBaseName(String name) {
    Matcher matcher = SERVICE_BASE_NAME_PATTERN.matcher(name);
    if (!matcher.matches()) {
      int firstDotIndex = name.indexOf('.');
      if (firstDotIndex > 0){ 
        return name.substring(0, firstDotIndex);
      }
      return null;
    }
    name = matcher.group("sandboxed");
    if (name != null) {
      return name;
    }
    name = matcher.group("corp");
    if (name != null) {
      return name;
    }
    return matcher.group("regular");
  }
}
