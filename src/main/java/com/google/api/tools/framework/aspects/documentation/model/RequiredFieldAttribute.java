/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.api.tools.framework.aspects.documentation.model;

import com.google.api.tools.framework.model.Field;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Key;

/**
 * An attribute attached to proto messages, representing the fields in the message
 * that are required for certain rest methods.
 */
public class RequiredFieldAttribute {

  /**
   * Key used to access this attribute
   */
  public static final Key<RequiredFieldAttribute> KEY = Key.get(RequiredFieldAttribute.class);

  /**
   * Maps rest method name to a set of field numbers in a message that are required to be specified
   * when that message is used in the context of a request to that REST method.
   */
  private final Multimap<String, Integer> methodToRequiredFieldMultimap = HashMultimap.create();

  /**
   * Maps field numbers in a message that are required to be specified when that message is used in
   * the context of a request to that REST method to a set of rest method names.
   */
  private final Multimap<Integer, String> fieldToMethodMultimap = HashMultimap.create();

  /**
   * Adds a mapping from a rest method to a field, which is specified
   * to be required in the request for that rest method.
   */
  public void addField(Field field,  String restMethodName) {
    methodToRequiredFieldMultimap.put(restMethodName, field.getNumber());
    fieldToMethodMultimap.put(field.getNumber(), restMethodName);
  }

  /** @return a map that maps rest method to its required fields */
  public Multimap<String, Integer> getMethodToRequiredFieldMap() {
    return methodToRequiredFieldMultimap;
  }

  /** @return a multimap that maps field numbers to methods that require it */
  public Multimap<Integer, String> getFieldToMethodMultimap() {
    return fieldToMethodMultimap;
  }
}
