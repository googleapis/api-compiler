/*
 * Copyright (C) 2017 Google, Inc.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import javax.annotation.Nullable;

/** Implementation of {@link Experiments} that is immutable after construction. */
public class ExperimentsImpl implements Experiments {

  private final ImmutableSet<String> experiments;

  public static ExperimentsImpl none() {
    return new ExperimentsImpl();
  }

  public ExperimentsImpl(String... experiments) {
    this(Lists.newArrayList(experiments));
  }

  public ExperimentsImpl(@Nullable Iterable<String> experiments) {
    if (experiments == null) {
      this.experiments = ImmutableSet.of();
    } else {
      this.experiments = ImmutableSet.copyOf(experiments);
    }
  }

  @Override
  public boolean isExperimentEnabled(String experiment) {
    return experiments.contains(experiment);
  }
}
