/*
 * Copyright 2017 Google Inc.
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

import com.google.api.Service;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import javax.annotation.Nullable;

/**
 * Binary google.api.Service proto reader.
 */
public class ProtoServiceReader {

  /**
   * Reads a configuration from a file containing a binary-encoded google.api.Service
   * proto message, reporting errors to the diag collector.
   * <p>Returns proto {@link ConfigSource} representing the config, or null if
   * errors were detected while unmarshaling the binary proto contents.
   */
  @Nullable public static ConfigSource readBinaryConfig(
      DiagCollector diag, String filename, ByteString fileContents) {
    try {
      return ConfigSource.newBuilder(Service.parser().parseFrom(fileContents)).build();
    } catch (InvalidProtocolBufferException e) {
      diag.addDiag(Diag.error(
          new SimpleLocation(filename),
          "Failed to parse google.api.Service proto message from the binary proto file."));
      return null;
    }
  }

  @Nullable public static ConfigSource readTextConfig(
      DiagCollector diag, String filename, ByteString fileContents) {
    try {
      Service.Builder builder = Service.newBuilder();
      TextFormat.getParser().merge(fileContents.toStringUtf8(), builder);
      return ConfigSource.newBuilder(builder.build()).build();
    } catch (final TextFormat.ParseException e) {
      diag.addDiag(Diag.error(
          new SimpleLocation(
              String.format("%s:%d:%d", filename, e.getLine(), e.getColumn()),
              filename),
          "Failed to parse google.api.Service proto message from the text proto file."));
      return null;
    }
  }
}
