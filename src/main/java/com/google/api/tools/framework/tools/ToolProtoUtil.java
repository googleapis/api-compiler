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

import com.google.api.AnnotationsProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.ExtensionRegistry;
import java.io.FileInputStream;
import java.io.IOException;

/** Proto utilities for tools. */
public class ToolProtoUtil {
  /**
   * Get the standard extension registry to use for processing service config. By default, registers
   * extensions from {@code google/api/annotations.proto} (and related proto files).
   */
  public static ExtensionRegistry getStandardPlatformExtensions() {
    ExtensionRegistry registry = ExtensionRegistry.newInstance();
    AnnotationsProto.registerAllExtensions(registry);

    return registry;
  }

  public static FileDescriptorSet openDescriptorSet(String fileName) {
    return openDescriptorSet(fileName, getStandardPlatformExtensions());
  }

  public static FileDescriptorSet openDescriptorSet(String fileName, ExtensionRegistry registry) {
    try {
      return FileDescriptorSet.parseFrom(new FileInputStream(fileName), registry);
    } catch (IOException e) {
      throw new RuntimeException(String.format("Cannot open+parse input file '%s'", fileName), e);
    }
  }
}
