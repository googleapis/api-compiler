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

import com.google.api.tools.framework.model.ConfigSource.BuildAction;
import com.google.api.tools.framework.model.ConfigSource.Builder;
import com.google.api.tools.framework.model.testdata.ConfigSource.NestedConfig;
import com.google.api.tools.framework.model.testdata.ConfigSource.SomeConfig;
import com.google.common.truth.Truth;
import com.google.protobuf.Descriptors.FieldDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test for {@link ConfigSource}. */
@RunWith(JUnit4.class)
public class ConfigSourceTest {

  private static final Location L1 = new SimpleLocation("l1");
  private static final Location L2 = new SimpleLocation("l2");
  private static final Location L3 = new SimpleLocation("l3");

  private static final FieldDescriptor STRING_VALUE_FIELD =
      SomeConfig.getDescriptor().findFieldByName("string_value");

  private static final FieldDescriptor NESTED_CONFIG_FIELD =
      SomeConfig.getDescriptor().findFieldByName("nested_config");

  private static final FieldDescriptor REPEATED_STRING_VALUE_FIELD =
      SomeConfig.getDescriptor().findFieldByName("repeated_string_value");

  private static final FieldDescriptor REPEATED_NESTED_CONFIG_FIELD =
      SomeConfig.getDescriptor().findFieldByName("repeated_nested_config");

  private static final FieldDescriptor MAP_VALUE_FIELD =
      SomeConfig.getDescriptor().findFieldByName("map_value");

  private static final FieldDescriptor NESTED_MAP_VALUE_FIELD =
      SomeConfig.getDescriptor().findFieldByName("nested_map_value");

  private static final FieldDescriptor NESTED_STRING_VALUE_FIELD =
      NestedConfig.getDescriptor().findFieldByName("nested_string_value");

  private static final FieldDescriptor NESTED_REPEATED_INT32_VALUE_FIELD =
      NestedConfig.getDescriptor().findFieldByName("nested_repeated_int32_value");

  @Test
  public void simpleField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.setValue(STRING_VALUE_FIELD, null, "Hello World", L1);
    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getStringValue()).isEqualTo("Hello World");
    Truth.assertThat(source.getLocation(config, STRING_VALUE_FIELD.getName(), null)).isEqualTo(L1);

    builder = source.toBuilder();
    builder.setValue(STRING_VALUE_FIELD, null, "", L2);
    source = builder.build();
    config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getStringValue()).isEqualTo("");
    Truth.assertThat(source.getLocation(config, STRING_VALUE_FIELD.getName(), null)).isEqualTo(L2);
  }

  @Test
  public void nestedSimpleField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.setValue(STRING_VALUE_FIELD, null, "Hello World", L1);
    builder.withBuilder(
        NESTED_CONFIG_FIELD,
        null,
        new BuildAction() {
          @Override
          public void accept(ConfigSource.Builder subBuilder) {
            subBuilder.setValue(NESTED_STRING_VALUE_FIELD, null, "Sub World", L2);
          }
        });
    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getStringValue()).isEqualTo("Hello World");
    Truth.assertThat(source.getLocation(config, STRING_VALUE_FIELD.getName(), null)).isEqualTo(L1);
    Truth.assertThat(config.getNestedConfig().getNestedStringValue()).isEqualTo("Sub World");
    Truth.assertThat(
            source.getLocation(config.getNestedConfig(), NESTED_STRING_VALUE_FIELD.getName(), null))
        .isEqualTo(L2);
  }

  @Test
  public void simpleRepeatedField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.addValue(REPEATED_STRING_VALUE_FIELD, "a", L1);
    builder.addValue(REPEATED_STRING_VALUE_FIELD, "b", L2);
    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getRepeatedStringValueList()).containsExactly("a", "b");
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 0))
        .isEqualTo(L1);
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 1))
        .isEqualTo(L2);

    builder = source.toBuilder();
    builder.addValue(REPEATED_STRING_VALUE_FIELD, "c", L3);
    source = builder.build();
    config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getRepeatedStringValueList()).containsExactly("a", "b", "c");
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 0))
        .isEqualTo(L1);
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 1))
        .isEqualTo(L2);
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 2))
        .isEqualTo(L3);
  }

  @Test
  public void nestedRepeatedField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.withAddedBuilder(
        REPEATED_NESTED_CONFIG_FIELD,
        new BuildAction() {
          @Override
          public void accept(Builder nestedBuilder) {
            nestedBuilder.addValue(NESTED_REPEATED_INT32_VALUE_FIELD, 0, L1);
            nestedBuilder.addValue(NESTED_REPEATED_INT32_VALUE_FIELD, 1, L2);
          }
        });
    builder.withAddedBuilder(
        REPEATED_NESTED_CONFIG_FIELD,
        new BuildAction() {
          @Override
          public void accept(Builder nestedBuilder) {
            nestedBuilder.addValue(NESTED_REPEATED_INT32_VALUE_FIELD, 2, L3);
          }
        });

    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getRepeatedNestedConfigCount()).isEqualTo(2);
    NestedConfig nested1 = config.getRepeatedNestedConfig(0);
    NestedConfig nested2 = config.getRepeatedNestedConfig(1);
    Truth.assertThat(nested1.getNestedRepeatedInt32ValueList()).containsExactly(0, 1);
    Truth.assertThat(nested2.getNestedRepeatedInt32ValueList()).containsExactly(2);
    Truth.assertThat(source.getLocation(nested1, NESTED_REPEATED_INT32_VALUE_FIELD.getName(), 0))
        .isEqualTo(L1);
    Truth.assertThat(source.getLocation(nested1, NESTED_REPEATED_INT32_VALUE_FIELD.getName(), 1))
        .isEqualTo(L2);
    Truth.assertThat(source.getLocation(nested2, NESTED_REPEATED_INT32_VALUE_FIELD.getName(), 0))
        .isEqualTo(L3);
  }

  @Test
  public void mapField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.setValue(MAP_VALUE_FIELD, "X", "A", L1);
    builder.setValue(MAP_VALUE_FIELD, "Y", "B", L2);

    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getMapValue().get("X")).isEqualTo("A");
    Truth.assertThat(config.getMapValue().get("Y")).isEqualTo("B");
    Truth.assertThat(source.getLocation(config, MAP_VALUE_FIELD.getName(), "X")).isEqualTo(L1);
    Truth.assertThat(source.getLocation(config, MAP_VALUE_FIELD.getName(), "Y")).isEqualTo(L2);
  }

  @Test
  public void nestedMapField() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.withBuilder(
        NESTED_MAP_VALUE_FIELD,
        "X",
        new BuildAction() {
          @Override
          public void accept(ConfigSource.Builder subBuilder) {
            subBuilder.setValue(NESTED_STRING_VALUE_FIELD, null, "A", L1);
          }
        });
    builder.withBuilder(
        NESTED_MAP_VALUE_FIELD,
        "Y",
        new BuildAction() {
          @Override
          public void accept(ConfigSource.Builder subBuilder) {
            subBuilder.setValue(NESTED_STRING_VALUE_FIELD, null, "B", L2);
          }
        });

    ConfigSource source = builder.build();
    SomeConfig config = (SomeConfig) source.getConfig();

    NestedConfig nested1 = config.getNestedMapValue().get("X");
    NestedConfig nested2 = config.getNestedMapValue().get("Y");

    Truth.assertThat(nested1.getNestedStringValue()).isEqualTo("A");
    Truth.assertThat(nested2.getNestedStringValue()).isEqualTo("B");

    Truth.assertThat(source.getLocation(nested1, NESTED_STRING_VALUE_FIELD.getName(), null))
        .isEqualTo(L1);
    Truth.assertThat(source.getLocation(nested2, NESTED_STRING_VALUE_FIELD.getName(), null))
        .isEqualTo(L2);
  }

  @Test
  public void mergeTest() {
    ConfigSource.Builder builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.setValue(STRING_VALUE_FIELD, null, "A", L1);
    builder.withBuilder(
        NESTED_CONFIG_FIELD,
        null,
        new BuildAction() {
          @Override
          public void accept(ConfigSource.Builder subBuilder) {
            subBuilder.setValue(NESTED_STRING_VALUE_FIELD, null, "B", L1);
          }
        });
    builder.addValue(REPEATED_STRING_VALUE_FIELD, "a", L1);
    ConfigSource source = builder.build();

    builder = ConfigSource.newBuilder(SomeConfig.getDefaultInstance());
    builder.setValue(STRING_VALUE_FIELD, null, "", L2);
    builder.withBuilder(
        NESTED_CONFIG_FIELD,
        null,
        new BuildAction() {
          @Override
          public void accept(ConfigSource.Builder subBuilder) {
            subBuilder.setValue(NESTED_STRING_VALUE_FIELD, null, "C", L2);
          }
        });
    builder.addValue(REPEATED_STRING_VALUE_FIELD, "b", L2);
    ConfigSource source2 = builder.build();

    source = source.toBuilder().mergeFrom(source2).build();

    SomeConfig config = (SomeConfig) source.getConfig();
    Truth.assertThat(config.getStringValue()).isEqualTo("");
    Truth.assertThat(source.getLocation(config, STRING_VALUE_FIELD.getName(), null)).isEqualTo(L2);

    Truth.assertThat(config.getRepeatedStringValueList()).containsExactly("a", "b");
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 0))
        .isEqualTo(L1);
    Truth.assertThat(source.getLocation(config, REPEATED_STRING_VALUE_FIELD.getName(), 1))
        .isEqualTo(L2);

    NestedConfig nested = config.getNestedConfig();
    Truth.assertThat(nested.getNestedStringValue()).isEqualTo("C");
    Truth.assertThat(source.getLocation(nested, NESTED_STRING_VALUE_FIELD.getName(), null))
        .isEqualTo(L2);
  }
}
