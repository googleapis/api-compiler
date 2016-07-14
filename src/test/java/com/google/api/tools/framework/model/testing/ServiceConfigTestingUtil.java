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

package com.google.api.tools.framework.model.testing;

import com.google.api.DocumentationRule;
import com.google.api.Service;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;

import java.util.List;

/**
 * Class containing utility methods to make baseline testing of service config simple.
 */
public class ServiceConfigTestingUtil {

  /** Package names of known system core types. */
  private static final FluentIterable<String> SYSTEM_TYPE_PACKAGE_NAMES =
      FluentIterable.from(Lists.newArrayList("google.protobuf.", "google.protobuf"));

  public static Service.Builder clearIrrelevantData(Service.Builder builder) {
    clearSystemDataDocumentation(builder);
    clearEmptyFields(builder);
    return builder;
  }

  public static Service.Builder clearSystemDataDocumentation(Service.Builder builder) {
    List<DocumentationRule> documentationRules =
        Lists.newArrayList(builder.getDocumentationBuilder().getRulesList());
    builder.getDocumentationBuilder().clearRules();
    for (DocumentationRule docRule : documentationRules) {
      final String selector = docRule.getSelector();
      if (!SYSTEM_TYPE_PACKAGE_NAMES.anyMatch(
          new Predicate<String>() {
            @Override
            public boolean apply(String systemTypePackageName) {
              if (systemTypePackageName.endsWith(".")) {
                return selector.startsWith(systemTypePackageName);
              } else {
                return selector.equals(systemTypePackageName);
              }
            }
          })) {
        builder.getDocumentationBuilder().addRules(docRule);
      }
    }
    return builder;
  }

  private static void clearIfEmptyMessage(Service.Builder builder, FieldDescriptor field) {
    if (!field.isRepeated() && field.getType() == FieldDescriptor.Type.MESSAGE
        && ((Message) builder.getField(field)).getAllFields().size() == 0) {
      builder.clearField(field);
    }
  }

  private static void clearEmptyFields(Service.Builder builder) {
    for (FieldDescriptor field : Service.getDescriptor().getFields()) {
      clearIfEmptyMessage(builder, field);
    }
  }
}

