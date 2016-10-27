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

package com.google.api.tools.framework.snippet;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.tools.framework.model.testing.BaselineTestCase;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.api.tools.framework.snippet.SnippetSet.EvalException;
import com.google.api.tools.framework.snippet.SnippetSet.InputSupplier;
import com.google.api.tools.framework.snippet.SnippetSet.ParseException;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link SnippetSet} and related functionality.
 */
@RunWith(JUnit4.class)

public class SnippetTest extends BaselineTestCase {

  private SnippetSet snippets;

  private SnippetSet createFromFile(String fileName) throws IOException  {
    TestDataLocator locator = TestDataLocator.create(getClass());
    try {
      return snippets = SnippetSet.parse(locator.getTestDataAsFile(fileName).toFile());
    } catch (ParseException e) {
      testOutput().println("errors!!");
      testOutput().println(e.getMessage());
      return snippets = null;
    }
  }

  private SnippetSet createFromResource(String fileName) {
    try {
      final String root = getClass().getPackage().getName().replace('.', '/') + "/testdata";
      return snippets = SnippetSet.parse(SnippetSet.resourceInputSupplier(root), fileName);
    } catch (ParseException e) {
      testOutput().println("errors!!");
      testOutput().println(e.getMessage());
      return snippets = null;
    }
  }

  private SnippetSet create(String... lines) {
    // Split up the lines into virtual input sources.
    final Map<String, List<String>> input = Maps.newLinkedHashMap();
    List<String> current = Lists.newArrayList();
    input.put("testInput", current);
    for (String line : lines) {
      if (line.startsWith("#@input ")) {
        String inputName = line.substring("#@input ".length()).trim();
        input.put(inputName, current = Lists.newArrayList());
      } else {
        current.add(line);
      }
    }
    InputSupplier supplier = new InputSupplier() {
      @Override public Iterable<String> readInput(String snippetSetName) throws IOException {
        return input.get(snippetSetName);
      }
    };

    try {
      return snippets = SnippetSet.parse(supplier, "testInput");
    } catch (ParseException e) {
      testOutput().println("errors!!");
      testOutput().println(e.getMessage());
      return snippets = null;
    }
  }

  private void eval(String snippet, Object... args) {
    eval(80, snippet, args);
  }

  private void eval(int width, String snippet, Object... args) {
    if (snippets == null) {
      // Error reported
      return;
    }
    testOutput().printf("---- eval %s(%s) ==>%n", snippet,
        Joiner.on(",").join(args));
    try {
      testOutput().println(snippets.eval(snippet, args).prettyPrint(width));
    } catch (EvalException e) {
      testOutput().println("Unexpected error. Message: " + e.getMessage());
    }
    testOutput().println("---- end");
  }

  private void evalWithError(String snippet, Object... args) {
    if (snippets == null) {
      // Error reported
      return;
    }
    testOutput().printf("---- eval %s(%s) ==>%n", snippet,
        Joiner.on(",").join(args));
    try {
      testOutput().println(snippets.eval(snippet, args).prettyPrint());
      testOutput().println("Unexpected success!");
    } catch (EvalException e) {
      testOutput().println("Found expected error: " + e.getMessage());
    }
    testOutput().println("---- end");
  }

  @Test public void simple() {
    create(
        "# just a simple snippet",
        "@snippet foo(x,y)",
        "  # say something",
        "  {@x}, {@y}!",
        "@end");
    eval("foo", "Hello", "World");
  }

  @Test public void literal() {
    create(
        "@snippet foo()",
        "  {@\"\\\"\"}", // {@"\""}
        "@end",
        "@snippet bar()",
        "  {@\"\\\"foo\"}", // {@"\"foo"}
        "@end"
        );
    eval("foo");
    eval("bar");
  }

  @Test public void vertical() {
    create(
        "@snippet foo(x,y)",
        "  {@x},",
        "    {@y},",
        "  oh {@y}",
        "@end"
        );
    eval("foo", "Hello", "World");
  }

  @Test public void autoWithNestNoBreak() {
    create(
        "@snippet foo(x,y) auto 4",
        "  {@x}, amazing",
        "  {@y}!",
        "@end"
        );
    eval("foo", "Hello", "World");
  }

  @Test public void autoWithNestBreak() {
    create(
        "@snippet foo(x,y) auto 4",
        "  {@x}, amazing",
        "  {@y}!",
        "@end"
        );
    eval(6, "foo", "Hello", "World");
  }

  @Test public void conditional() {
    create(
        "@snippet foo(x,y)",
        "  @if x",
        "    # this is first",
        "    first if",
        "  @end",
        "  @if x",
        "    @if y",
        "      # this is nested",
        "      nested if",
        "    @end",
        "  @end",
        "  @if x",
        "    if-else",
        "  @else",
        "      # on the other hand...",
        "    else-part",
        "  @end",
        "@end");
    eval("foo", true, true);
    eval("foo", true, false);
    eval("foo", false, true);
    eval("foo", false, false);
  }

  @Test public void conditionalWaysOfTruth() {
    create(
        "@snippet foo(x)",
        "  @if x",
        "    {@x} is true",
        "  @else",
        "    {@x} is false",
        "  @end",
        "@end"
        );
    eval("foo", Doc.EMPTY);
    eval("foo", Doc.text("a"));
    eval("foo", "");
    eval("foo", "a");
    eval("foo", true);
    eval("foo", false);
    eval("foo", 1);
    eval("foo", 0);
    eval("foo", 1.0);
    eval("foo", 0.0);
    eval("foo", ImmutableList.<Object>of());
    eval("foo", ImmutableList.<Object>of(1));
  }

  @Test public void relations() {
    create(
        "@snippet foo(x,y)",
        "  {@x == y}",
        "  @if x == y",
        "    x == y",
        "  @end",
        "  @if x != y",
        "    x != y",
        "  @end",
        "  @if x <= y",
        "    x <= y",
        "  @end",
        "  @if x < y",
        "    x < y",
        "  @end",
        "  @if x > y",
        "    x > y",
        "  @end",
        "  @if x >= y",
        "    x >= y",
        "  @end",
        "@end"
        );
    eval("foo", "same", "same");
    eval("foo", "less", "less1");
    eval("foo", "more1", "more");
    eval("foo", 1, 10);
    eval("foo", 1, -10);
  }

  @Test public void join() {
    create(
        "@snippet foo(x)",
        "  join default:",
        "  @join e : x",
        "    # joining some stuff",
        "    {@e}",
        "  @end",
        "  join soft break:",
        "  @join e : x auto on SOFT_BREAK",
        "    # joining some stuff softly",
        "    {@e}",
        "  @end",
        "  join empty:",
        "  @join e : x auto on EMPTY",
        "    # joining not at all",
        "    {@e}",
        "  @end",
        "  join , then soft break:",
        "  @join e : x auto on \",\".add(SOFT_BREAK)",
        "    {@e}",
        "  @end",
        "@end");
    eval("foo", ImmutableList.of("1"));
    eval("foo", ImmutableList.of());
    eval("foo", ImmutableList.of("1", "2", "3"));
  }

  @Test public void joinIf() {
    create(
        "@snippet foo(x)",
        "  @join e : x",
        "    @if e == 2",
        "      {@e}",
        "    @end",
        "  @end",
        "@end",
        "@snippet foo2(x)",
        "  @join e : x if e == 2",
        "    {@e}",
        "  @end",
        "@end",
        "@snippet bar(x)",
        "  @join e : x on EMPTY",
        "    @if e == 2",
        "      {@e}{@BREAK}",
        "    @end",
        "  @end",
        "@end"
        );
    eval("foo", ImmutableList.of("1", "2", "3", "2"));
    eval("bar", ImmutableList.of("1", "2", "3", "2"));
    eval("foo2", ImmutableList.of("1", "2", "3", "2"));
  }

  @Test public void fluentIterable() {
    create(
        "@snippet foo(x, y)",
        "  @join e : x.append(y) on \",\".add(BREAK)",
        "    {@e}",
        "  @end",
        "@end"
        );
    eval("foo", ImmutableList.of(1), ImmutableList.of(2, 3));
  }

  @Test public void let() {
    create(
        "@snippet foo(x,y)",
        "  @let x = bar(x,y)",
        "    # who let the dogs out?",
        "    {@x}",
        "  @end",
        "",
        "  {@x}",
        "@end",
        "@snippet bar(x,y)",
        "  {@x} + {@y}",
        "@end");
    eval("foo", 1, 2);
  }

  @Test public void multiLet() {
    create(
        "@snippet foo(x,y)",
        "  @let x = bar(x,y), z = bar(x,y)",
        "    {@x}",
        "    {@z}",
        "  @end",
        "@end",
        "@snippet bar(x,y)",
        "  {@x} + {@y}",
        "@end"
        );
    eval("foo", 1, 2);
  }

  @Test public void multiLetMultiLine() {
    create(
        "@snippet foo(x,y)",
        "  @let x = bar(x,y), \\",
        "       z = bar(x,y)",
        "    {@x}",
        "    {@z}",
        "  @end",
        "@end",
        "@snippet bar(x,y)",
        "  {@x} + {@y}",
        "@end"
        );
    eval("foo", 1, 2);
  }

  @Test public void cases() {
    create(
        "@snippet foo(x)",
        "  @switch x",
        "  @case \"a\"",
        "    A",
        "  @case \"b\"",
        "    B",
        "  @end",
        "@end");
    eval("foo", "a");
    eval("foo", "b");
    evalWithError("foo", "c");
  }

  @Test public void casesWithDefault() {
    create(
        "@snippet foo(x)",
        "  # whatever the case may be",
        "  @switch x",
        "  @case \"a\"",
        "    # just in case",
        "    A",
        "  @case \"b\"",
        "    B",
        "  @default",
        "    # The two sweetest words in the English language. -Homer Simpson",
        "    C",
        "  @end",
        "@end");
    eval("foo", "a");
    eval("foo", "b");
    eval("foo", "c");
  }

  @Test public void syntaxErrors() {
    create(
        "@snippet foo(x,y) aut",
        "  {@foo.}",
        "  {@foo..x}",
        "  {@.foo(x)}",
        "  {@x.foo x}",
        "  {@x.foo(x y))}",
        "  {@x %x}",
        "  {@x.x(a,b,)}",
        "@end",
        "@x",
        "garbage",
        "# no garbage, comment",
        "@snippet bar(x)",
        "@snippet xbar(x)",
        "@end",
        "@snippet xxbar(x)"
        );
    Assert.assertNull(snippets);
  }

  @Test public void call() {
    create(
        "@snippet foo(x)",
        "  {@bar(x, x)}",
        "@end",
        "@snippet bar(x, y) auto",
        "  {@x} == {@y}",
        "@end"
        );
    eval("foo", "p");
  }

  @Test public void callLiteral() {
    create(
        "@snippet foo()",
        "  {@bar(\"a\", \"b\")}",
        "@end",
        "@snippet bar(x, y) auto",
        "  {@x} == {@y}",
        "@end"
        );
    eval("foo");
  }

  @Test public void dataAccess() {
    create(
        "@snippet foo(x)",
        "  {@x.stringField}",
        "  {@x.intField}",
        "  {@x.getIntField()}",
        "  {@x.getStringField()}",
        "  {@x.getStringField}",
        "  {@x.getIntFieldAndAdd(x.intField)}",
        "@end"
        );
    eval("foo", new TestData());
  }

  @Test public void dataAccessError() {
    create(
        "@snippet unknown(x)",
        "  {@x.x}",
        "@end",
        "@snippet access(x)",
        "  {@x.privateIntField}",
        "@end",
        "@snippet type(x)",
        "  {@x.getIntFieldAndAdd(x.stringField)}",
        "@end"
        );
    evalWithError("unknown", new TestData());
    evalWithError("access", new TestData());
    evalWithError("type", new TestData());
  }

  @Test public void extending() {
    create(
        "@extends \"other\"",
        "@snippet caller(x)",
        "  {@callee(x)}",
        "@end",
        "#@input other",
        "@snippet callee(x)",
        "  {@x}",
        "@end"
        );
    eval("caller", "Hello");
  }

  @Test public void extendingNotFound() {
    create(
        "@extends \"other\"",
        "@snippet caller(x)",
        "  {@callee(x)}",
        "@end"
        );
    Assert.assertNull(snippets);
  }

  @Test public void privates() {
    create(
        "@snippet caller(x)",
        "  {@callee(x)}",
        "@end",
        "@private callee(x)",
        "  # keep it secret, keep it safe",
        "  {@x}",
        "@end");
    eval("caller", "Hello");
  }

  @Test public void privatesNotFound() {
    create(
        "@extends \"other\"",
        "@snippet caller(x)",
        "  {@callee(x)}",
        "@end",
        "#@input other",
        "@private callee(x)",
        "  {@x}",
        "@end"
        );
    evalWithError("caller", "Hello");
  }

  @Test public void overriding() {
    create(
        "@extends \"other\"",
        "@override foo(x)",
        "  new {@x}",
        "@end",
        "#@input other",
        "@snippet foo(x)",
        "  original {@x}",
        "@end"
        );
    eval("foo", "hello");
  }

  @Test public void missingOverride() {
    create(
        "@extends \"other\"",
        "@snippet foo(x)",
        "  new {@x}",
        "@end",
        "#@input other",
        "@snippet foo(x)",
        "  original {@x}",
        "@end"
        );
    Assert.assertNull(snippets);
  }

  @Test public void illegalIndirectOverride() {
    create(
        "@extends \"A\"",
        "@extends \"B\"",
        "#@input A",
        "@snippet foo(x)",
        "  original {@x}",
        "@end",
        "#@input B",
        "@override foo(x)",
        "  original {@x}",
        "@end"
        );
    Assert.assertNull(snippets);
  }

  @Test public void abstractSnippet() {
    create(
        "@extends \"other\"",
        "@override foo(x)",
        "  new {@x}",
        "@end",
        "#@input other",
        "@abstract foo(x)"
        );
    eval("foo", "hello");
  }

  @Test public void abstractEval() {
    create(
        "@abstract foo(x)"
        );
    evalWithError("foo", "hello");
  }

  @Test public void file() throws IOException {
    createFromFile("file.snip");
    eval("foo", "Hello", "World");
    eval("unary", "NOT", 43);
  }

  @Test public void resource() {
    createFromResource("resource.snip");
    eval("foo", "Hello", "World");
    eval("unary", "NOT", 43);
  }

  @Test public void binding() {
    create(
        "@snippet foo(x,y)",
        "  {@x}, {@y}!",
        "@end",
        "@snippet foo(x)",
        "  {@x}!",
        "@end"
        );
    if (snippets != null) {
      Interface x = snippets.bind(Interface.class);
      testOutput().println(x.foo(Doc.text("foo1"), Doc.text("bar1")).prettyPrint());
      testOutput().println(x.foo(Doc.text("foo2")).prettyPrint());
    }
  }

  @Test public void bindingWithContext() {
    create(
        "@snippet foo(x)",
        "  {@x} and {@y}",
        "@end",
        "@snippet foo(x, y)",
        "  {@x} and {@y}",
        "@end"
        );
    if (snippets != null) {
      Interface x = snippets.bind(Interface.class,
          ImmutableMap.<String, Object>of("y", "from global"));
      testOutput().println(x.foo(Doc.text("foo1"), Doc.text("bar1")).prettyPrint());
      testOutput().println(x.foo(Doc.text("foo2")).prettyPrint());
    }
  }

  @Test public void bindingMissingMethod() {
    create(
        "@snippet foo(x,y)",
        "  {@x}, {@y}!",
        "@end"
        );
    if (snippets != null) {
      try {
        snippets.bind(Interface.class);
        Assert.fail();
      } catch (IllegalArgumentException e) {
        testOutput().println(e.getMessage());
      }
    }
  }

  private static Doc wrapInHtmlBody(Doc doc) {
    return Doc.blockBuilder(Doc.text("<html>"))
        .add(Doc.blockBuilder(Doc.text("<body>"))
            .add(doc)
            .build(Doc.text("</body>")))
        .build(Doc.text("</html>"));
  }

  @Test public void testIndentAt() {
    Doc inner = Doc.blockBuilder(Doc.LBRACE)
        .add(Doc.text("name: string,"))
        .add(Doc.text("size: integer"))
        .build(Doc.RBRACE);
    Doc outer = Doc.blockBuilder(Doc.LBRACE)
        .add(Doc.text("one: ").add(inner).add(Doc.COMMA))
        .add(Doc.text("two: ").add(inner))
        .build(Doc.RBRACE);

    Doc pre = Doc.text("<pre>").add(outer).vgroup().add(Doc.text("</pre>")).vgroup();
    Doc html = wrapInHtmlBody(pre);

    testOutput().println("---- without indentAt");
    testOutput().println(html.prettyPrint());

    pre = Doc.text("<pre>").add(outer.indentAt(0)).vgroup().add(Doc.text("</pre>")).vgroup();
    html = wrapInHtmlBody(pre);

    testOutput().println("---- indentAt (0)");
    testOutput().println(html.prettyPrint());

    pre = Doc.text("<pre>").add(outer.indentAt(3)).vgroup().add(Doc.text("</pre>")).vgroup();
    html = wrapInHtmlBody(pre);

    testOutput().println("---- indentAt (3)");
    testOutput().println(html.prettyPrint());
    testOutput().println("---- end");
  }

  private interface Interface {
    Doc foo(Doc x, Doc y);
    Doc foo(Doc x);
  }

  @SuppressWarnings("unused")
  private static class TestData {
    public String stringField = "Hello";
    public int intField = 2;
    private final int privateIntField = 2;

    public int getIntField() {
      return intField;
    }

    public String getStringField() {
      return stringField;
    }

    public int getIntFieldAndAdd(int value) {
      return intField + value;
    }

    @Override
    public String toString() {
      return "TestData";
    }
  }

  @Test public void testEscapes() {
    create(
        "@snippet test()",
        "  escaped 'at' sign: @@",
        "  escape backslash : @\\",
        "  escaped hash mark: @#",
        "@end"
        );
    eval("test");
  }

  @Test public void higherOrder() {
    create(
        "@snippet test()",
        "  {@template(foo,\"a\",\"b\")}",
        "@end",
        "@snippet template(f,a,b)",
        "  {@f(a)}",
        "  {@f(b)}",
        "@end",
        "@snippet foo(x)",
        "  called foo({@x})",
        "@end"
    );
    eval("test");
  }
}
