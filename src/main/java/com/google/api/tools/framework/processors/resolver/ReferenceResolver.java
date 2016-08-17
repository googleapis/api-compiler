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

package com.google.api.tools.framework.processors.resolver;

import com.google.api.tools.framework.model.Diag;
import com.google.api.tools.framework.model.EnumType;
import com.google.api.tools.framework.model.EnumValue;
import com.google.api.tools.framework.model.Field;
import com.google.api.tools.framework.model.Location;
import com.google.api.tools.framework.model.Method;
import com.google.api.tools.framework.model.Model;
import com.google.api.tools.framework.model.ProtoContainerElement;
import com.google.api.tools.framework.model.ProtoElement;
import com.google.api.tools.framework.model.SymbolTable;
import com.google.api.tools.framework.model.TypeRef;
import com.google.api.tools.framework.model.Visitor;
import com.google.api.tools.framework.util.Visits;
import com.google.api.tools.framework.util.VisitsBefore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Queues;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Visitor which resolves type references.
 */
class ReferenceResolver extends Visitor {

  private final Model model;
  private final SymbolTable symbolTable;

  // Represents a stack of namespaces, with the top element the most active one.
  // Namespace here either means the package (on file level) or the package
  // plus an enclosing number of messages. This is used for resolution of partial names.
  private final Deque<String> namespaces = Queues.newArrayDeque();
  private final Set<String> foundOptionTypes = new HashSet<>();

  ReferenceResolver(Model model, SymbolTable symbolTable) {
    this.model = model;
    this.symbolTable = symbolTable;
  }

  void run() {
    visit(model);
    // Make sure any internal options/extensions we find get added into the model traversals.
    addExtraExtensionTypes();
  }

  private static final Set<String> WHITELISTED_EXTENSION_TYPES = ImmutableSet.of(
 );

  private static boolean isWhitelistedExtensionType(String type) {
    for (String whitelistedType : WHITELISTED_EXTENSION_TYPES) {
      if (whitelistedType.equals(type)) {
        return true;
      }
      if (whitelistedType.endsWith(".") && type.startsWith(whitelistedType)) {
        return true;
      }
    }
    return false;
  }

  private void addExtraExtensionTypes() {
    for (String optionType : foundOptionTypes) {
      if (isWhitelistedExtensionType(optionType)) {
        TypeRef typeRef = symbolTable.lookupType(optionType);
        if (null != typeRef) {
          ProtoElement element = null;
          if (typeRef.isMessage()) {
            element = typeRef.getMessageType();
          } else if (typeRef.isEnum()) {
            element = typeRef.getEnumType();
          }
          if (null != element) {
            model.addRoot(element);
          }
        }
      }
    }
  }

  @Visits
  void visit(ProtoContainerElement container) {
    namespaces.push(container.getFullName());
    accept(container);
    namespaces.pop();
  }

  @VisitsBefore
  void visit(ProtoElement element) {
    findOptionTypes(element.getOptionFields());
  }

  @VisitsBefore
  void visit(Field field) {
    // Resolve type of this field.
    TypeRef type = resolveType(field.getLocation(), field.getProto().getType(),
        field.getProto().getTypeName());
    if (type != null) {
      if (field.isRepeated()) {
        type = type.makeRepeated();
      } else if (!field.isOptional()) {
        type = type.makeRequired();
      }
      field.setType(type);
    }

    // Check for resolution of oneof.
    if (field.getProto().hasOneofIndex() && field.getOneof() == null) {
      // Indicates the oneof index could not be resolved.
      model.getDiagCollector().addDiag(Diag.error(field.getLocation(),
          "Unresolved oneof reference (indicates internal inconsistency of input; oneof index: %s)",
          field.getProto().getOneofIndex()));
    }

    findOptionTypes(field.getOptionFields());
  }

  @VisitsBefore
  void visit(Method method) {
    // Resolve input and output type of this method.
    TypeRef inputType = resolveType(method.getLocation(),
        FieldDescriptorProto.Type.TYPE_MESSAGE, method.getDescriptor().getInputTypeName());
    if (inputType != null) {
      method.setInputType(inputType);
    }
    TypeRef outputType = resolveType(method.getLocation(),
        FieldDescriptorProto.Type.TYPE_MESSAGE, method.getDescriptor().getOutputTypeName());
    if (outputType != null) {
      method.setOutputType(outputType);
    }
    findOptionTypes(method.getOptionFields());
  }

  @VisitsBefore
  void visit(EnumValue value) {
    // The type is build from the parent, which must be an enum type.
    value.setType(TypeRef.of((EnumType) value.getParent()));
    findOptionTypes(value.getProto().getOptions().getAllFields());
  }

  private void findOptionTypes(Map<FieldDescriptor, Object> extensionFields) {
    for (Map.Entry<FieldDescriptor, Object> entry : extensionFields.entrySet()) {
      Object value = entry.getValue();
      // Could probably look for other types of options (e.g. enums), but we don't care about any
      // of them for now.
      if (value instanceof Message) {
        Message optionMessage = (Message) value;
        foundOptionTypes.add(optionMessage.getDescriptorForType().getFullName());
      }
    }
  }

  /**
   * Resolves a type based on the given partial name. This does not assume that the name, as
   * obtained from the descriptor, is in absolute form.
   */
  private TypeRef resolveType(Location location, FieldDescriptorProto.Type kind, String name) {
    TypeRef type;
    switch (kind) {
      case TYPE_MESSAGE:
      case TYPE_ENUM:
      case TYPE_GROUP:
        type = symbolTable.resolveType(namespaces.peek(), name);
        break;
      default:
        type = TypeRef.of(kind);
    }
    if (type == null) {
      model.getDiagCollector().addDiag(Diag.error(location, "Unresolved type '%s'", name));
    }
    return type;
  }
}
