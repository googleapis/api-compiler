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

package com.google.api.tools.framework.importers.swagger.aspects.type;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import javax.annotation.Nullable;

/** Type information to represent a schema object or a type of a field. */
@AutoValue
public abstract class TypeInfo {
  @Nullable
  public abstract String typeUrl();

  // Kind of the type.
  public abstract Kind kind();

  // Cardinality of the type.
  public abstract Cardinality cardinality();

  // If this type has fields (properties), its fields.
  @Nullable
  public abstract ImmutableList<Field> fields();

  // If this type has enum values, its values.
  @Nullable
  public abstract ImmutableList<String> enumValues();

  // Does represent Map Entry.
  public abstract Boolean isMapEntry();

  // Return TypeInfo with given cardinality
  TypeInfo withCardinality(Cardinality cardinality) {
    return create(typeUrl(), kind(), cardinality, fields(), enumValues(), isMapEntry());
  }

  // Return TypeInfo with given typeUrl
  TypeInfo withTypeUrl(String typeUrl) {
    return create(typeUrl, kind(), cardinality(), fields(), enumValues(), isMapEntry());
  }

  // Return TypeInfo with given kind
  TypeInfo withKind(Kind kind) {
    return create(typeUrl(), kind, cardinality(), fields(), enumValues(), isMapEntry());
  }

  // Return TypeInfo with given fields
  TypeInfo withFields(ImmutableList<Field> fields) {
    return create(typeUrl(), kind(), cardinality(), fields, enumValues(), isMapEntry());
  }

  // Return TypeInfo with given fields
  TypeInfo withEnums(ImmutableList<String> enumValues) {
    return create(typeUrl(), kind(), cardinality(), fields(), enumValues, isMapEntry());
  }

  // Return TypeInfo with given isMapEntry
  TypeInfo withIsMapEntry(Boolean isMapEntry) {
    return create(typeUrl(), kind(), cardinality(), fields(), enumValues(), isMapEntry);
  }

  // Creates a TypeInfo instance
  static TypeInfo create(String typeUrl, Kind kind, Cardinality cardinality) {
    return create(typeUrl, kind, cardinality, null, null, false);
  }

  // Creates a TypeInfo instance
  static TypeInfo create(
      String typeUrl,
      Kind kind,
      Cardinality cardinality,
      ImmutableList<Field> fields,
      ImmutableList<String> enumValues,
      Boolean isMapEntry) {
    return new AutoValue_TypeInfo(typeUrl, kind, cardinality, fields, enumValues, isMapEntry);
  }
}
