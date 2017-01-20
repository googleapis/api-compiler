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

package com.google.api.tools.framework.aspects.naming;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.LintRule;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.EnumType;
import com.google.api.tools.framework.model.EnumValue;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Config aspect for validating naming rules.
 */
public class NamingConfigAspect extends ConfigAspectBase {

  public static NamingConfigAspect create(Model model) {
    return new NamingConfigAspect(model);
  }

  // TODO(user): refine those rules as naming conventions for OP are finalized.

  private static final String UPPER_CAMEL_REGEX = "[A-Z][A-Za-z0-9]*";
  private static final String UPPER_UNDERSCORE_REGEX = "[A-Z][A-Z0-9_]*";
  private static final String LOWER_UNDERSCORE_REGEX = "[a-z][a-z0-9_]*";
  private static final String PACKAGE_REGEX = String.format("%s([.]%s)*",
      LOWER_UNDERSCORE_REGEX, LOWER_UNDERSCORE_REGEX);
  private static final String PROTO_FILE_REGEX = String.format("%s.proto", LOWER_UNDERSCORE_REGEX);

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  private NamingConfigAspect(Model model) {
    super(model, "naming");
    registerLintRule(new ServiceNameRule(this));

    registerLintRule(new NameAbbreviationRule(this));

    // Package name.
    registerLintRule(new RegexRule<>(ProtoFile.class, "lower-camel-qualified", PACKAGE_REGEX,
        new Function<ProtoFile, String>() {
          @Override public String apply(ProtoFile elem) {
            return elem.getFullName();
          }
    }));

    // Proto file name.
    registerLintRule(new RegexRule<>(ProtoFile.class, "lower-underscore-proto", PROTO_FILE_REGEX,
        new Function<ProtoFile, String>() {
          @Override public String apply(ProtoFile protoFile) {
            String fileName = protoFile.getSimpleName();
            if (fileName.contains("/")) {  // Extract the file name from relative path.
              fileName = fileName.substring(fileName.lastIndexOf("/") + 1, fileName.length());
            }
            return fileName;
          }
    }));

    // Interface name.
    registerLintRule(new RegexRule<>(Interface.class, "upper-camel", UPPER_CAMEL_REGEX,
        new Function<Interface, String>() {
          @Override public String apply(Interface elem) {
            return elem.getSimpleName();
          }
    }));

    // Method name.
    registerLintRule(new RegexRule<>(Method.class, "upper-camel", UPPER_CAMEL_REGEX,
        new Function<Method, String>() {
          @Override public String apply(Method elem) {
            return elem.getSimpleName();
          }
    }));

    // Message name.
    registerLintRule(new RegexRule<>(MessageType.class, "upper-camel", UPPER_CAMEL_REGEX,
        new Function<MessageType, String>() {
          @Override public String apply(MessageType elem) {
            return elem.getSimpleName();
          }
    }));

    // Enum type Name
    registerLintRule(new RegexRule<>(EnumType.class, "upper-camel", UPPER_CAMEL_REGEX,
        new Function<EnumType, String>() {
          @Override public String apply(EnumType elem) {
            return elem.getSimpleName();
          }
    }));

    // Enum value Name
    registerLintRule((new RegexRule<>(EnumValue.class, "upper-underscore",
        UPPER_UNDERSCORE_REGEX,
        new Function<EnumValue, String>() {
          @Override public String apply(EnumValue elem) {
            return elem.getSimpleName();
          }
    })));
  }

  private class RegexRule<E extends Element> extends LintRule<E> {

    private final String ruleName;
    private final Pattern pattern;
    private final Function<E, String> nameExtractor;

    private RegexRule(Class<E> elemClass, String ruleName,
        String regex, Function<E, String> nameExtractor) {
      super(NamingConfigAspect.this, ruleName, elemClass);
      this.ruleName = ruleName;
      pattern = Pattern.compile(regex);
      this.nameExtractor = nameExtractor;
    }

    @Override public void run(E elem) {
      String name = nameExtractor.apply(elem);
      if (!pattern.matcher(name).matches()) {
        warning(elem, "Name '%s' is not matching %s conventions (pattern '%s')", name, ruleName,
            pattern);
      }
    }
  }
}
