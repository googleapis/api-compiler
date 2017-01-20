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
import com.google.api.tools.framework.aspects.visibility.model.ScoperImpl;
import com.google.api.tools.framework.model.ConfigAspect;
import com.google.api.tools.framework.model.ConfigValidator;
import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.Element;
import com.google.api.tools.framework.model.Interface;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.Processor;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.TypeRef;
import com.google.api.tools.framework.model.Visitor;
import com.google.api.tools.framework.model.stages.Merged;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Key;
import com.google.protobuf.Api;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A processor which establishes the {@link Merged} stage, in which service config
 * and IDL are combined and validated.
 *
 * <p>The merger also derives interpreted values from the configuration, for example, the
 * {@link com.google.api.tools.framework.aspects.http.HttpConfigAspect},
 * and reports consistency errors encountered during interpretation.
 */
public class Merger implements Processor {

  private static final Pattern SELECTOR_PATTERN =
      Pattern.compile("^(\\w+(\\.\\w+)*(\\.\\*)?)$");

  @Override
  public ImmutableList<Key<?>> requires() {
    return ImmutableList.<Key<?>>of(Resolved.KEY);
  }

  @Override
  public Key<?> establishes() {
    return Merged.KEY;
  }

  @Override
  public boolean run(Model model) {
    int oldErrorCount = model.getDiagCollector().getErrorCount();

    if (model.getServiceConfig() == null) {
      // No service config defined; create a dummy one.
      model.setServiceConfig(Service.getDefaultInstance());
    }

    // Resolve apis, computing which parts of the model are included. Attach apis to interfaces.
    Service config = model.getServiceConfig();
    for (Api api : model.getServiceConfig().getApisList()) {
      Interface iface = model.getSymbolTable().lookupInterface(api.getName());
      Location location = model.getLocationInConfig(api, "name");
      if (iface != null) {
        // Add interface to the roots.
        model.addRoot(iface);
        // Attach api proto to interface.
        iface.setConfig(api);
      } else {
        model.getDiagCollector().addDiag(Diag.error(location,
            "Cannot resolve api '%s'.", api.getName()));
      }
    }

    List<Set<ConfigAspect>> orderedAspectGroup = sortForMerge(model.getConfigAspects());
    // Merge-in config aspects.
    for (Set<ConfigAspect> aspects : orderedAspectGroup) {
      for (ConfigAspect aspect : aspects) {
        aspect.startMerging();
      }
    }

    for (Set<ConfigAspect> aspects : orderedAspectGroup) {
      new ConfigAspectMerger(aspects).visit(model);
    }

    for (Set<ConfigAspect> aspects : orderedAspectGroup) {
      for (ConfigAspect aspect : aspects) {
        aspect.endMerging();
      }
    }

    runValidators(model);

    // Resolve types and enums specified in the service config as additional inclusions to
    // the tool chain, but not reachable from the service IDL, such as types associated with
    // Any type.
    for (com.google.protobuf.Type type : config.getTypesList()) {
      addAdditionalType(
          model, model.getLocationInConfig(type, "name"), type.getName(), Type.TYPE_MESSAGE);
    }
    for (com.google.protobuf.Enum enumType : config.getEnumsList()) {
      addAdditionalType(
          model, model.getLocationInConfig(enumType, "name"), enumType.getName(), Type.TYPE_ENUM);
    }

    // Set the initial scoper based on the roots. This will scope down further operation on the
    // model to those elements reachable via the roots.
    model.setScoper(ScoperImpl.create(model.getRoots()));

    if (oldErrorCount == model.getDiagCollector().getErrorCount()) {
      // No new errors produced -- success.
      model.putAttribute(Merged.KEY, new Merged());
      return true;
    }
    return false;
  }

  private void runValidators(Model model) {
    final List<ConfigValidator<? extends Element>> validators = model.getValidators();

    new Visitor() {
      @SuppressWarnings("unchecked")
      @VisitsBefore
      void validate(Element element) {
        final Class<?> elementType = element.getClass();
        Iterable<ConfigValidator<? extends Element>> validatorsToRun =
            getValidatorsToRun(validators, elementType);
        for (ConfigValidator<? extends Element> validator : validatorsToRun) {
          ConfigValidator<Element> castedValidator = (ConfigValidator<Element>) validator;
          castedValidator.run(element);
        }
      }
    }.visit(model);
  }

  private static FluentIterable<ConfigValidator<? extends Element>> getValidatorsToRun(
      List<ConfigValidator<? extends Element>> validators, final Class<?> elementType) {
    return FluentIterable.from(validators)
        .filter(
            new Predicate<ConfigValidator<? extends Element>>() {
              @Override
              public boolean apply(ConfigValidator<? extends Element> validator) {
                return validator.getElementClass().isAssignableFrom(elementType);
              }
            });
  }

  /**
   * Resolve the additional type specified besides those that can be reached transitively from
   * service definition. It resolves the typeName into a {@link TypeRef} object. If typeName ends
   * with wildcard ".*", all the {@link TypeRef}s that is under typeName pattern
   * path are added to the root.
   */
  private void addAdditionalType(
      Model model, Location location, final String typeName, final Type kind) {
    if (!SELECTOR_PATTERN.matcher(typeName).matches()) {
      model.getDiagCollector().addDiag(Diag.error(
          location,
          "Type selector '%s' specified in the config has bad syntax. "
          + "Valid format is \"<segment>('.' <segment>)*('.' '*')?\"",
          typeName));
      return;
    }

    List<TypeRef> typeRefs = model.getSymbolTable().lookupMatchingTypes(typeName, kind);

    if (typeRefs == null || typeRefs.isEmpty()) {
      model.getDiagCollector().addDiag(Diag.error(location,
          "Cannot resolve additional %s type '%s' specified in the config. Make"
          + " sure the name is right and its associated build target was included"
          + " in your protobuf build rule.",
          kind, typeName));
    } else {
      for (TypeRef typeRef : typeRefs) {
        if (typeRef.isMessage()) {
          model.addRoot(typeRef.getMessageType());
        } else if (typeRef.isEnum()) {
          model.addRoot(typeRef.getEnumType());
        }
      }
    }
  }

  private static class ConfigAspectMerger extends Visitor {

    private final Iterable<ConfigAspect> orderedAspects;

    private ConfigAspectMerger(Iterable<ConfigAspect> orderedAspects) {
      this.orderedAspects = orderedAspects;
    }

    @VisitsBefore void merge(ProtoElement element) {
      for (ConfigAspect aspect : orderedAspects) {
        aspect.merge(element);
      }
    }
  }

  /**
   * Returns the given config aspects as list of group of aspects in merge dependency order.
   * This performs a 'longest path layering' algorithm by placing aspects at different levels
   * (layers). First place all sink nodes at level-1 and then each node n is placed at level
   * level-p+1, where p is the longest path from n to sink. Aspects in each level are independent of
   * each other and can only depend on aspects in lower levels.
   * Detailed algorithm : 13.3.2 Layer Assignment Algorithms :
   * https://cs.brown.edu/~rt/gdhandbook/chapters/hierarchical.pdf
   */
  private static List<Set<ConfigAspect>> sortForMerge(Iterable<ConfigAspect> aspects) {
    Map<Class<? extends ConfigAspect>, ConfigAspect> aspectsByType =
        HashBiMap.create(Maps.toMap(
                             aspects, new Function<ConfigAspect, Class<? extends ConfigAspect>>() {
                               @Override
                               public Class<? extends ConfigAspect> apply(ConfigAspect aspect) {
                                 return aspect.getClass();
                               }
                             })).inverse();
    List<Class<? extends ConfigAspect>> visiting = Lists.newArrayList();
    Map<ConfigAspect, Integer> aspectsToLevel = Maps.newLinkedHashMap();
    for (ConfigAspect aspect : aspects) {
      assignLevelToAspect(aspect, aspectsByType, visiting, aspectsToLevel);
    }
    Map<Integer, Set<ConfigAspect>> aspectsByLevel = Maps.newLinkedHashMap();
    for (ConfigAspect aspect : aspectsToLevel.keySet()) {
      Integer aspectLevel = aspectsToLevel.get(aspect);
      if (!aspectsByLevel.containsKey(aspectLevel)) {
        aspectsByLevel.put(aspectLevel, Sets.<ConfigAspect>newLinkedHashSet());
      }
      aspectsByLevel.get(aspectLevel).add(aspect);
    }
    List<Set<ConfigAspect>> aspectListByLevels = Lists.newArrayList();
    for (int level = 1; level <= aspectsByLevel.size(); ++level) {
      aspectListByLevels.add(aspectsByLevel.get(level));
    }
    return aspectListByLevels;
  }

  /**
   * Does a DFS traversal and computes the maximum height (level) of each node from the sink node.
   */
  private static int assignLevelToAspect(ConfigAspect aspect,
      Map<Class<? extends ConfigAspect>, ConfigAspect> aspectsByType,
      List<Class<? extends ConfigAspect>> visiting, Map<ConfigAspect, Integer> aspectToLevel) {
    Class<? extends ConfigAspect> aspectType = aspect.getClass();
    if (aspectToLevel.containsKey(aspect)) {
      return aspectToLevel.get(aspect);
    }
    if (visiting.contains(aspectType)) {
      throw new IllegalStateException(
          String.format("Cyclic dependency between config aspect attributes. Cycle is: %s <- %s",
              aspectType, Joiner.on(" <- ").join(visiting)));
    }
    visiting.add(aspectType);
    Integer childMaxHeight = 0;
    for (Class<? extends ConfigAspect> dep : aspect.mergeDependencies()) {
      if (aspectsByType.containsKey(dep)) {
        Integer childHeight =
            assignLevelToAspect(aspectsByType.get(dep), aspectsByType, visiting, aspectToLevel);
        childMaxHeight = childHeight > childMaxHeight ? childHeight : childMaxHeight;
      } else {
        throw new IllegalStateException(
            String.format("config aspect %s depends on an unregistered aspect %s.",
                aspectType.getSimpleName(), dep.getSimpleName()));
      }
    }
    visiting.remove(aspectType);
    aspectToLevel.put(aspect, childMaxHeight + 1);
    return childMaxHeight + 1;
  }
}
