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

package com.google.api.tools.framework.aspects.documentation.model;

import com.google.inject.Key;

/**
 * An attribute attached by this aspect to elements, indicating that this element's documentation
 * should be directly included into the documentation pages that reference it, rather than being
 * linked to.
 *
 * TODO(user): In the future, this may be used analogously in the Discovery document, as well. In
 * that case, consider moving this attribute outside of the documentation package.
 */
public class InliningAttribute {

  /**
   * Key used to access this attribute.
   */
  public static final Key<InliningAttribute> KEY = Key.get(InliningAttribute.class);
}
