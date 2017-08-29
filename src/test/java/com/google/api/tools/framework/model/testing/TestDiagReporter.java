/*
 * Copyright 2017 Google Inc.
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

import com.google.api.tools.framework.model.ConfigLocationResolver;
import com.google.api.tools.framework.model.DiagCollector;
import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagSuppressor;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.protobuf.Message;

/** Diag reporter used for testing. Always returns {@link SimpleLocation#UNKNOWN} for location. */
public class TestDiagReporter {

  public static DiagReporter createForTest() {
    return createForTest(new SimpleDiagCollector());
  }

  public static DiagReporter createForTest(DiagCollector diagCollector) {
    return new DiagReporter(
        diagCollector, new DiagSuppressor(diagCollector), new TestConfigLocationResolver());
  }

  private static final class TestConfigLocationResolver implements ConfigLocationResolver {

    @Override
    public Location getLocationInConfig(Message message, String fieldName) {
      return SimpleLocation.UNKNOWN;
    }

    @Override
    public Location getLocationOfRepeatedFieldInConfig(
        Message message, String fieldName, Object elementKey) {
      return SimpleLocation.UNKNOWN;
    }
  }
}
