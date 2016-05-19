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

import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.common.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Test for {@link SourceParser}
 */
@RunWith(JUnit4.class)

public class SourceParserTest {

  private SourceParser parser;
  private final SimpleDiagCollector diag = new SimpleDiagCollector();

  @Test
  public void parse_html_pre() {
    String source = "A B C\n"
                  + "  <pre> 1 2  3\n"
                  + " 4  5 6\n"
                  + "</pre>\nNOTCODE";
    parser = new SourceParser(source, null, diag, "");
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
    assertTrue(e0.getContent().equals("A B C\n  "));
    assertTrue(e1.getContent().equals("<pre> 1 2  3\n 4  5 6\n</pre>"));
    assertTrue(e2.getContent().equals("\nNOTCODE"));
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
    parser = new SourceParser(source, null, diag, "");
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
    assertTrue(e0.getContent().equals("A B C\n"));
    assertTrue(e1.getContent().equals("<pre> 1 2  3\n 4  5 6\n</pre>"));
    assertTrue(e2.getContent().equals("\nNOTCODE"));
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
    parser = new SourceParser(source, null, diag, "");
    parser.parse();
  }

}
