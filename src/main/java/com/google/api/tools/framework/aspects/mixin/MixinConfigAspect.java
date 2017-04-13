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

package com.google.api.tools.framework.aspects.mixin;

import static com.google.common.base.CharMatcher.whitespace;

import com.google.api.tools.framework.aspects.ConfigAspectBase;
import com.google.api.tools.framework.aspects.documentation.DocumentationConfigAspect;
import com.google.api.tools.framework.aspects.documentation.model.DocumentationUtil;
import com.google.api.tools.framework.aspects.documentation.model.ElementDocumentationAttribute;
import com.google.api.tools.framework.aspects.http.HttpConfigAspect;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.MethodKind;
import com.google.api.tools.framework.aspects.mixin.model.ImplementsAttribute;
import com.google.api.tools.framework.aspects.mixin.model.MixinAttribute;
import com.google.api.tools.framework.aspects.versioning.VersionConfigAspect;
import com.google.api.tools.framework.aspects.versioning.model.VersionAttribute;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.DiagReporter.LocationContext;
import com.google.api.tools.framework.model.DiagReporter.MessageLocationContext;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.protobuf.Api;
import com.google.protobuf.Mixin;
import java.util.List;

/** Configuration aspect for mixins. */
public class MixinConfigAspect extends ConfigAspectBase {

  /** Creates mixin config aspect. */
  public static ConfigAspectBase create(Model model) {
    return new MixinConfigAspect(model);
  }

  private MixinConfigAspect(Model model) {
    super(model, "mixin");
  }

  /**
   * Returns dependencies. The attributes belonging to those aspects are used by this aspect during
   * merging phase.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.<Class<? extends ConfigAspect>>of(
        VersionConfigAspect.class,
        HttpConfigAspect.class,
        DocumentationConfigAspect.class);
  }

  /**
   * Analyze any mixins and attach attributes related to interfaces and methods which are used by
   * the main merging phase.
   */
  @Override
  public void startMerging() {
    for (Api api : getModel().getServiceConfig().getApisList()) {
      // Resolve the including interface.
      Interface including =
          resolveInterface(
              api.getName(), MessageLocationContext.create(api, Api.NAME_FIELD_NUMBER));

      // Process each mixin declaration.
      for (Mixin mixin : api.getMixinsList()) {
        Interface included =
            resolveInterface(
                mixin.getName(), MessageLocationContext.create(mixin, Mixin.NAME_FIELD_NUMBER));
        if (including == null || included == null) {
          // Errors have been reported.
          continue;
        }

        // Attach the mixin attribute.
        MixinAttribute mixinAttr = MixinAttribute.create(included, mixin);
        including.addAttribute(MixinAttribute.KEY, mixinAttr);

        // Process each method in the included interface.
        for (Method method : included.getMethods()) {
          startMergeMethod(including, method);
        }
      }
    }
  }

  private void startMergeMethod(Interface including, Method method) {
    // Check conditions implied by mixin configuration.
    Method redeclared = including.lookupMethod(method.getSimpleName());
    if (redeclared == null) {
      error(
          including.getLocation(),
          "The API '%s' does not redeclare method '%s' as required by the mixin configuration.",
          including.getFullName(),
          method);
      return;
    }
    if (!method.getInputType().equals(redeclared.getInputType())) {
      error(
          redeclared.getLocation(),
          "The method '%s' must have request type '%s' as required by the mixin configuration.",
          redeclared,
          method.getInputMessage());
      return;
    }
    if (!method.getOutputType().equals(redeclared.getOutputType())) {
      error(
          redeclared.getLocation(),
          "The method '%s' must have response type '%s' as required by the mixin configuration.",
          redeclared,
          method.getOutputType());
      return;
    }

    // Add attribute.
    redeclared.addAttribute(ImplementsAttribute.KEY, ImplementsAttribute.create(method));
  }

  @Override
  public void merge(ProtoElement elem) {
    if (!(elem instanceof Method)) {
      return;
    }

    // Use the first implemented method to derive properties.
    ImplementsAttribute attrib =
        elem.hasAttribute(ImplementsAttribute.KEY)
            ? elem.getAttribute(ImplementsAttribute.KEY).get(0)
            : null;
    if (attrib == null) {
      return;
    }
    Method method = (Method) elem;
    deriveDoc(method, attrib.method());
    deriveHttp(method, attrib.method());
  }

  private void deriveDoc(Method redeclared, Method method) {
    String doc = DocumentationUtil.getScopedDescription(redeclared);
    if (!whitespace().matchesAllOf(Strings.nullToEmpty((doc)))) {
      // Don't derive as it is overridden.
      return;
    }
    ElementDocumentationAttribute sourceAttrib =
        method.getAttribute(ElementDocumentationAttribute.KEY);
    if (sourceAttrib != null) {
      redeclared.putAttribute(ElementDocumentationAttribute.KEY, sourceAttrib);
    }
  }

  private void deriveHttp(Method redeclared, Method method) {
    HttpAttribute attrib = redeclared.getAttribute(HttpAttribute.KEY);
    if (attrib != null && attrib.getMethodKind() != MethodKind.NONE) {
      // Don't derive as it is overridden.
      return;
    }
    HttpAttribute sourceAttrib = method.getAttribute(HttpAttribute.KEY);
    if (sourceAttrib != null && sourceAttrib.getMethodKind() != MethodKind.NONE) {
      // Compute the root of the http binding in the mixin context, using the version
      // of the interface and config provided in the mixin declaration.
      String effectiveRoot = "";
      String effectiveVersion = "v1";
      VersionAttribute versionAttrib = redeclared.getParent().getAttribute(VersionAttribute.KEY);
      if (versionAttrib != null
          && !whitespace().matchesAllOf(Strings.nullToEmpty(versionAttrib.majorVersion()))) {
        effectiveRoot = effectiveVersion = versionAttrib.majorVersion();
      }

      // Get the configured root from the attribute of the interface parent, and append it.
      String configuredRoot =
          redeclared.getParent().getAttribute(MixinAttribute.KEY).get(0).config().getRoot();
      if (!whitespace().matchesAllOf(Strings.nullToEmpty(configuredRoot))) {
        if (!effectiveRoot.isEmpty()) {
          effectiveRoot = effectiveRoot + "/";
        }
        effectiveRoot = effectiveRoot + configuredRoot;
      }

      // Set the derived attribute and version.
      if (!whitespace().matchesAllOf(Strings.nullToEmpty(effectiveRoot))) {
        redeclared.putAttribute(HttpAttribute.KEY, sourceAttrib.reroot(effectiveRoot));
        redeclared.putAttribute(VersionAttribute.KEY, VersionAttribute.create(effectiveVersion));
      }
    }
  }

  private Interface resolveInterface(String name, LocationContext location) {
    Interface iface = getModel().getSymbolTable().resolveInterface("", name);
    if (iface == null) {
      error(location, "The API '%s' cannot be resolved.", name);
    }
    return iface;
  }
}
