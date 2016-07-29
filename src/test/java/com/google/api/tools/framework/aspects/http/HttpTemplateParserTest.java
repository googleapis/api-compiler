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

package com.google.api.tools.framework.aspects.http;

import com.google.api.tools.framework.aspects.http.model.HttpAttribute.FieldSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.LiteralSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.WildcardSegment;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.SimpleDiagCollector;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link HttpTemplateParser}.
 */
@RunWith(JUnit4.class)
public class HttpTemplateParserTest {
  private static final Location TEST_LOCATION = new SimpleLocation("test");
  private static final PathSegment BUCKETS = new LiteralSegment("buckets");
  private static final PathSegment BUCKET_ID = new FieldSegment("bucket_id",
      ImmutableList.<PathSegment>of());
  private static final PathSegment BUCKET_NAME_ID = new FieldSegment("bucket_name.bucket_id",
      ImmutableList.<PathSegment>of());
  private static final PathSegment OBJECTS = new LiteralSegment("objects");
  private static final PathSegment DOTOBJECTS = new LiteralSegment("objects", true);
  private static final PathSegment CUSTOM_VERB = new LiteralSegment("custom", true);
  private static final PathSegment BOUNDED_WILDCARD = new WildcardSegment(false);
  private static final PathSegment UNBOUNDED_WILDCARD = new WildcardSegment(true);
  private static final int CONFIG_VERSION_0 = 0;
  private static final int CONFIG_VERSION_1 = 1;

  @Test public void testParser() {
    assertParsingFailure("", CONFIG_VERSION_1, "unexpected end of input ''.",
        "effective path must start with leading '/'.");
    assertParsingFailure("/", CONFIG_VERSION_1, "unexpected end of input '/'.");
    assertParsingFailure("buckets", CONFIG_VERSION_1,
        "effective path must start with leading '/'.");
    assertParsingFailure("buckets/{name=/objects/*}",
        CONFIG_VERSION_1, "leading '/' only allowed for first segment of path.",
        "effective path must start with leading '/'.");
    assertParsingFailure("/buckets/{bucket_name.bucket_id}/:objects",
        CONFIG_VERSION_1, "invalid token '/:' before the custom verb.");
    assertParsingSuccess("/*", CONFIG_VERSION_1, BOUNDED_WILDCARD);
    assertParsingSuccess("/**", CONFIG_VERSION_1, UNBOUNDED_WILDCARD);
    assertParsingSuccess("/buckets", CONFIG_VERSION_1, BUCKETS);
    assertParsingSuccess("/{name=buckets}", CONFIG_VERSION_1,
        new FieldSegment("name", ImmutableList.of(BUCKETS)));
    assertParsingSuccess("/buckets/{bucket_id}", CONFIG_VERSION_1,
        BUCKETS, BUCKET_ID);
    assertParsingSuccess("/buckets/{bucket_name.bucket_id}/objects",
        CONFIG_VERSION_1, BUCKETS, BUCKET_NAME_ID, OBJECTS);
    assertParsingSuccess("/buckets/{bucket_name.bucket_id}:objects",
        CONFIG_VERSION_0, BUCKETS, BUCKET_NAME_ID, DOTOBJECTS);
    assertParsingSuccess("/buckets/{bucket_name.bucket_id}:custom",
        CONFIG_VERSION_1, BUCKETS, BUCKET_NAME_ID, CUSTOM_VERB);
    assertParsingSuccess("/buckets/{bucket_name.bucket_id}/objects/{object_id=**}",
        CONFIG_VERSION_1, BUCKETS, BUCKET_NAME_ID, OBJECTS,
        new FieldSegment("object_id", ImmutableList.of(UNBOUNDED_WILDCARD)));
    assertParsingSuccess("/{name=buckets/*/objects/**}",
        CONFIG_VERSION_1, new FieldSegment("name",
            ImmutableList.of(BUCKETS, BOUNDED_WILDCARD, OBJECTS, UNBOUNDED_WILDCARD)));
    assertParsingSuccess("/buckets/create", CONFIG_VERSION_1,
        BUCKETS, new LiteralSegment("create"));
  }

  private void assertParsingSuccess(String path, int configVersion, PathSegment... expected) {
    SimpleDiagCollector diag = new SimpleDiagCollector();
    List<PathSegment> segments = new HttpTemplateParser(diag,
        TEST_LOCATION, path, configVersion).parse();
    Assert.assertEquals("Path template " + path + " has unexpected errors\n" + diag,
        0, diag.getErrorCount());
    Assert.assertEquals(PathSegment.toSyntax(Arrays.asList(expected)),
        PathSegment.toSyntax(segments));
  }

  private void assertParsingFailure(final String path, int configVersion,
      String... expectedErrors) {
    SimpleDiagCollector diag = new SimpleDiagCollector();
    new HttpTemplateParser(diag, TEST_LOCATION, path, configVersion).parse();

    List<Diag> expectedDiagErros = FluentIterable.from(Arrays.asList(expectedErrors))
        .transform(new Function<String, Diag>() {
          @Override public Diag apply(String input) {
            return Diag.error(TEST_LOCATION, "In path template '" + path + "': " + input);
          }}).toList();

    List<Diag> actualErrors = diag.getErrors();
    Assert.assertEquals(expectedDiagErros, actualErrors);
  }
}
