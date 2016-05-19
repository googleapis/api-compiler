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

package com.google.api.tools.framework.aspects.authentication.model;

import com.google.api.AuthenticationRule;
import com.google.api.tools.framework.model.Method;
import com.google.inject.Key;

/**
 * Represents the authentication configuration for a method.
 */
public class AuthAttribute {

  /**
   * A key to access authentication binding attributes.
   */
  public static final Key<AuthAttribute> KEY = Key.get(AuthAttribute.class);
  private final AuthenticationRule rule;

  public AuthAttribute(AuthenticationRule rule) {
    this.rule = rule;
  }

  /**
   * Gets the underlying AuthenticationRule.
   */
  public AuthenticationRule getAuthenticationRule() {
    return rule;
  }

  /**
   * Builds AuthBinding, mapping validated configuration settings into the HttpRequestChecker
   * configuration proto.
   */
  public static class Builder {
    private AuthenticationRule rule;
    private Method method;

    /**
     * Sets the underlying AuthenticationRule.
     */
    public Builder setRule(AuthenticationRule rule) {
      this.rule = rule;
      return this;
    }

    /**
     * Sets the method this applies to.
     */
    public Builder setMethod(Method method) {
      this.method = method;
      return this;
    }

    public AuthAttribute build() {
      return new AuthAttribute(rule);
    }
  }
}
