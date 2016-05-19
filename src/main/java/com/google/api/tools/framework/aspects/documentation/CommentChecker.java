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

import com.google.api.tools.framework.aspects.documentation.model.DocumentationUtil;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Location;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;

/**
 * Runs the comment filter in order to diagnose issues. Does not actually do comment filtering.
 */
final class CommentChecker implements DocumentationProcessor {
  // TODO(user): comments should be treated like regular source elements.

  private final DiagCollector diagCollector;

  /**
   * Creates an instance of {@link CommentChecker}
   */
  public CommentChecker(DiagCollector diagCollector) {
    this.diagCollector = Preconditions.checkNotNull(diagCollector,
        "diagCollector should not be null.");
  }

  /**
   * See {@link DocumentationUtil#filter}.
   */
  @Override
  public String process(@Nullable String source, Location location,
      @Nullable Element element) {
    DocumentationUtil.filter(diagCollector, null, location, source);
    return source;
  }
}
