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

import com.google.api.tools.framework.model.stages.Requires;
import com.google.api.tools.framework.model.stages.Resolved;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents a symbol table, an object mapping interfaces and types by name. It also maintains a
 * set of simple names of fields. Established by stage {@link Resolved}.
 *
 */
@Requires(Resolved.class)
@Immutable
public class SymbolTable {

  private final ImmutableMap<String, Interface> interfaceByName;
  private final ImmutableMap<String, TypeRef> typeByName;
  private final ImmutableSet<String> fieldNames;
  private final ImmutableSet<String> packageNames;
  private final ImmutableMap<String, ? extends List<Method>> methodsBySimpleName;

  public SymbolTable(Map<String, Interface> interfaceByName, Map<String, TypeRef> typeByName,
      Set<String> fieldNames, Map<String, ? extends List<Method>> methodsBySimpleName,
      Set<String> packageNames) {
    this.interfaceByName = ImmutableMap.copyOf(interfaceByName);
    this.typeByName = ImmutableMap.copyOf(typeByName);
    this.fieldNames = ImmutableSet.copyOf(fieldNames);
    this.methodsBySimpleName = ImmutableMap.copyOf(methodsBySimpleName);
    this.packageNames = ImmutableSet.copyOf(packageNames);
  }

  /**
   * Get the interface by its full name.
   */
  @Nullable
  public Interface lookupInterface(String fullName) {
    return interfaceByName.get(fullName);
  }

  /**
   * Resolves a interface by a partial name within a given package context, following PB (== C++)
   * conventions.
   */
  @Nullable
  public Interface resolveInterface(String inPackage, String name) {
    for (String cand : nameCandidates(inPackage, name)) {
      Interface endpointInterface = lookupInterface(cand);
      if (endpointInterface != null) {
        return endpointInterface;
      }
    }
    return null;
  }

  /**
   * Get the type by its full name.
   */
  @Nullable
  public TypeRef lookupType(String fullName) {
    return typeByName.get(getTypeNameInSymbolTable(fullName));
  }

  /**
   * Get the list of types that match the typeNamePattern and the kind. Pattern can be of the form
   * {@code foo.bar.*} to match any name which starts with the prefix {@code foo.bar}, or a fixed
   * name.
   */
  public List<TypeRef> lookupMatchingTypes(final String typeNamePattern, final Type kind) {
    if (Strings.isNullOrEmpty(typeNamePattern)) {
      return ImmutableList.of();
    }
    if (typeNamePattern.endsWith(".*")) {
      List<TypeRef> typeRefs =
          FluentIterable.from(getDeclaredTypes())
              .filter(new Predicate<TypeRef>() {
                @Override
                public boolean apply(TypeRef type) {
                  if (type.getKind() == kind) {
                    if (type.isMessage()) {
                      return type.getMessageType().getFullName().startsWith(
                          typeNamePattern.substring(0, typeNamePattern.length() - 1));
                    } else if (type.isEnum()) {
                      return type.getEnumType().getFullName().startsWith(
                          typeNamePattern.substring(0, typeNamePattern.length() - 1));
                    }
                    return false;
                  }
                  return false;
                }
              })
              .toList();
      return typeRefs;

    } else {
      TypeRef type = lookupType(typeNamePattern);
      if (type == null) {
        return ImmutableList.of();
      }
      if (type.getKind() != kind) {
        return ImmutableList.of();
      }

      return ImmutableList.of(type);
    }
  }

  /**
   * Returns the type name used to store in symbol table.
   *
   * <p>Message fullname starts with a '.' if no package is specified in the proto file.
   * Remove the preceding '.' to make it consistent with other types.
   */
  public static String getTypeNameInSymbolTable(String fullName) {
    return fullName = fullName.startsWith(".") ? fullName.substring(1) : fullName;
  }

  /**
   * Resolves a type by its partial name within a given package context, following PB (== C++)
   * conventions. If the given name is a builtin type name for a primitive type in the PB
   * language, a reference for that type will be returned.
   *
   * Note that this differs from the proto compiler in that it will continue searching if a
   * partial resolution fails; see resolveType2 for details.
   */
  @Nullable
  public TypeRef resolveType(String inPackage, String name) {
    TypeRef type = TypeRef.fromPrimitiveName(name);
    if (type != null) {
      return type;
    }
    for (String cand : nameCandidates(inPackage, name)) {
      type = lookupType(cand);
      if (type != null) {
        return type;
      }
    }
    return null;
  }

  /**
   * Resolves a type by its partial name within a given package context, following PB (== C++)
   * conventions. If the given name is a builtin type name for a primitive type in the PB
   * language, a reference for that type will be returned.
   *
   * This uses a stricter algorithm than resolveType, in that it fails to resolve if a partial
   * match fails, whereas resolveType keeps looking.
   *
   * For example, if there exist types:
   *   a.b.a.b.M.N
   *   a.b.J
   * and we try to resolve the type "b.J" in the package "a.b.a.b", then resolveType will
   * successfully resolve "b.J" to "a.b.J".
   *
   * In contrast, the proto compiler and resolveType2 will first perform the partial patch of "b" in
   * the package "a.b.a.b", which resolves to "a.b.a.b". The lookup "a.b.a.b.J" then fails.
   *
   * TODO (jgeiger): can resolveType be replaced safely by resolveType2?
   */
  @Nullable
  public TypeRef resolveType2(String inPackage, String name) {
    int firstDot = name.indexOf(".");
    String firstComponent;
    if (firstDot > 0) {
      firstComponent = name.substring(0, name.indexOf("."));
    } else {
      return resolveType(inPackage, name);
    }
    for (String cand : nameCandidates(inPackage, firstComponent)) {
      TypeRef outerType = lookupType(cand);
      if (outerType != null) {
        if (!outerType.isMessage()) {
          return null;
        }
        String outerTypeName = outerType.getMessageType().getFullName();
        int lastDot = outerTypeName.lastIndexOf(".");
        String fullType = lastDot > 0 ? outerTypeName.substring(0, lastDot) + "." + name : name;
        return lookupType(fullType);
      } else if (packageNames.contains(cand)) {
        int lastDot = cand.lastIndexOf(".");
        String fullType = lastDot > 0 ? cand.substring(0, lastDot) + "." + name : name;
        return lookupType(fullType);
      }
    }
    return null;
  }

  /**
   * Get all interfaces in the symbol table.
   */
  public ImmutableCollection<Interface> getInterfaces() {
    return interfaceByName.values();
  }

  /**
   * Get all declared types in the symbol table.
   */
  public ImmutableCollection<TypeRef> getDeclaredTypes() {
    return typeByName.values();
  }

  /**
   * Returns the candidates for name resolution of a name within a container(e.g. package, message,
   * enum, service elements) context following PB (== C++) conventions. Iterates those names which
   * shadow other names first; recognizes and removes a leading '.' for overriding shadowing. Given
   * a container name {@code a.b.c.M.N} and a type name {@code R.s}, this will deliver in order
   * {@code a.b.c.M.N.R.s, a.b.c.M.R.s, a.b.c.R.s, a.b.R.s, a.R.s, R.s}.
   */
  public static Iterable<String> nameCandidates(String inContainer, String name) {
    // TODO(user): we may want to make this a true lazy iterable for performance.
    if (name.startsWith(".")) {
      return FluentIterable.from(ImmutableList.of(name.substring(1)));
    }
    if (inContainer.length() == 0) {
      return FluentIterable.from(ImmutableList.of(name));
    } else {
      int i = inContainer.lastIndexOf('.');
      return FluentIterable.from(ImmutableList.of(inContainer + "." + name))
          .append(nameCandidates(i >= 0 ? inContainer.substring(0, i) : "", name));
    }
  }

  /**
   * Attempts to resolve the given id into a protocol element, applying certain heuristics.
   *
   * <p>First the name is attempted to interpret as a type or as an interface, in that order.
   * If that succeeds, the associated proto element is returned.
   *
   * <p>If resolution does not succeed, the last component of the name is chopped of, and
   * resolution is attempted recursively on the parent name. On success, the chopped name
   * is looked up in the parent depending on its type.
   */
  @Nullable public ProtoElement resolve(String id) {
    TypeRef type = lookupType(id);
    if (type != null) {
      if (type.isMessage()) {
        return type.getMessageType();
      }
      if (type.isEnum()) {
        return type.getEnumType();
      }
      throw new IllegalStateException("Unexpected type resolution.");
    }

    Interface iface = lookupInterface(id);
    if (iface != null) {
      return iface;
    }

    int i = id.lastIndexOf('.');
    if (i < 0) {
      return null;
    }
    String lastName = id.substring(i + 1);
    id = id.substring(0, i);
    ProtoElement parent = resolve(id);
    if (parent != null) {
      if (parent instanceof Interface) {
        return ((Interface) parent).lookupMethod(lastName);
      }
      if (parent instanceof MessageType) {
        return ((MessageType) parent).lookupField(lastName);
      }
      if (parent instanceof EnumType) {
        return ((EnumType) parent).lookupValue(lastName);
      }
    }
    return null;
  }

  public boolean containsFieldName(String fieldName) {
    return fieldNames.contains(fieldName);
  }

  @Nullable
  public List<Method> lookupMethodSimpleName(String methodName) {
    return methodsBySimpleName.get(methodName);
  }
}
