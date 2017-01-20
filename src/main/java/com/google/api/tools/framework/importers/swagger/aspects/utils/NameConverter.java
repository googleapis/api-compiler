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

package com.google.api.tools.framework.importers.swagger.aspects.utils;

import com.google.common.base.CaseFormat;

/**
 * Helper class to provide appropriate names when representing elements from swagger into proto.
 */
public class NameConverter {
  public  static String operationIdToMethodName(String operationId) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, sanitizeStringValue(operationId));
  }

  public  static String operationIdToRequestMessageName(String operationId) {
    return operationIdToMethodName(operationId) + "Request";
  }

  public  static String operationIdToResponseMessageName(String operationId) {
    return operationIdToMethodName(operationId) + "Response";
  }

  public static String getFieldName(String jsonName) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sanitizeStringValue(jsonName));
  }

  public  static String propertyNameToMessageName(String propertyName) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, sanitizeStringValue(propertyName))
        + "Type";
  }

  public static String schemaNameToMessageName(String schemaName) {
    return CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, sanitizeStringValue(schemaName));
  }

  private static String sanitizeStringValue(String operationId) {
    return operationId.replaceAll("[^a-zA-Z0-9_]", "");
  }
}
