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

package com.google.api.tools.framework.aspects.documentation;

import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Model;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.List;

/**
 * Processor set that contains a set of registered {@link DocumentationProcessor}s to process
 * given documentation in a chained way.
 */
public class DocumentationProcessorSet {

  private final List<DocumentationProcessor> processors;

  /**
   * Returns {@link DocumentationProcessorSet} with standard processors registered.
   */
  public static DocumentationProcessorSet standardSetup(Model model) {
    return new DocumentationProcessorSet(
        Lists.newArrayList(
            new CommentReferenceResolver(model),
            new SourceNormalizer(model.getDiagReporter(), model.getDataPath()),
            new CommentChecker(model.getDiagReporter())));
  }

  public DocumentationProcessorSet(Collection<DocumentationProcessor> processors) {
    Preconditions.checkNotNull(processors, "processors should not be null");
    this.processors = Lists.newArrayList(processors);
  }

  /**
   * Processes given documentation source by the registered processor chain. Returns processed
   * documentation string.
   */
  public String process(String source, LocationContext location, Element element) {
    if (Strings.isNullOrEmpty(source)) {
      return source;
    }

    String result = source;
    for (DocumentationProcessor processor : processors) {
      result = processor.process(result, location, element);
    }
    return result;
  }
}
