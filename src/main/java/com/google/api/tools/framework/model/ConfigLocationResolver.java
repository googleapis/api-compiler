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

import com.google.protobuf.Message;

/**
 * An interface representing an object which collects diagnostics.
 */
public interface ConfigLocationResolver {

  /**
   * Returns the service config file location of the given named field in the (sub)message.
   * Returns {@link SimpleLocation#TOPLEVEL} if the location is not known.
   */
  Location getLocationInConfig(Message message, String fieldName);

  /**
   * Returns the service config file location of the given named field in the (sub)message. The key
   * identifies the key of the map. For repeated fields, the element key is a
   * zero-based index.
   * Returns {@link SimpleLocation#TOPLEVEL} if the location is not known.
   */
  Location getLocationOfRepeatedFieldInConfig(
      Message message, String fieldName, Object elementKey);
}
