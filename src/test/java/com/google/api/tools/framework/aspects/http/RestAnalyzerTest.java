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

import com.google.api.HttpRule;
import com.google.api.Service;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.http.model.RestKind;
import com.google.api.tools.framework.aspects.http.model.RestMethod;
import com.google.api.tools.framework.model.ConfigSource;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.ExtensionPool;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoFile;
import com.google.api.tools.framework.model.SimpleLocation;
import com.google.api.tools.framework.model.testing.BaselineTestCase;
import com.google.api.tools.framework.model.testing.DiagUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.Empty;
import com.google.protobuf.UInt32Value;
import java.io.PrintWriter;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link RestAnalyzer}.
 */
@RunWith(JUnit4.class)

public class RestAnalyzerTest extends BaselineTestCase {

  private int configVersion = 1;

  @Test
  public void restifier() {
    restify(MethodKind.GET, "ListFruits", "/trees/{tree_id}/fruits");
    restify(MethodKind.GET, "GetFruit", "/trees/{tree_id}/fruits/{fruit_id}");
    restify(MethodKind.PUT, "UpdateFruit", "/trees/{tree_id}/fruits/{fruit_id}");
    restify(MethodKind.POST, "CreateFruit", "/trees/{tree_id}/fruits");
    restify(MethodKind.POST, "InsertFruit", "/trees/{tree_id}/fruits");
    restify(MethodKind.POST, "RipenFruit", "/trees/{tree_id}/fruits/{fruit_id}/ripen");
    restify(MethodKind.POST, "RipenFruit", "/trees/{tree_id}/fruits/{fruit_id}:ripen");
    restify(MethodKind.DELETE, "DeleteFruit", "/trees/{tree_id}/fruits/{fruit_id}");
    restify(MethodKind.PATCH, "PatchFruit", "/trees/{tree_id}/fruits/{fruit_id}");
    restify(MethodKind.PATCH, "PatchBranch", "/trees/{tree_id}/branch");
    restify(MethodKind.GET, "GetFruitDiameter", "/{fruit_id}/diameter");
    restify(MethodKind.GET, "ListTrees", "/trees");
    restify(MethodKind.GET, "GetTree", "/trees/{tree_id}");
    restify(MethodKind.PUT, "UpdateTree", "/trees/{tree_id}");
    restify(MethodKind.POST, "CreateTree", "/trees");
    restify(MethodKind.POST, "InsertTree", "/trees");
    restify(MethodKind.POST, "ShakeTree", "/trees/{tree_id}/shake");
    restify(MethodKind.POST, "ShakeTree", "/trees/{tree_id}:shake");
    restify(MethodKind.DELETE, "DeleteTree", "/trees/{tree_id}");
    restify(MethodKind.PATCH, "PatchTree", "/trees/{tree_id}");
    // Sub-resources
    restify(MethodKind.GET, "GetOrchardLocation", "/orchard/location");
    restify(MethodKind.PUT, "UpdateOrchardLocation", "/orchard/location");
    restify(MethodKind.GET, "GetOrchard", "/orchard");
    restify(MethodKind.PUT, "UpdateOrchard", "/orchard");
    // Custom get methods on a sub-resource.
    restify(MethodKind.GET, "FindTallestTree", "/orchard/tallestTree");
    // Custom get method - global resource.
    restify(MethodKind.GET, "FindTrees", "/findTrees");
    // Custom get method - resource
    restify(MethodKind.GET, "FindRipeFruit", "/trees/{tree_id}/findRipeFruit");
    restify(MethodKind.GET, "FindRipeFruit", "/trees/{tree_id}:findRipeFruit");
    // Custom get method - resource
    restify(MethodKind.GET, "FindWorm", "/trees/{tree_id}/fruits/{fruit_id}/findWorm");
    // Collection with idempotent create via put.
    restify(MethodKind.PUT, "CreateBush", "/bushes/{bush_id}");

    // Delete of a singleton
    restify(MethodKind.DELETE, "DeleteJenkins", "/projects/{project}/jenkins");

    // Top-level methods
    restify(MethodKind.GET, "GetFruit", "/v1:fruit");
    restify(MethodKind.PUT, "UpdateFruit", "/v1:fruit");
    restify(MethodKind.DELETE, "DeleteFruit", "/v1:fruit");
    restify(MethodKind.GET, "GetFruit", "");
    restify(MethodKind.GET, "GetFruit", "/");
    restify(MethodKind.GET, "GetFruit", "/:fruit");

    // A non-conforming method where the last segment is not a literal. Behaves different
    // in config version 1 and 2.
    restify(MethodKind.GET, "MethodNotStartingWithGet", "/projects/{project}");
    configVersion = 2;
    restify(MethodKind.GET, "MethodNotStartingWithGet", "/projects/{project}");

  }

  @Test
  public void customGet() {
    restify(MethodKind.GET, "GetSummaryResponse", "/customer:getSummary");
  }

  private void restify(MethodKind httpKind, String simpleName, String template) {
    Model model = Model.create(FileDescriptorSet.getDefaultInstance());
    model.setServiceConfig(
        ConfigSource.newBuilder(Service.getDefaultInstance())
            .setValue(
                Service.getDescriptor().findFieldByNumber(Service.CONFIG_VERSION_FIELD_NUMBER),
                null,
                UInt32Value.newBuilder().setValue(configVersion).build(),
                new SimpleLocation("from test"))
            .build());
    HttpConfigAspect aspect = HttpConfigAspect.create(model);
    ProtoFile file = ProtoFile.create(model, FileDescriptorProto.getDefaultInstance(), true,
        ExtensionPool.EMPTY);
    Interface iface = Interface.create(file, ServiceDescriptorProto.getDefaultInstance(), "");
    Method method = Method.create(iface,
        MethodDescriptorProto.newBuilder().setName(simpleName).build(), "");

    RestMethod restMethod;
    ImmutableList<PathSegment> path = parse(model, template);
    if (!model.getDiagCollector().getDiags().isEmpty()) {
      restMethod = RestMethod.create(method, RestKind.CUSTOM, "*error*", "*error*");
    } else {
      HttpAttribute httpConfig = new HttpAttribute(HttpRule.getDefaultInstance(),
          httpKind,
          MessageType.create(file, Empty.getDescriptor().toProto(), "", ExtensionPool.EMPTY),
          path, "", false,
          ImmutableList.<HttpAttribute>of(), false);
      RestAnalyzer analyzer = new RestAnalyzer(aspect);
      restMethod = analyzer.analyzeMethod(method, httpConfig);
    }

    PrintWriter pw = testOutput();
    pw.print(httpKind.toString());
    pw.print(" ");
    pw.print(simpleName);
    pw.print(" ");
    pw.print(template.isEmpty() ? "(empty)" : template);
    pw.println();
    pw.println(Strings.repeat("=", 70));
    pw.printf("Rest Kind:   %s\n", restMethod.getRestKind());
    pw.printf("Collection:  %s\n",
        restMethod.getRestCollectionName().isEmpty()
        ? "(empty)" : restMethod.getRestCollectionName());
    pw.printf("Custom Name: %s\n",
        restMethod.getRestKind() == RestKind.CUSTOM
        ? restMethod.getRestMethodName() : "(null)");

    List<Diag> diags = model.getDiagCollector().getDiags();
    if (diags.size() > 0) {
      pw.println("Diagnostics:");
      for (Diag d : diags) {
        pw.printf("  %s\n", DiagUtils.getDiagToPrint(d, true));
      }
    }
    pw.println();
  }

  private ImmutableList<PathSegment> parse(Model model, String path) {
    ImmutableList<PathSegment> segments =
        new HttpTemplateParser(model.getDiagCollector(), SimpleLocation.TOPLEVEL, path,
            configVersion).parse();
    return segments;
  }
}
