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

package com.google.api.tools.framework.importers.swagger;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;

class WellKnownTypeUtils {

  static final String TYPE_SERVICE_BASE_URL = "type.googleapis.com/";
  private static final String INTEGER_TYPE_URL =
      TYPE_SERVICE_BASE_URL + "google.protobuf.Int32Value";
  private static final String NUMBER_TYPE_URL =
      TYPE_SERVICE_BASE_URL + "google.protobuf.DoubleValue";
  private static final String BOOLEAN_TYPE_URL =
      TYPE_SERVICE_BASE_URL + "google.protobuf.BoolValue";
  private static final String STRING_TYPE_URL =
      TYPE_SERVICE_BASE_URL + "google.protobuf.StringValue";
  private static final String LIST_VALUE_TYPE_URL =
      TYPE_SERVICE_BASE_URL + "google.protobuf.ListValue";
  private static final String VALUE_TYPE_URL = TYPE_SERVICE_BASE_URL + "google.protobuf.Value";
  private static final String STRUCT_TYPE_URL = TYPE_SERVICE_BASE_URL + "google.protobuf.Struct";
  private static final String EMPTY_TYPE_URL = TYPE_SERVICE_BASE_URL + "google.protobuf.Empty";

  // TODO (guptasu): We do not have the format information. Are the default typeUrl based on
  // types correct ?
  private static final ImmutableMap<String, String> wellKnownTypesToTypeUrl = ImmutableMap
      .<String, String>builder()
      .put("string", STRING_TYPE_URL)
      .put("integer", INTEGER_TYPE_URL)
      .put("boolean", BOOLEAN_TYPE_URL)
      .put("number", NUMBER_TYPE_URL)
      .put("list", LIST_VALUE_TYPE_URL)
      .put("value", VALUE_TYPE_URL)
      .put("struct", STRUCT_TYPE_URL)
      .put("empty", EMPTY_TYPE_URL)
      .build();

  private static final ImmutableSet<String> primitiveTypes = ImmutableSet
      .<String>builder()
      .add("string")
      .add("integer")
      .add("boolean")
      .add("number")
      .add()
      .build();

  /**
   * @return the typeUrl corresponding to the type.
   * Example: for string, the typeUrl is type.googleapis.com/google.protobuf.StringValue
   */
  static TypeInfo getTypeInfo(String type) {
    Preconditions.checkState(wellKnownTypesToTypeUrl.containsKey(type),
        type + " invalid primitive " + "type");
    return TypeInfo.create(
        wellKnownTypesToTypeUrl.get(type), Kind.TYPE_MESSAGE, Cardinality.CARDINALITY_OPTIONAL);
  }

  /**
   * @return true if json type is a primitive type like boolean, number, integer or number.
   */
  static boolean isPrimitiveType(String type) {
    return primitiveTypes.contains(type);
  }

  /**
   * @return a Kind corresponding to type and format.
   */
  static Kind getKind(String type, String format) {
    Preconditions.checkState(primitiveTypes.contains(type),
        type + " invalid primitive " + "type");
    switch (type) {
    case "number":
      return WellKnownTypeUtils.getNumberKind(format);
    case "integer":
      return WellKnownTypeUtils.getIntegerKind(format);
    case "boolean":
      return Kind.TYPE_BOOL;
    case "string":
      return WellKnownTypeUtils.getStringKind(format);
    default:
      return Kind.UNRECOGNIZED;
    }
  }

  /**
   * @return a Kind corresponding to the JSON number type and the given format.
   * TODO (guptasu): For all below types, ensure defaults are right.
   * TODO (guptasu): For all below types, add errors for unknown types.
   */
  private static Kind getNumberKind(String format) {
    if (Strings.isNullOrEmpty(format)) {
      return Kind.TYPE_DOUBLE;
    }
    switch (format) {
      case "float":
        return Kind.TYPE_FLOAT;
      case "double":
        return Kind.TYPE_DOUBLE;
      default:
        return Kind.TYPE_DOUBLE;
    }
  }

  /**
   * @return a Kind corresponding to the JSON integer type and the given format.
   */
  private static Kind getIntegerKind(String format) {
    if (Strings.isNullOrEmpty(format)) {
      return Kind.TYPE_INT32;
    }
    switch (format) {
      case "int32":
        return Kind.TYPE_INT32;
      case "uint32":
        return Kind.TYPE_UINT32;
      case "int64":
        return Kind.TYPE_INT64;
      default:
        return Kind.TYPE_INT32;
    }
  }

  /**
   * @return a Kind corresponding to the JSON string type and the given format.
   */
  private static Kind getStringKind(String format) {
    if (Strings.isNullOrEmpty(format)) {
      return Kind.TYPE_STRING;
    }
    switch (format) {
      case "int64":
        return Kind.TYPE_INT64;
      case "uint64":
        return Kind.TYPE_UINT64;
      case "byte":
        return Kind.TYPE_BYTES;
      default:
        return Kind.TYPE_STRING;
    }
  }
}
