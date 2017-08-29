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

package com.google.api.tools.framework.processors.merger;

import com.google.api.Service;
import com.google.api.tools.framework.aspects.documentation.model.ElementDocumentationAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.versioning.model.VersionAttribute;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Diag.Kind;
import com.google.api.tools.framework.model.FieldSelector;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.TypeRef;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.api.tools.framework.model.testing.StageValidator;
import com.google.api.tools.framework.model.testing.TestConfig;
import com.google.api.tools.framework.model.testing.TestDataLocator;
import com.google.api.tools.framework.setup.StandardSetup;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.inject.Key;
import com.google.protobuf.TextFormat;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Merger}.
 */
@RunWith(JUnit4.class)

public class MergerTest {

  // TODO(user): http tests should be moved out into individual tests for HTTP config.
  // This class should only test the generic merger infrastructure.

  @Rule public TestName testName = new TestName();
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  TestConfig testConfig;
  Model model;

  private void createApi(String... fileNames) throws Exception {
    TestDataLocator locator = TestDataLocator.create(MergerTest.class);
    testConfig = new TestConfig(locator, tempDir.getRoot().getPath(),
        ImmutableList.copyOf(fileNames));
    model = testConfig.createModel(ImmutableList.<String>of());
    StandardSetup.registerStandardProcessors(model);
  }

  private void createApiWithHttpConfig(String methodKind, String path, String body)
      throws Exception {
    createApiWithHttpConfig(methodKind, path, body, false);
  }

  private void createApiWithHttpConfig(String methodKind, String path, String body,
      boolean isCustom)
      throws Exception {
    String source =
        "syntax = \"proto2\"; "
            + "package protiary.test;"
            + "message M {"
            + "  optional string a = 1;"
            + "  required N b = 2;"
            + "  optional string c = 3;"
            + "  repeated string d = 4;"
            + "}"
            + "message N {"
            + "  optional string a = 1;"
            + "}"
            + "service S {"
            + "  rpc Rpc(M) returns (N);"
            + "}";

    Service.Builder configBuilder = Service.newBuilder();
    TextFormat.merge(
        String.format(
            "name: \"blob.googleapis.com\" "
            + "config_version {"
            +  "value: 3"
            + "}"
            + "http { rules { selector: \"protiary.test.S.Rpc\" "
            + (isCustom ? " custom{ kind: \"%s\" path: \"%s\" }" : " %s: \"%s\" ")
            + " %s  } }",
            methodKind, path, body == null ? "" : "body: \"" + body + "\""),
        configBuilder);

    TestDataLocator locator = TestDataLocator.create(MergerTest.class);
    locator.injectVirtualTestData("source.proto", source);
    testConfig = new TestConfig(locator, tempDir.getRoot().getPath(),
        ImmutableList.of("source.proto"));
    model = testConfig.createModel(ImmutableList.<String>of());
    StandardSetup.registerStandardProcessors(model);
    model.setServiceConfig(configBuilder.build());
    StandardSetup.registerStandardConfigAspects(model);
  }

  @Test public void mergesWithoutConfig() throws Exception {
    createApi("service.proto");
    model.establishStage(Merged.KEY);
    checkNoErrors();
  }

  @Test public void mergesWithConfig() throws Exception {
    createApi("service.proto");
    model.setServiceConfig(testConfig.getApiProtoConfig("service.config"));
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);
    checkNoErrors();

    Interface iface = getInterface("protiary.test.Storage");
    Method getBucket = getMethod("protiary.test.Storage", "GetBucket");
    Assert.assertEquals("v1", iface.getAttribute(VersionAttribute.KEY).majorVersion());
    Assert.assertEquals("Get Bucket. (-- For internal tests --)",
        getBucket.getAttribute(ElementDocumentationAttribute.KEY).documentation());
  }

  @Test public void mergesWithYamlConfig() throws Exception {
    createApi("service.proto", "included_type.proto");
    model.setConfigSources(
        testConfig.getApiYamlConfigSources(
            model.getDiagReporter().getDiagCollector(), ImmutableList.of("service.yaml")));
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);
    checkNoErrors();

    Method getBucket = getMethod("protiary.test.Storage", "GetBucket");
    Assert.assertEquals("Override GetBucket documentation.",
        getBucket.getAttribute(ElementDocumentationAttribute.KEY).documentation());
    assertTypeInclusions(model, Lists.newArrayList("protiary.test.inclusion.Type1",
        "protiary.test.inclusion.Type2", "protiary.test.inclusion.Enum1"),
        Lists.newArrayList("protiary.test.inclusion.Type3"));
  }

  @Test public void mergesWithConfigErrors() throws Exception {
    createApi("service.proto");
    model.setServiceConfig(testConfig.getApiProtoConfig("service_errors.config"));
    model.establishStage(Merged.KEY);
    assertError("protiary.testx.Storage");
  }

  @Test public void mergesWithTypeMismatch() throws Exception {
    createApi("service_type_mismatch.proto");
    model.setConfigSources(
        testConfig.getApiYamlConfigSources(
            model.getDiagReporter().getDiagCollector(),
            ImmutableList.of("service_type_mismatch.yaml")));
    model.establishStage(Merged.KEY);
    assertError("protiary.test.ExtraEnum");
    assertError("protiary.test.Bucket");
  }

  @Test public void mergesWithApiVersion() throws Exception {
    createApi("service_with_version.proto");
    model.setConfigSources(
        testConfig.getApiYamlConfigSources(
            model.getDiagReporter().getDiagCollector(),
            ImmutableList.of("service_with_version.yaml")));
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);

    Interface iface = getInterface("protiary.test.v2.Storage");
    Assert.assertEquals("v2", iface.getAttribute(VersionAttribute.KEY).majorVersion());
  }

  @Test public void mergesWithBetaVersion() throws Exception {
    createApi("service_with_beta_version.proto");
    model.setConfigSources(
        testConfig.getApiYamlConfigSources(
            model.getDiagReporter().getDiagCollector(),
            ImmutableList.of("service_with_beta_version.yaml")));
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);

    Interface iface = getInterface("protiary.test.v2beta1.Storage");
    Assert.assertEquals("v2beta1", iface.getAttribute(VersionAttribute.KEY).majorVersion());
  }

  @Test public void httpGet() throws Exception {
    createApiWithHttpConfig("get", "/some/{a}", null);
    model.establishStage(Merged.KEY);
    checkNoErrors();
    assertMethodConfig("protiary.test.S", "Rpc", MethodKind.GET,
        makeSet("a"), makeSet("b", "c", "d"), makeSet());
  }

  @Test public void httpGetNestedPath() throws Exception {
    createApiWithHttpConfig("get", "{a=/some/*}", null);
    model.establishStage(Merged.KEY);
    checkNoErrors();
    assertMethodConfig("protiary.test.S", "Rpc", MethodKind.GET,
        makeSet("a"), makeSet("b", "c", "d"), makeSet());
  }

  @Test public void httpGetNoLeadingSlash() throws Exception {
    createApiWithHttpConfig("get", "{a=some/*}", null);
    model.establishStage(Merged.KEY);
    assertError("start with leading");
  }

  @Test
  public void httpBodyNotMessage() throws Exception {
    createApiWithHttpConfig("put", "{a=/some/*}", "a");
    model.establishStage(Merged.KEY);
    assertError("non-repeated message");
  }

  @Test public void httpBodyRepeated() throws Exception {
    createApiWithHttpConfig("put", "{a=/some/*}", "d");
    model.establishStage(Merged.KEY);
    assertError("non-repeated message");
  }

  @Test public void httpMessageInPath() throws Exception {
    createApiWithHttpConfig("get", "/some/{b}", null);
    model.establishStage(Merged.KEY);
    assertError("message field");
  }

  @Test public void httpRepeatedInPath() throws Exception {
    createApiWithHttpConfig("get", "/some/{d}", null);
    model.establishStage(Merged.KEY);
    assertError("repeated field");
  }

  @Test public void httpParsingError1() throws Exception {
    createApiWithHttpConfig("get", "{a=/some/*", null);
    model.establishStage(Merged.KEY);
    assertError("expected '}'");
  }

  @Test public void httpParsingError2() throws Exception {
    createApiWithHttpConfig("get", "/a=/some/*", null);
    model.establishStage(Merged.KEY);
    assertError("unrecognized input at '='");
  }

  @Test public void httpParsingError3() throws Exception {
    createApiWithHttpConfig("get", "/a/", null);
    model.establishStage(Merged.KEY);
    assertError("unexpected end of input");
  }

  @Test public void httpCustomMethod() throws Exception {
    createApiWithHttpConfig("customMethod", "/some/{a}", null, true /* custom */);
    model.establishStage(Merged.KEY);
    checkNoErrors();
    assertWarning("control environment");
  }

  @Test public void storageHttpConfig() throws Exception {
    createApi("service.proto", "included_type.proto");
    model.setConfigSources(
        testConfig.getApiYamlConfigSources(
            model.getDiagReporter().getDiagCollector(), ImmutableList.of("service.yaml")));
    StandardSetup.registerStandardConfigAspects(model);
    model.establishStage(Merged.KEY);
    checkNoErrors();

    // Validate http configs
    assertMethodConfig("protiary.test.Storage", "GetBucket", MethodKind.GET,
        makeSet("bucket_id"), makeSet(), makeSet());
    assertMethodConfig("protiary.test.Storage", "CreateObject", MethodKind.POST,
        makeSet("bucket_name.bucket_id"), makeSet("mode", "kind"), makeSet("object"));
    assertMethodConfig("protiary.test.Storage", "CustomCreate", MethodKind.POST,
        makeSet(), makeSet(), makeSet("bucket"));
    assertMethodConfig("protiary.test.Storage", "CustomObjectCreateAllParam", MethodKind.POST,
        makeSet("bucket_name.bucket_id"), makeSet("object", "mode", "kind"), makeSet());
  }

  private void assertError(final String phrase) {
    Assert.assertTrue(model.getDiagReporter().getDiagCollector().hasErrors());
    Assert.assertTrue(
        Iterators.any(
            model.getDiagReporter().getDiagCollector().getDiags().iterator(),
            new Predicate<Diag>() {
              @Override
              public boolean apply(Diag diag) {
                return diag.getKind() == Kind.ERROR && diag.toString().contains(phrase);
              }
            }));
  }

  private void assertWarning(final String phrase) {
    Assert.assertTrue(model.getDiagReporter().getDiagCollector().getDiags().size() > 0);
    Assert.assertTrue(
        Iterators.any(
            model.getDiagReporter().getDiagCollector().getDiags().iterator(),
            new Predicate<Diag>() {
              @Override
              public boolean apply(Diag diag) {
                return diag.getKind() != Kind.ERROR && diag.toString().contains(phrase);
              }
            }));
  }

  private void assertMethodConfig(String interfaceName, String methodName,
      MethodKind kind, ImmutableSet<String> pathSelectors, ImmutableSet<String> paramSelectors,
      ImmutableSet<String> bodySelectors) {
    Method method = getMethod(interfaceName, methodName);
    HttpAttribute binding = method.getAttribute(HttpAttribute.KEY);
    Assert.assertNotNull(binding);

    Assert.assertEquals(kind,  binding.getMethodKind());
    Assert.assertEquals(pathSelectors, selectors(binding.getPathSelectors()));
    Assert.assertEquals(paramSelectors, selectors(binding.getParamSelectors()));
    Assert.assertEquals(bodySelectors, selectors(binding.getBodySelectors()));
  }

  private void assertTypeInclusions(Model model, Iterable<String> includedTypeNames,
      Iterable<String> excludedTypeNames) {
    for (String typeName : includedTypeNames) {
      TypeRef type = model.getSymbolTable().lookupType(typeName);
      Assert.assertNotNull(type);
      Assert.assertTrue(type.isMessage() || type.isEnum());
      if (type.isMessage()) {
        Assert.assertTrue(type.getMessageType().isReachable());
      } else {
        Assert.assertTrue(type.getEnumType().isReachable());
      }
    }

    for (String typeName : excludedTypeNames) {
      TypeRef type = model.getSymbolTable().lookupType(typeName);
      Assert.assertNotNull(type);
      Assert.assertTrue(type.isMessage() || type.isEnum());
      if (type.isMessage()) {
        Assert.assertFalse(type.getMessageType().isReachable());
      } else {
        Assert.assertFalse(type.getEnumType().isReachable());
      }
    }
  }

  private Method getMethod(String interfaceName, String methodName) {
    Interface endpointInterface = getInterface(interfaceName);
    Method method = endpointInterface.lookupMethod(methodName);
    Assert.assertNotNull(method);
    return method;
  }

  private Interface getInterface(String interfaceName) {
    Interface endpointInterface = model.getSymbolTable().lookupInterface(interfaceName);
    Assert.assertNotNull(endpointInterface);
    return endpointInterface;
  }

  private void checkNoErrors() {
    if (model.getDiagReporter().getDiagCollector().hasErrors()) {
      Assert.fail(
          "Errors: " + Joiner.on("\n").join(model.getDiagReporter().getDiagCollector().getDiags()));
    }
    StageValidator.assertStages(ImmutableList.<Key<?>>of(Resolved.KEY, Merged.KEY), model);
  }

  private ImmutableSet<String> makeSet(String...strings) {
    return ImmutableSet.copyOf(strings);
  }

  private ImmutableSet<String> selectors(ImmutableList<FieldSelector> selectors) {
    return FluentIterable.from(selectors).transform(Functions.toStringFunction()).toSet();
  }
}
