/*
 * Copyright (C) 2017 Google, Inc.
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

package com.google.api.tools.framework.util.buildervisitor;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;

/** Encapsulate information about a field to be added to a message type. */
@AutoValue
public abstract class FieldLocation {
  public static FieldLocation create(
      FieldDescriptorProto.Builder fieldDescriptor, SourceCodeInfo.Location location) {
    return new AutoValue_FieldLocation(fieldDescriptor, location);
  }

  public abstract FieldDescriptorProto.Builder fieldDescriptor();

  public abstract SourceCodeInfo.Location location();

  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("field", String.format("{%s}", condenseString(fieldDescriptor())))
        .add("location", String.format("{%s}", condenseString(location())))
        .toString();
  }

  private static String condenseString(Object input) {
    return input.toString().replace("\n", "; ").trim();
  }
}
