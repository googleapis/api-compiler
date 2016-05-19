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

package com.google.api.tools.framework.snippet;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Internal representation of a snippet definition.
 */
@AutoValue
abstract class Snippet {

  enum SnippetKind {
    REGULAR, ABSTRACT, OVERRIDE, PRIVATE
  }

  /**
   * The input name of the location describes the sequence of inputs from where this snippet was
   * obtained, separated by {@code File.pathSeparator}. If input A extends input B and B contains
   * this snippet, this will be {@code A;B}. The path is used to check consistency of overrides. (A
   * snippet can only override a snippet which was defined in its path. In particular, if A extends
   * both B and C, but B and C are unrelated, then a snippet from B cannot be overridden by a
   * snippet from C).
   */
  abstract Location location();

  abstract String name();
  abstract SnippetKind kind();
  @Nullable abstract Snippet overridden();
  abstract Layout layout();
  abstract ImmutableList<String> params();
  abstract ImmutableList<Elem> content();

  static Snippet create(Location location, String name, SnippetKind snippetKind,
      @Nullable Snippet overridden, Layout layout, Iterable<String> params, List<Elem> elems) {
    return new AutoValue_Snippet(location, name, snippetKind, overridden, layout,
        ImmutableList.copyOf(params), ImmutableList.copyOf(elems));
  }

  /**
   * Returns true if the snippet is bindable to an interface. That is true if it is not private,
   * or overrides a non-internal snippet.
   */
  boolean isBindable() {
    if (kind() == SnippetKind.OVERRIDE && overridden() != null) {
      return overridden().isBindable();
    }
    return kind() != SnippetKind.PRIVATE;
  }

  /**
   * Evaluates the snippet within the given context.
   */
  Doc eval(Context context, List<?> args) {
    Preconditions.checkArgument(args.size() == params().size());
    context = context.fork();
    context.enterScope();
    int i = 0;
    for (String param : params()) {
      context.bind(param, args.get(i++));
    }
    return evalElems(context, content()).group(layout().groupKind()).nest(layout().nest()).align();
  }

  /**
   * Helper to evaluate the iterable of elements into a document, composing them via
   * {@link Doc#add(Doc)}.
   */
  static Doc evalElems(Context context, Iterable<Elem> elems) {
    Doc result = Doc.EMPTY;
    for (Elem elem : elems) {
      result = result.add(Values.convertToDoc(elem.eval(context)));
    }
    return result;
  }

  /**
   * A display name for the snippet.
   */
  String displayName() {
    return String.format("%s(%s)", name(), params().size());
  }
}
