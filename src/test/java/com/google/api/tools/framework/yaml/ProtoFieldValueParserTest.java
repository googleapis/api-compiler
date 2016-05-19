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

package com.google.api.tools.framework.yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.api.tools.framework.yaml.ProtoFieldValueParser.ParseException;
import com.google.api.tools.framework.yaml.ProtoFieldValueParserProto.TestEnum;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link ProtoFieldValueParser}
 */
@RunWith(JUnit4.class)

public class ProtoFieldValueParserTest {

  @Test
  public void testParseBoolean() {
    assertTrue(ProtoFieldValueParser.parseBoolean("true"));
    assertTrue(ProtoFieldValueParser.parseBoolean("t"));
    assertTrue(ProtoFieldValueParser.parseBoolean("1"));

    assertFalse(ProtoFieldValueParser.parseBoolean("false"));
    assertFalse(ProtoFieldValueParser.parseBoolean("f"));
    assertFalse(ProtoFieldValueParser.parseBoolean("0"));
  }

  @Test (expected = ParseException.class)
  public void testParseBoolean_invalid() {
    ProtoFieldValueParser.parseBoolean("foo");
  }

  @Test
  public void testParseInt() {
      assertEquals(0, ProtoFieldValueParser.parseInt32("0"));
      assertEquals(1, ProtoFieldValueParser.parseInt32("1"));
      assertEquals(-1, ProtoFieldValueParser.parseInt32("-1"));
      assertEquals(12345, ProtoFieldValueParser.parseInt32("12345"));
      assertEquals(-12345, ProtoFieldValueParser.parseInt32("-12345"));
      assertEquals(2147483647, ProtoFieldValueParser.parseInt32("2147483647"));
      assertEquals(-2147483648, ProtoFieldValueParser.parseInt32("-2147483648"));

      assertEquals(0, ProtoFieldValueParser.parseUInt32("0"));
      assertEquals(1, ProtoFieldValueParser.parseUInt32("1"));
      assertEquals(12345, ProtoFieldValueParser.parseUInt32("12345"));
      assertEquals(2147483647, ProtoFieldValueParser.parseUInt32("2147483647"));
      assertEquals((int) 2147483648L, ProtoFieldValueParser.parseUInt32("2147483648"));
      assertEquals((int) 4294967295L, ProtoFieldValueParser.parseUInt32("4294967295"));

      assertEquals(0L, ProtoFieldValueParser.parseInt64("0"));
      assertEquals(1L, ProtoFieldValueParser.parseInt64("1"));
      assertEquals(-1L, ProtoFieldValueParser.parseInt64("-1"));
      assertEquals(12345L, ProtoFieldValueParser.parseInt64("12345"));
      assertEquals(-12345L, ProtoFieldValueParser.parseInt64("-12345"));
      assertEquals(2147483647L, ProtoFieldValueParser.parseInt64("2147483647"));
      assertEquals(-2147483648L, ProtoFieldValueParser.parseInt64("-2147483648"));
      assertEquals(4294967295L, ProtoFieldValueParser.parseInt64("4294967295"));
      assertEquals(4294967296L, ProtoFieldValueParser.parseInt64("4294967296"));
      assertEquals(9223372036854775807L,
                   ProtoFieldValueParser.parseInt64("9223372036854775807"));
      assertEquals(-9223372036854775808L,
                   ProtoFieldValueParser.parseInt64("-9223372036854775808"));

      assertEquals(0L, ProtoFieldValueParser.parseUInt64("0"));
      assertEquals(1L, ProtoFieldValueParser.parseUInt64("1"));
      assertEquals(12345L, ProtoFieldValueParser.parseUInt64("12345"));
      assertEquals(2147483647L, ProtoFieldValueParser.parseUInt64("2147483647"));
      assertEquals(4294967295L, ProtoFieldValueParser.parseUInt64("4294967295"));
      assertEquals(4294967296L, ProtoFieldValueParser.parseUInt64("4294967296"));
      assertEquals(9223372036854775807L,
                   ProtoFieldValueParser.parseUInt64("9223372036854775807"));
      assertEquals(-9223372036854775808L,
                   ProtoFieldValueParser.parseUInt64("9223372036854775808"));
      assertEquals(-1L, ProtoFieldValueParser.parseUInt64("18446744073709551615"));

      // Hex
      assertEquals(0x1234abcd, ProtoFieldValueParser.parseInt32("0x1234abcd"));
      assertEquals(-0x1234abcd, ProtoFieldValueParser.parseInt32("-0x1234abcd"));
      assertEquals(-1, ProtoFieldValueParser.parseUInt64("0xffffffffffffffff"));
      assertEquals(0x7fffffffffffffffL,
                   ProtoFieldValueParser.parseInt64("0x7fffffffffffffff"));

      // Octal
      assertEquals(01234567, ProtoFieldValueParser.parseInt32("01234567"));
  }

  @Test (expected = ParseException.class)
  public void testParseInt32_outOfRange_positive() {
    ProtoFieldValueParser.parseInt32("2147483648");
  }

  @Test (expected = ParseException.class)
  public void testParseInt32_outOfRange_negative() {
    ProtoFieldValueParser.parseInt32("-2147483649");
  }

  @Test (expected = ParseException.class)
  public void testParseUInt32_outOfRange_positive() {
    ProtoFieldValueParser.parseUInt32("4294967296");
  }

  @Test (expected = ParseException.class)
  public void testParseUInt32_outOfRange_negative() {
    ProtoFieldValueParser.parseUInt32("-1");
  }

  @Test (expected = ParseException.class)
  public void testParseInt64_outOfRange_positive() {
    ProtoFieldValueParser.parseInt64("9223372036854775808");
  }

  @Test (expected = ParseException.class)
  public void testParseInt64_outOfRange_negative() {
    ProtoFieldValueParser.parseInt64("-9223372036854775809");
  }

  @Test (expected = ParseException.class)
  public void testParseUInt64_outOfRange_positive() {
    ProtoFieldValueParser.parseUInt64("18446744073709551616");
  }

  @Test (expected = ParseException.class)
  public void testParseUInt64_outOfRange_negative() {
    ProtoFieldValueParser.parseUInt64("-1");
  }

  @Test (expected = ParseException.class)
  public void testParse32_notANumber() {
    ProtoFieldValueParser.parseUInt64("foo");
  }

  @Test
  public void testParseDouble() {
    double eps = 0.000001;
    assertEquals(123.52222d, ProtoFieldValueParser.parseDouble("123.52222"), eps);
    assertEquals(-123.52222d, ProtoFieldValueParser.parseDouble("-123.52222"), eps);
    assertEquals(Double.POSITIVE_INFINITY, ProtoFieldValueParser.parseDouble("Infinity"), eps);
    assertEquals(Double.NEGATIVE_INFINITY, ProtoFieldValueParser.parseDouble("-Infinity"), eps);
    assertEquals(Double.valueOf("1.23E17"), ProtoFieldValueParser.parseDouble("1.23E17"), eps);
  }

  @Test (expected = ParseException.class)
  public void testParseDouble_notANumber() {
    ProtoFieldValueParser.parseDouble("foo");
  }

  @Test
  public void testParseFloat() {
    float eps = 0.0001f;
    assertEquals(123.5222f, ProtoFieldValueParser.parseFloat("123.5222"), eps);
    assertEquals(-123.5222f, ProtoFieldValueParser.parseFloat("-123.52222"), eps);
    assertEquals(Float.POSITIVE_INFINITY, ProtoFieldValueParser.parseFloat("Infinity"), eps);
    assertEquals(Float.NEGATIVE_INFINITY, ProtoFieldValueParser.parseFloat("-Infinity"), eps);
    assertEquals(Float.valueOf("1.23E2"), ProtoFieldValueParser.parseFloat("1.23E2"), eps);
  }

  @Test (expected = ParseException.class)
  public void testParseFloat_notANumber() {
    ProtoFieldValueParser.parseFloat("foo");
  }

  @Test
  public void testParseEnum() {
    assertEquals(TestEnum.VALUE1.getValueDescriptor(),
        ProtoFieldValueParser.parseEnum(TestEnum.getDescriptor(), "2"));
    assertEquals(TestEnum.VALUE1.getValueDescriptor(),
        ProtoFieldValueParser.parseEnum(TestEnum.getDescriptor(), "VALUE1"));
  }

  @Test (expected = ParseException.class)
  public void testParseEnum_invalidValueNumber() {
    assertEquals(TestEnum.VALUE1.getValueDescriptor(),
        ProtoFieldValueParser.parseEnum(TestEnum.getDescriptor(), "11"));
  }

  @Test (expected = ParseException.class)
  public void testParseEnum_invalidValueName() {
    assertEquals(TestEnum.VALUE1.getValueDescriptor(),
        ProtoFieldValueParser.parseEnum(TestEnum.getDescriptor(), "DUMMY"));
  }
}
