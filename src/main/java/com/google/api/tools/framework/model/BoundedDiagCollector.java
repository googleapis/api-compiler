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

package com.google.api.tools.framework.model;

import com.google.api.tools.framework.model.Diag.Kind;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implementation of {@link DiagCollector} that will contain a bounded amount of {@link Diag}s. If
 * more that the max {@link Diag} of type {@link Kind#ERROR} are added to the collector, it will
 * throw an {@link TooManyDiagsException}. If more than the max for any other type are added to the
 * collector, it will add a 'too many diags' message and silently ignore additional messages for
 * that type.
 */
public class BoundedDiagCollector implements DiagCollector {

  public static final Integer DEFAULT_MAX_ERRORS = 500;
  public static final Integer DEFAULT_MAX_WARNINGS = 5000;
  private static final Map<Kind, Integer> DEFAULT_CAPACITY =
      ImmutableMap.<Kind, Integer>builder()
          .put(Kind.ERROR, DEFAULT_MAX_ERRORS)
          .put(Kind.WARNING, DEFAULT_MAX_WARNINGS)
          .build();

  private final List<Diag> diags = new ArrayList<>();
  private final Map<Kind, Integer> capacityByKind;

  public BoundedDiagCollector(Map<Kind, Integer> capacityByKind) {
    this.capacityByKind = Maps.newEnumMap(capacityByKind);
  }

  public BoundedDiagCollector() {
    this(DEFAULT_CAPACITY);
  }

  @Override
  public void addDiag(Diag diag) {
    int capacity =
        capacityByKind.containsKey(diag.getKind()) ? capacityByKind.get(diag.getKind()) : 0;
    int currentCount = listByKind(diag.getKind()).size();
    if (currentCount < capacity) {
      diags.add(diag);
    } else if (currentCount == capacity) {
      final String msg =
          String.format(
              "Hit max count(%d) of allowed %s. No more diags of this kind will be logged.",
              capacity,
              StringUtils.lowerCase(diag.getKind().toString()));

      diags.add(Diag.create(SimpleLocation.TOPLEVEL, "%s", diag.getKind(), msg));

      // Try to short circuit proceeding in a known bad state (to avoid potential timeouts in
      // trying process REALLY bad configuration).
      if (diag.getKind() == Kind.ERROR) {
        throw new TooManyDiagsException(msg);
      }
    }
  }

  public List<Diag> getDiags() {
    return ImmutableList.<Diag>copyOf(diags);
  }

  @Override
  public int getErrorCount() {
    return getErrors().size();
  }

  @Override
  public boolean hasErrors() {
    return getErrorCount() > 0;
  }

  @Override
  public String toString() {
    return Joiner.on("\n").join(diags);
  }

  public List<Diag> getErrors() {
    return listByKind(Kind.ERROR);
  }

  private List<Diag> listByKind(final Kind kind) {
    return FluentIterable.from(diags)
        .filter(
            new Predicate<Diag>() {
              @Override
              public boolean apply(Diag input) {
                return input.getKind() == kind;
              }
            })
        .toList();
  }

  /**
  *  Thrown if more that the max {@link Diag} of type {@link Kind#ERROR} are added to the collector.
  */
  public class TooManyDiagsException extends RuntimeException {
    public TooManyDiagsException(String msg) {
      super(msg);
    }
  }
}
