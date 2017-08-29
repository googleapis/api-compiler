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

package com.google.api.tools.framework.aspects.superquota.model;

import com.google.api.MetricRule;
import com.google.inject.Key;

/**
 * Attribute attached by this aspect to methods, representing the MetricRule.
 */
public class SuperQuotaAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<SuperQuotaAttribute> KEY = Key.get(SuperQuotaAttribute.class);

  private final MetricRule rule;

  public SuperQuotaAttribute(MetricRule rule) {
    this.rule = rule;
  }

  /**
   * Gets the underlying MetricRule.
   */
  public MetricRule getRule() {
    return this.rule;
  }
}
