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

package com.google.api.tools.framework.aspects.documentation.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.tools.framework.model.DiagReporter;
import com.google.api.tools.framework.model.DiagReporter.ResolvedLocation;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.testing.TestDiagReporter;
import com.google.common.collect.Lists;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Test for {@link SourceParser}
 */
@RunWith(JUnit4.class)

public class SourceParserTest {

  private SourceParser parser;
  private final DiagReporter diag = TestDiagReporter.createForTest();

  @Test
  public void parse_html_pre() {
    String source = "A B C\n"
                  + "  <pre> 1 2  3\n"
                  + " 4  5 6\n"
                  + "</pre>\nNOTCODE";
    parser = new SourceParser(source, ResolvedLocation.create(SimpleLocation.UNKNOWN), diag, "");
    SourceRoot root = parser.parse();
    List<ContentElement> tlcontents = Lists.newArrayList();
    for (ContentElement elt : root.getTopLevelContents()) {
      tlcontents.add(elt);
    }

    ContentElement e0 = tlcontents.get(0);
    ContentElement e1 = tlcontents.get(1);
    ContentElement e2 = tlcontents.get(2);

    assertTrue(e0 instanceof Text);
    assertTrue(e1 instanceof CodeBlock);
    assertTrue(e2 instanceof Text);
    assertEquals("A B C\n  ", e0.getContent());
    assertEquals("<pre> 1 2  3\n 4  5 6\n</pre>", e1.getContent());
    assertEquals("\nNOTCODE", e2.getContent());

    assertEquals(3, tlcontents.size());
  }

  // If the <pre> block is surrounded by two or more newlines, we strip one of the newlines.
  // This compensates for G3doc adding an additional newline later.
  @Test
  public void parse_html_pre_extra_space() {
    String source = "A B C\n\n"
                  + "  <pre> 1 2  3\n"
                  + " 4  5 6\n"
                  + "</pre>\n\nNOTCODE";
    parser = new SourceParser(source, ResolvedLocation.create(SimpleLocation.UNKNOWN), diag, "");
    SourceRoot root = parser.parse();
    List<ContentElement> tlcontents = Lists.newArrayList();
    for (ContentElement elt : root.getTopLevelContents()) {
      tlcontents.add(elt);
    }

    ContentElement e0 = tlcontents.get(0);
    ContentElement e1 = tlcontents.get(1);
    ContentElement e2 = tlcontents.get(2);

    assertTrue(e0 instanceof Text);
    assertTrue(e1 instanceof CodeBlock);
    assertTrue(e2 instanceof Text);
    assertEquals("A B C\n", e0.getContent());
    assertEquals("<pre> 1 2  3\n 4  5 6\n</pre>", e1.getContent());
    assertEquals("\nNOTCODE", e2.getContent());

    assertEquals(3, tlcontents.size());
  }

  @Test
  public void parse_html_code_block() {
    String source = "<pre><code>{"
                  + "  \"serviceName\": \"library.googleapis.com\","
                  + "  \"operation\": {"
                  + "    \"operationId\": \"1302984f-f9b5-4274-b4f9-079a3731e6e5\","
                  + "    \"operationName\": \"library.googleapis.com.v1.QuotaCheck\","
                  + "    \"consumerId\": \"project:proven-catcher-789\","
                  + "    \"startTime\": \"2015-05-01T15:00:05Z\","
                  + "    \"quotaProperties\": {"
                  + "      \"quotaMode\": \"NORMAL\","
                  + "        \"limitByIds\": { \"USER\": \"some_user\" }"
                  + "    },"
                  + "    \"metricValueSets\": [ {"
                  + "      \"metricName\": \"library.googleapis.com/quota_used\","
                  + "      \"metricValues\": [ {"
                  + "        \"int64Value\": \"1\","
                  + "        \"labels\": { \"/quota_group_name\": \"AllGroup\" }"
                  + "      } ]"
                  + "    } ]"
                  + "  }"
                  + "}</code></pre>";
    parser = new SourceParser(source, ResolvedLocation.create(SimpleLocation.UNKNOWN), diag, "");
    parser.parse();
  }

  @Test
  public void escape_instruction() {
    String source = "a (== do_something arg ==) b\n"
                  + "c \\(== don't do anything \\==) d\n"
                  + "e \\(== f (== command arg ==) \\==) g\n"
                  + "h (== do_something_else arg1 \\==) \\(== \\==) arg5 ==) i";
    parser = new SourceParser(source, ResolvedLocation.create(SimpleLocation.UNKNOWN), diag, "");
    SourceRoot root = parser.parse();
    List<ContentElement> tlcontents = Lists.newArrayList();
    for (ContentElement elt : root.getTopLevelContents()) {
      tlcontents.add(elt);
    }

    ContentElement e0 = tlcontents.get(0);
    ContentElement e1 = tlcontents.get(1);
    ContentElement e2 = tlcontents.get(2);
    ContentElement e3 = tlcontents.get(3);
    ContentElement e4 = tlcontents.get(4);
    ContentElement e5 = tlcontents.get(5);
    ContentElement e6 = tlcontents.get(6);

    assertTrue(e0 instanceof Text);
    assertTrue(e1 instanceof Instruction);
    assertTrue(e2 instanceof Text);
    assertTrue(e3 instanceof Instruction);
    assertTrue(e4 instanceof Text);
    assertTrue(e5 instanceof Instruction);
    assertTrue(e6 instanceof Text);

    assertEquals("a ", e0.getContent());
    assertEquals("do_something", ((Instruction) e1).getCode());
    assertEquals("arg", ((Instruction) e1).getArg());
    assertEquals(" b\nc (== don't do anything ==) d\ne (== f ", e2.getContent());
    assertEquals("command", ((Instruction) e3).getCode());
    assertEquals("arg", ((Instruction) e3).getArg());
    assertEquals(" ==) g\nh ", e4.getContent());
    assertEquals("do_something_else", ((Instruction) e5).getCode());
    assertEquals("arg1 ==) (== ==) arg5", ((Instruction) e5).getArg());
    assertEquals(" i", e6.getContent());

    assertEquals(7, tlcontents.size());
  }

  @Test
  public void instruction_scoping() {
    String source = "a (== do_something arg ==) ==) b\n"
        + "c (== do_something (== (== arg ==) d";
    parser = new SourceParser(source, ResolvedLocation.create(SimpleLocation.UNKNOWN), diag, "");
    SourceRoot root = parser.parse();
    List<ContentElement> tlcontents = Lists.newArrayList();
    for (ContentElement elt : root.getTopLevelContents()) {
      tlcontents.add(elt);
    }
    ContentElement e0 = tlcontents.get(0);
    ContentElement e1 = tlcontents.get(1);
    ContentElement e2 = tlcontents.get(2);
    ContentElement e3 = tlcontents.get(3);
    ContentElement e4 = tlcontents.get(4);

    assertTrue(e0 instanceof Text);
    assertTrue(e1 instanceof Instruction);
    assertTrue(e2 instanceof Text);
    assertTrue(e3 instanceof Instruction);
    assertTrue(e4 instanceof Text);

    assertEquals("a ", e0.getContent());
    assertEquals("do_something", ((Instruction) e1).getCode());
    assertEquals("arg", ((Instruction) e1).getArg());
    assertEquals(" ==) b\nc ", e2.getContent());
    assertEquals("do_something", ((Instruction) e3).getCode());
    assertEquals("(== (== arg", ((Instruction) e3).getArg());
    assertEquals(" d", e4.getContent());

    assertEquals(5, tlcontents.size());
  }
}
