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

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.api.Service.Builder;
import com.google.api.tools.framework.aspects.RuleBasedConfigAspect;
import com.google.api.tools.framework.aspects.documentation.DocumentationConfigAspect;
import com.google.api.tools.framework.aspects.documentation.model.ResourceAttribute;
import com.google.api.tools.framework.aspects.http.model.CollectionAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.FieldSegment;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.MethodKind;
import com.google.api.tools.framework.aspects.http.model.HttpAttribute.PathSegment;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.FieldSelector;
import com.google.api.tools.framework.model.MessageType;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.TypeRef;
import com.google.api.tools.framework.model.TypeRef.WellKnownType;
import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Key;

import java.util.List;
import java.util.Set;

/**
 * Configuration aspect for the http binding.
 *
 * <p>The attribute used for representing the binding is {@link HttpAttribute}.
 */
public class HttpConfigAspect extends RuleBasedConfigAspect<HttpRule, HttpAttribute> {

  /**
   * A private key to store the RestAnalyzer with the model.
   */
  private static final Key<RestAnalyzer> REST_ANALYZER_KEY = Key.get(RestAnalyzer.class);

  private static final String BYTE_STREAM_API = "google.bytestream.ByteStream";

  /**
   * Creates http config aspect.
   */
  public static HttpConfigAspect create(Model model) {
    return new HttpConfigAspect(model);
  }

  private HttpConfigAspect(Model model) {
    super(model, HttpAttribute.KEY, "http", HttpRule.getDescriptor(),
        model.getServiceConfig().getHttp().getRulesList());
    registerLintRule(new HttpParameterReservedKeywordRule(this));
    RestAnalyzer.registerLintRuleNames(this);
  }

  /**
   * Depends on documentation aspect via consumption of {@link ResourceAttribute}.
   */
  @Override
  public List<Class<? extends ConfigAspect>> mergeDependencies() {
    return ImmutableList.<Class<? extends ConfigAspect>>of(DocumentationConfigAspect.class);
  }

  @Override
  public void startMerging() {
    // Attach the RestAnalyzer to the model so we can retrieve it during merging.
    getModel().putAttribute(REST_ANALYZER_KEY, new RestAnalyzer(this));
  }

  @Override
  protected boolean isApplicable(ProtoElement element) {
    return element instanceof Method;
  }

  @Override
  protected HttpRule fromIdlLayer(ProtoElement element) {
    Method method = (Method) element;
    HttpRule rule = method.getDescriptor().getMethodAnnotation(AnnotationsProto.http);
    if (rule != null && !rule.equals(HttpRule.getDefaultInstance())) {
      return rule;
    }
    return null;
  }

  @Override
  protected HttpAttribute evaluate(ProtoElement element, HttpRule rule, boolean isFromIdl) {
    return parseAndResolve((Method) element, rule, isFromIdl);
  }

  @Override
  public void endMerging() {
    Model model = getModel();

    // Attach the rest collections to the model.
    model.putAttribute(CollectionAttribute.KEY,
        model.getAttribute(REST_ANALYZER_KEY).finalizeAndGetCollections());
    model.removeAttribute(REST_ANALYZER_KEY);

    super.endMerging();
  }

  @Override
  protected void clearRuleBuilder(Builder builder) {
    builder.getHttpBuilder().clearRules();
  }

  @Override
  protected void addToRuleBuilder(Builder builder, String selector, HttpAttribute binding) {
    builder.getHttpBuilder().addRules(
        binding.getHttpRule().toBuilder().setSelector(selector).build());
  }

  // --------------------------------------------------------------------------------
  // Parsing and resolving of http config

  /**
   * Parse and resolve the http rule for the given method.
   */
  private HttpAttribute parseAndResolve(final Method method, HttpRule rule,
      final boolean isFromIdl) {
    // Construct the http mapping.
    List<HttpAttribute> additionalBindings =
        Lists.transform(rule.getAdditionalBindingsList(), new Function<HttpRule, HttpAttribute>() {
          @Override
          public HttpAttribute apply(HttpRule additionalRule) {
            HttpAttribute binding = constructBinding(method, additionalRule, isFromIdl,
                null, false);
            if (binding != null) {
              validateBinding(method, additionalRule, binding, true);
            }
            return binding;
          }
        });

    HttpAttribute binding = constructBinding(method, rule, isFromIdl, additionalBindings, true);
    if (binding != null) {
      validateBinding(method, rule, binding, false);
    }
    return binding;
  }

  private void validateBinding(Method method, HttpRule rule, HttpAttribute binding,
      boolean isAdditionalBinding) {
    // Resolve the http mapping.
    resolve(binding, method);

    // Check whether body is provided only where it is allowed.
    MethodKind kind = binding.getMethodKind();
    switch (kind) {
      case GET:
      case DELETE:
        if (!Strings.isNullOrEmpty(binding.getBody())) {
          error(method, "get/delete methods cannot have a body.");
        }
        break;
      case PATCH:
      case POST:
      case PUT:
        if (Strings.isNullOrEmpty(binding.getBody())) {
          warning(method, "POST/PATCH/PUT method for '%s' should specify a body.",
              method.getFullName());
        }
        break;
      default:
        break;
    }

    // Check for overlapping selectors.
    for (FieldSelector selector : binding.getPathSelectors()) {
      for (FieldSelector otherSelector : binding.getPathSelectors()) {
        if (selector != otherSelector && selector.isPrefixOf(otherSelector)) {
          error(method, "path contains overlapping field paths '%s' and '%s'.",
              selector, otherSelector);
        }
      }
    }

    // Check whether the response is not a WKT which does render as a non-object.
    if (kind != MethodKind.NONE) {
      if (!allowedAsHttpRequestResponse(TypeRef.of(method.getOutputMessage()).getWellKnownType())) {
        error(
            method,
            "type '%s' is not allowed as a response because it does not render as "
            + "a JSON object.",
            method.getOutputMessage().getFullName());
      } else if (!allowedAsRequestResponseInCodeGen(
                     TypeRef.of(method.getOutputMessage()).getWellKnownType())) {
        warning(
            method,
            "Codegen does not allow type '%s' as a response of a method. Please use other "
            + "message types for response, else client library generation will fail.",
            method.getOutputMessage().getFullName());
      }
    }

    if (isAdditionalBinding) {
      // Additional bindings must not specify more bindings or a selector.
      if (rule.getAdditionalBindingsCount() > 0) {
        error(method, "rules in additional_bindings must not specify additional_bindings");
      }
      if (!Strings.isNullOrEmpty(rule.getSelector())) {
        error(method, "rules in additional_bindings must not specify a selector");
      }
    }

    // Construct REST method.
    binding.setRestMethod(
        getModel().getAttribute(REST_ANALYZER_KEY).analyzeMethod(method, binding));
  }

  private HttpAttribute constructBinding(Method method, HttpRule rule, boolean isFromIdl,
      List<HttpAttribute> additionalBindings, boolean isPrimary) {
    // Extract the path and the method kind.
    MethodKind kind;
    String path;
    switch(rule.getPatternCase()) {
      case GET:
        kind = MethodKind.GET;
        path = rule.getGet();
        break;
      case PUT:
        kind = MethodKind.PUT;
        path = rule.getPut();
        break;
      case POST:
        kind = MethodKind.POST;
        path = rule.getPost();
        break;
      case DELETE:
        kind = MethodKind.DELETE;
        path = rule.getDelete();
        break;
      case PATCH:
        kind = MethodKind.PATCH;
        path = rule.getPatch();
        break;
      case CUSTOM:
        kind = MethodKind.NONE;
        path = rule.getCustom().getPath();
        break;
      default:
        error(method,
            "Http config must specify path for exactly one of get/put/post/delete/patch.");
        return null;
    }

    // Parse the path.
    ImmutableList<PathSegment> parsedPath = new HttpTemplateParser(
        asDiagCollector(), method.getLocation(),
        path, method.getModel().getConfigVersion()).parse();
    if (parsedPath == null) {
      return null;
    }

    // Construct the http mapping.
    return new HttpAttribute(rule,
        kind,
        method.getInputType().getMessageType(),
        parsedPath,
        rule.getBody().isEmpty() ? null : rule.getBody(),
        isFromIdl,
        additionalBindings != null ? ImmutableList.copyOf(additionalBindings)
            : ImmutableList.<HttpAttribute>of(),
        isPrimary);
  }
  /**
   * Resolves the http method config for the given method.
   */
  private void resolve(HttpAttribute binding, Method method) {
    // Walk over the path and resolve field paths. Remember any bound selectors.
    // while doing so.
    Set<FieldSelector> bound = Sets.newLinkedHashSet();
    resolve(method, bound, binding.getPath());

    // If a body field is provided, resolve it.
    ImmutableList.Builder<FieldSelector> bodyFields = ImmutableList.builder();
    if (binding.getBody() != null && !binding.bodyCapturesUnboundFields()) {
      FieldSelector bodyField = resolveFieldPath(method, binding.getBody());
      if (bodyField != null) {
        if (!bodyField.getType().isMessage() || bodyField.getType().isRepeated()
            || !allowedAsHttpRequestResponse(bodyField.getType().getWellKnownType())) {
          error(method, "body field path '%s' must be a non-repeated message.", bodyField);
        } else if (!allowedAsRequestResponseInCodeGen(bodyField.getType().getWellKnownType())) {
          warning(
              method,
              "Codegen does not allow type '%s' as a body field path. Please use other "
              + "types, else client library generation will fail.",
              bodyField.getType().toString());
        }
        bodyFields.add(bodyField);
        bound.add(bodyField);
      }
    }

    // Now compute all those field selectors not bound by path or body.
    Set<FieldSelector> unbound = Sets.newLinkedHashSet();
    computeUnbound(method.getInputType().getMessageType(), bound, FieldSelector.of(), unbound);

    // Resolve the http method config.
    if (binding.bodyCapturesUnboundFields()) {
      // All unbound fields are mapped to the body.
      binding.setFields(ImmutableList.<FieldSelector>of(), ImmutableList.copyOf(unbound));
    } else {
      // All unbound fields are mapped to parameters.
      checkHttpParameterConditions(method,
          FluentIterable.from(unbound).transform(new Function<FieldSelector, Field>() {
            @Override public Field apply(FieldSelector selector) {
              return selector.getFields().get(selector.getFields().size() - 1);
            }
          }),
          Sets.<MessageType>newHashSet());
      binding.setFields(ImmutableList.copyOf(unbound), bodyFields.build());
    }
  }

  /**
   * Returns whether the WKT can appear as request/response.
   */
  private boolean allowedAsHttpRequestResponse(WellKnownType wkt) {
      return wkt.allowedAsHttpRequestResponse();
  }

  /**
   * Returns whether the WKT can appear as request/response in generated client library.
   */
  private boolean allowedAsRequestResponseInCodeGen(WellKnownType wkt) {
    return wkt.allowedAsRequestResponseInCodeGen();
  }

  /**
   * Resolves field reference in a path, recursing into sub-paths.
   */
  private void resolve(Method method, Set<FieldSelector> bound, Iterable<PathSegment> path) {
    for (PathSegment seg : path) {
      if (seg instanceof FieldSegment) {
        FieldSegment fieldSeg = (FieldSegment) seg;
        FieldSelector selector = resolveFieldPath(method, fieldSeg.getFieldPath());
        if (selector != null) {
          checkPathParameterConditions(method, selector);
          fieldSeg.setFieldSelector(selector);
          bound.add(selector);
        }
        if (!fieldSeg.getSubPath().isEmpty()) {
          resolve(method, bound, fieldSeg.getSubPath());
        }
      }
    }
  }

  /**
   * Resolves a field path into a field selector.
   */
  private FieldSelector resolveFieldPath(Method method, String fieldPath) {
    FieldSelector result = FieldSelector.resolve(method.getInputType().getMessageType(),
        fieldPath);
    if (result == null) {
      error(method, "undefined field '%s' on message '%s'.",
          fieldPath, getInputMessageName(method));
    }
    return result;
  }

  /**
   * Checks context conditions for selectors bound to the HTTP path.
   */
  private void checkPathParameterConditions(Method method, FieldSelector selector) {
    TypeRef type = selector.getType();
    WellKnownType wkt = type.getWellKnownType();
    if (type.isMap()) {
      error(method, "map field not allowed: reached via '%s' on message '%s'.",
          selector.toString(), getInputMessageName(method));
    } else if (type.isRepeated()) {
      error(method, "repeated field not allowed: reached via '%s' on message '%s'.",
          selector, getInputMessageName(method));
    } else if (type.isMessage() && !wkt.allowedAsPathParameter()) {
      error(method, "message field not allowed: reached via '%s' on message '%s'.",
          selector, getInputMessageName(method));
    }
  }

  /**
   * Helper to access the full name of the input (request) message of a method.
   */
  private String getInputMessageName(Method method) {
    return method.getInputType().getMessageType().getFullName();
  }

  /**
   * Computes the field selectors not bound w.r.t. a given message and the set of unbound
   * selectors.
   */
  private void computeUnbound(MessageType message, Set<FieldSelector> bound,
      FieldSelector parent, Set<FieldSelector> unbound) {
    for (Field field : message.getFields()) {
      FieldSelector selector = parent.add(field);
      if (bound.contains(selector)) {
        // This field and all sub-fields are bound.
        continue;
      }
      boolean boundSubFields = false;
      if (selector.getType().isMessage()) {
        for (FieldSelector boundSelector : bound) {
          if (selector.isPrefixOf(boundSelector)) {
            // This field's message has some sub-fields which are bound. Recurse to discover the
            // unbound ones on the next level.
            boundSubFields = true;
            computeUnbound(selector.getType().getMessageType(), bound, selector, unbound);
            break;
          }
        }
      }
      if (!boundSubFields) {
        // This field as a whole marked as unbound.
        unbound.add(selector);
      }
    }
  }

  /**
   * Check context conditions on http parameters.
   */
  private void checkHttpParameterConditions(Method method,
      Iterable<Field> fields, Set<MessageType> visited) {
    for (Field field : fields) {
      checkHttpParameterConditions(method, field, visited);
    }
  }

  /**
   * Check context conditions on http parameters.
   */
  private void checkHttpParameterConditions(Method method, Field field, Set<MessageType> visited) {
    TypeRef type = field.getType();
    WellKnownType wkt = type.getWellKnownType();
    if (type.isMap()) {
      error(method,
          "map field '%s' referred to by message '%s' cannot be mapped as an HTTP parameter.",
          field.getFullName(), getInputMessageName(method));
      return;
    }

    if (type.isMessage()) {
      if (wkt.allowedAsHttpParameter()) {
        return;
      }
      if (!visited.add(type.getMessageType())) {
        error(method,
            "cyclic message field '%s' referred to by message '%s' cannot be mapped "
            + "as an HTTP parameter.",
            field.getFullName(), getInputMessageName(method));
        return;
      }
      if (type.isRepeated()) {
        error(method,
            "repeated message field '%s' referred to by message '%s' cannot be mapped "
            + "as an HTTP parameter.",
            field.getFullName(), getInputMessageName(method));
      }
      checkHttpParameterConditions(method, type.getMessageType().getFields(), visited);
      visited.remove(type.getMessageType());
    }
  }
}
