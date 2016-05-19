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

package com.google.api.tools.framework.aspects.system;

import com.google.api.Service.Builder;
import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Model;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import java.util.List;
import java.util.Set;

/**
 * Configuration aspect for system-level configuration (e.g. system_types).
 */
public class SystemConfigAspect extends ConfigAspectBase {
  public static SystemConfigAspect create(Model model) {
    return new SystemConfigAspect(model);
  }

  private SystemConfigAspect(Model model) {
    super(model, "system");
  }

  /**
   * Returns an empty list since this aspect does not depend on any other aspects.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.of();
  }

  @Override
  public void startNormalization(Builder builder) {
    Set<String> userDefinedTypes = Sets.newHashSet();
    for (com.google.protobuf.Type type : builder.getTypesList()) {
      userDefinedTypes.add(type.getName());
    }
  }
}
