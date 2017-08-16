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
package com.google.api.tools.framework.importers.swagger.aspects.quota;

import com.google.api.MetricRule;
import com.google.api.tools.framework.importers.swagger.aspects.utils.NameConverter;
import com.google.api.tools.framework.importers.swagger.aspects.utils.VendorExtensionProtoConverter;
import com.google.api.tools.framework.model.DiagCollector;
import io.swagger.models.Operation;
import javax.annotation.Nullable;

/** Generator for {@link MetricRule}s for single API methods. */
public class MetricRuleGenerator {

  private static final String QUOTA_SWAGGER_EXTENSION = "x-google-quota";

  private final String namespacePrefix;
  private final DiagCollector diagCollector;

  public MetricRuleGenerator(String namespacePrefix, DiagCollector diagCollector) {
    this.diagCollector = diagCollector;
    this.namespacePrefix =
        (namespacePrefix.isEmpty() || namespacePrefix.endsWith("."))
            ? namespacePrefix
            : namespacePrefix + ".";
  }

  @Nullable
  public MetricRule createMetricRule(Operation operation) {
    VendorExtensionProtoConverter extensionConverter =
        new VendorExtensionProtoConverter(operation.getVendorExtensions(), diagCollector);
    if (extensionConverter.hasExtension(QUOTA_SWAGGER_EXTENSION)) {
      MetricRule metricRule =
          extensionConverter.convertExtensionToProto(
              MetricRule.getDefaultInstance(), QUOTA_SWAGGER_EXTENSION);
      return metricRule
          .toBuilder()
          .setSelector(
              namespacePrefix + NameConverter.operationIdToMethodName(operation.getOperationId()))
          .build();
    }
    return null;
  }
}
