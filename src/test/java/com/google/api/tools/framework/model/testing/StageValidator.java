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

package com.google.api.tools.framework.model.testing;

import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Visitor;
import com.google.api.tools.framework.model.stages.Requires;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import java.util.Collection;
import java.util.Set;
import junit.framework.Assert;

/**
 * A class which does stage validation of model elements. Walks the tree and checks whether all
 * fields which are guarded by the {@link Requires} annotation have a valid value according to the
 * specified stages.
 */
public class StageValidator extends Visitor {

  /**
   * Runs stage validation and returns a list of errors.
   */
  public static Collection<String> validate(Collection<Key<?>> stages, Element element) {
    StageValidator validator = new StageValidator(stages);
    validator.visit(element);
    return validator.errors;
  }

  /**
   * Runs stage validation and produces an assertion failure if it has errors.
   */
  public static void assertStages(Collection<Key<?>> stages, Element element) {
    Collection<String> errors = validate(stages, element);
    if (!errors.isEmpty()) {
      Assert.fail(String.format("Stage validation failed: %s", Joiner.on("\n").join(errors)));
    }
  }

  private final Set<String> errors = Sets.newLinkedHashSet();
  private final Set<Key<?>> stages;

  private StageValidator(Collection<Key<?>> stages) {
    this.stages = ImmutableSet.copyOf(stages);
  }

  @VisitsBefore
  void validate(Element element) {
    for (java.lang.reflect.Field field : element.getClass().getDeclaredFields()) {
      if (field.isAnnotationPresent(Requires.class)) {
        // A field protected by the @Requires annotation.
        Requires req = field.getAnnotation(Requires.class);
        Class<?> stageClass = req.value();
        if (stages.contains(Key.get(stageClass))) {
          // Check the value of this field not being null.
          try {
            field.setAccessible(true);
            Object result = field.get(element);
            if (result == null) {
              errors.add(String.format("@Requires(%s.class) not satisfied on %s.%s",
                  stageClass.getSimpleName(), field.getDeclaringClass().getSimpleName(),
                  field.getName()));
            }
          } catch (IllegalAccessException | IllegalArgumentException e) {
            errors.add(String.format(
                "@Requires(%s.class) not satisfied on %s.%s (throws %s)",
                stageClass.getSimpleName(), field.getDeclaringClass().getSimpleName(),
                field.getName(), e.getClass().getSimpleName()));
          }
        }
      }
    }
  }
}
