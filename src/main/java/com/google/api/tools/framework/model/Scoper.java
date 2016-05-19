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

package com.google.api.tools.framework.model;

import com.google.common.base.Predicate;

import java.util.Set;

/**
 * A scoper is an object which allows to traverse the model only looking at elements which are 'in
 * scope'.
 */
public interface Scoper {

  /**
   * An unrestricted scoper with all elements in scope.
   */
  public static final Scoper UNRESTRICTED = new Scoper() {

    @Override
    public boolean isReachable(ProtoElement elem) {
      return true;
    }

    @Override
    public <E extends ProtoElement> Iterable<E> filter(Iterable<E> elems) {
      return elems;
    }

    @Override
    public boolean hasUnreachableDescendants(ProtoElement elem) {
      return false;
    }

    @Override
    public Scoper restrict(Model model, Set<String> visibilityLabels) {
      throw new IllegalStateException("not supported on unrestricted scoper");
    }

    @Override
    public Scoper restrict(Predicate<ProtoElement> predicate, String errorContext) {
      throw new IllegalStateException("not supported on unrestricted scoper");
    }
  };

  /**
   * Returns true if the element is reachable with this scoper.
   */
  boolean isReachable(ProtoElement elem);

  /**
   * Filters the given iterable to the elements reachable with this scoper.
   */
  <E extends ProtoElement> Iterable<E> filter(Iterable<E> elems);

  /**
   * Returns true if the given message or enum type has any hidden, non-reachable descendants, e.g.
   * fields which are not reachable, or which have types which have non-reachable elements.
   */
  boolean hasUnreachableDescendants(ProtoElement elem);

  /**
   * Returns a scoper which restricts a model to the elements reachable with given visibility labels
   * applied. This may produce errors if scoping rules are violated, or if the visibility label
   * combination is not available.
   */
  Scoper restrict(Model model, Set<String> visibilityLabels);

  /**
   * Returns a scoper which refines restrictions on this scoper using the predicate. This may
   * produce errors if scoping rules are violated. The passed errorContext is used in error
   * messages to identify the source of the error.
   */
  Scoper restrict(final Predicate<ProtoElement> predicate, String errorContext);
}
