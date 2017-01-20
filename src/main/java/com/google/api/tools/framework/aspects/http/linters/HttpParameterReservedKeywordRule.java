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

package com.google.api.tools.framework.aspects.http.linters;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.SystemParameter;
import com.google.api.tools.framework.model.FieldSelector;
import com.google.api.tools.framework.model.Method;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * Style rule to verify if the names of query parameters, of a http request, is not a reserved
 * keyword.
 */
public class HttpParameterReservedKeywordRule extends LintRule<Method> {

  public HttpParameterReservedKeywordRule(ConfigAspectBase aspect) {
    super(aspect, "param-reserved-keyword", Method.class);
  }

  @Override
  public void run(Method method) {
    if (!method.hasAttribute(HttpAttribute.KEY)) {
      return;
    }
    Set<String> visitedFieldNames = Sets.newHashSet();
    for (HttpAttribute httpAttribute : method.getAttribute(HttpAttribute.KEY).getAllBindings()) {
      for (FieldSelector fieldSelector : httpAttribute.getParamSelectors()) {
        String restParameterName = fieldSelector.getLastField().getJsonName();
        if (!visitedFieldNames.contains(restParameterName)) {
          visitedFieldNames.add(restParameterName);
          if (SystemParameter.isSystemParameter(restParameterName)) {
            warning(method, "Field name '%s' is a reserved keyword, please use a different name. "
                + "The reserved keywords are %s.",
                restParameterName,
                Joiner.on(", ").join(SystemParameter.allSystemParameters()).toLowerCase());
          }
        }
      }
    }
  }
}
