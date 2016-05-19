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

package com.google.api.tools.framework.aspects.documentation.source;

import com.google.api.tools.framework.aspects.documentation.model.DeprecationDescriptionAttribute;
import com.google.api.tools.framework.aspects.documentation.model.PageAttribute;
import com.google.api.tools.framework.aspects.documentation.model.ResourceAttribute;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.MessageType;

/**
 * Represents Docgen instructions other than file inclusion:
 * (== code arg ==)
 */
public class Instruction extends ContentElement {

  private static final String PAGE_INSTRUCTION = "page";
  private static final String SUPPRESS_WARNING_INSTRUCTION = "suppress_warning";
  private static final String RESOURCE_INSTRUCTION = "resource_for";
  private static final String DEPRECATION_DESCRIPTION = "deprecation_description";

  private final String code;
  private final String arg;

  public Instruction(String code, String arg, int startIndex, int endIndex,
      DiagCollector diagCollector, Location sourceLocation) {
    super(startIndex, endIndex, diagCollector, sourceLocation);
    this.code = code.trim();
    this.arg = arg.trim();
  }

  /**
   * Returns the instruction code
   */
  public String getCode() {
    return code;
  }

  /**
   * Returns the instruction argument.
   */
  public String getArg() {
    return arg;
  }

  /**
   * Return the content (empty for instruction).
   */
  @Override public String getContent() {
    return "";
  }

  /**
   * Evaluate the instruction in context of given element.
   */
  public void evalute(Element element) {
    switch (code) {
      case PAGE_INSTRUCTION:
        element.putAttribute(PageAttribute.KEY, PageAttribute.create(arg));
        break;
      case SUPPRESS_WARNING_INSTRUCTION:
        element.getModel().addSupressionDirective(element, arg);
        break;
      case RESOURCE_INSTRUCTION:
        if (!(element instanceof MessageType)) {
          element.getModel().getDiagCollector().addDiag(Diag.error(element.getLocation(),
              "resource instruction must be associated with a message declaration, but '%s' "
              + "is not a message.",
              element.getFullName()));
        } else {
          element.addAttribute(ResourceAttribute.KEY, ResourceAttribute.create(arg));
        }
        break;
      case DEPRECATION_DESCRIPTION:
        element.putAttribute(
            DeprecationDescriptionAttribute.KEY, DeprecationDescriptionAttribute.create(arg));
        break;
      default:
        element.getModel().getDiagCollector().addDiag(Diag.error(element.getLocation(),
            "documentation instruction '%s' unknown.", code));
    }
  }
}
