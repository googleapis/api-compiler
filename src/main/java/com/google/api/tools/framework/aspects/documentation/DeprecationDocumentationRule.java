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

package com.google.api.tools.framework.aspects.documentation;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.aspects.documentation.model.DocumentationUtil;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.base.Strings;

/**
 * Style rule checking for issues with deprecation documentation.
 */
class DeprecationDocumentationRule extends LintRule<ProtoElement> {

  DeprecationDocumentationRule(ConfigAspectBase aspect) {
    super(aspect, "deprecation", ProtoElement.class);
  }

  @Override public void run(ProtoElement elem) {
    final boolean isDeprecated = elem.isDeprecated();
    final String doc = DocumentationUtil.getDeprecationDescription(elem);

    if (!isDeprecated && !Strings.isNullOrEmpty(doc)) {
      warning(elem,
          "'%s' is not marked deprecated=true, so its deprecation_description will be ignored.",
          elem);
    }
  }
}
