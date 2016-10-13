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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/**
 * Tests for {@link SymbolTable}.
 */
@RunWith(JUnit4.class)
public class SymbolTableTest {

  private final MessageType message1 = Mockito.mock(MessageType.class);
  private final TypeRef m1 = TypeRef.of(message1);
  private final String m1Name = "a.b.m";

  private final MessageType message2 = Mockito.mock(MessageType.class);
  private final TypeRef m2 = TypeRef.of(message2);
  private final String m2Name = "a.b.m.m";

  private final MessageType message3 = Mockito.mock(MessageType.class);
  private final TypeRef m3 = TypeRef.of(message3);
  private final String m3Name = "a.m";

  private final MessageType message4 = Mockito.mock(MessageType.class);
  private final TypeRef m4 = TypeRef.of(message4);
  private final String m4Name = "a.b.a.n";

  private final Interface s1 = Mockito.mock(Interface.class);
  private final Interface s2 = Mockito.mock(Interface.class);
  private final Method rpc1 = Mockito.mock(Method.class);

  private final SymbolTable table =
      new SymbolTable(
          ImmutableMap.of("a.b.s1", s1, "a.b.s2", s2),
          ImmutableMap.of(m1Name, m1, m2Name, m2, m3Name, m3, m4Name, m4),
          ImmutableSet.of("foo", "bar"),
          ImmutableMap.of("rpc1", Lists.newArrayList(rpc1)),
          ImmutableSet.of("a", "a.b", "a.b.a"));

  @Test public void testResolveInterface() {
    Assert.assertSame(s1, table.resolveInterface("a.b",  "s1"));
    Assert.assertSame(s1, table.resolveInterface("a.b",  "b.s1"));
    Assert.assertSame(s1, table.resolveInterface("a.b",  "a.b.s1"));
    Assert.assertSame(s1, table.resolveInterface("a.b",  ".a.b.s1"));
  }

  @Test public void testResolveType() {
    Assert.assertSame(TypeRef.of(Type.TYPE_INT32), table.resolveType("a.b",  "int32"));
    Assert.assertSame(m1, table.resolveType("a.b",  "m"));
    Assert.assertSame(m2, table.resolveType("a.b",  "m.m"));
    Assert.assertSame(m2, table.resolveType("a.b.m",  "m"));
    Assert.assertSame(m1, table.resolveType("a.b.m",  ".a.b.m"));
    Assert.assertSame(m3, table.resolveType("a.b.a",  "a.m")); // Note: different from resolveType()
  }

  @Test public void testContainsFieldName() {
    Assert.assertTrue(table.containsFieldName("foo"));
    Assert.assertTrue(table.containsFieldName("bar"));
    Assert.assertFalse(table.containsFieldName("baz"));
  }

  @Test public void testContainsMethodName() {
    Assert.assertSame(rpc1, table.lookupMethodSimpleName("rpc1").get(0));
    Assert.assertNull(table.lookupMethodSimpleName("rpc2"));
  }

  @Test public void testResolveType2() {
    Mockito.when(message1.getFullName()).thenReturn(m1Name);
    Mockito.when(message2.getFullName()).thenReturn(m2Name);
    Mockito.when(message3.getFullName()).thenReturn(m3Name);
    Mockito.when(message4.getFullName()).thenReturn(m4Name);

    Assert.assertSame(TypeRef.of(Type.TYPE_INT32), table.resolveType("a.b",  "int32"));
    Assert.assertSame(m1, table.resolveType2("a.b",  "m"));
    Assert.assertSame(m2, table.resolveType2("a.b",  "m.m"));
    Assert.assertSame(m2, table.resolveType2("a.b.m",  "m"));
    Assert.assertSame(m1, table.resolveType2("a.b.m",  ".a.b.m"));
    Assert.assertSame(m4, table.resolveType2("a.b.a",  "a.n"));
    Assert.assertNull(table.resolveType2("a.b.a",  "a.m")); // Note: different from resolveType()
  }
}
