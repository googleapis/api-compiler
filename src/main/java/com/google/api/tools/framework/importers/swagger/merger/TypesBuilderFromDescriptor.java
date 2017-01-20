/*
 * Copyright (C) 2016 Google, Inc.
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

package com.google.api.tools.framework.importers.swagger.merger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.protobuf.BoolValue;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DoubleValue;
import com.google.protobuf.Empty;
import com.google.protobuf.Enum;
import com.google.protobuf.Field;
import com.google.protobuf.Field.Cardinality;
import com.google.protobuf.Field.Kind;
import com.google.protobuf.Int32Value;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.SourceContext;
import com.google.protobuf.StringValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Syntax;
import com.google.protobuf.Type;
import com.google.protobuf.Value;
import java.util.List;
import java.util.Map;

/**
 * Creates service config types and enums from a given descriptor.
 */
class TypesBuilderFromDescriptor {
  static final String TYPE_SERVICE_BASE_URL = "type.googleapis.com/";

  /**
   * Creates additional types (Value, Struct and ListValue) to be added to the Service config.
   * TODO (guptasu): Fix this hack. Find a better way to add the predefined types.
   * TODO (guptasu): Add them only when required and not in all cases.
   */

  static Iterable<Type> createAdditionalServiceTypes() {
    Map<String, DescriptorProto> additionalMessages = Maps.newHashMap();
    additionalMessages.put(Struct.getDescriptor().getFullName(),
        Struct.getDescriptor().toProto());
    additionalMessages.put(Value.getDescriptor().getFullName(),
        Value.getDescriptor().toProto());
    additionalMessages.put(ListValue.getDescriptor().getFullName(),
        ListValue.getDescriptor().toProto());
    additionalMessages.put(Empty.getDescriptor().getFullName(),
        Empty.getDescriptor().toProto());
    additionalMessages.put(Int32Value.getDescriptor().getFullName(),
        Int32Value.getDescriptor().toProto());
    additionalMessages.put(DoubleValue.getDescriptor().getFullName(),
        DoubleValue.getDescriptor().toProto());
    additionalMessages.put(BoolValue.getDescriptor().getFullName(),
        BoolValue.getDescriptor().toProto());
    additionalMessages.put(StringValue.getDescriptor().getFullName(),
        StringValue.getDescriptor().toProto());

    for (Descriptor descriptor : Struct.getDescriptor().getNestedTypes()) {
      additionalMessages.put(descriptor.getFullName(), descriptor.toProto());
    }

    // TODO (guptasu): Remove this hard coding. Without this, creation of Model from Service throws.
    // Needs investigation.
    String fileName = "struct.proto";
    List<Type> additionalTypes = Lists.newArrayList();
    for (String typeName : additionalMessages.keySet()) {
      additionalTypes.add(TypesBuilderFromDescriptor.createType(typeName,
          additionalMessages.get(typeName), fileName));
    }
    return additionalTypes;
  }

  /**
   * Creates additional Enums (NullValue) to be added to the Service config.
   */
  // TODO (guptasu): Fix this hack. Find a better way to add the predefined types.
  // TODO (guptasu): Add them only when required and not in all cases.
  static Iterable<Enum> createAdditionalServiceEnums() {
    // TODO (guptasu): Remove this hard coding. Without this, creation of Model from Service throws.
    // Needs investigation.
    String fileName = "struct.proto";
    List<Enum> additionalEnums = Lists.newArrayList();
    additionalEnums.add(TypesBuilderFromDescriptor.createEnum(NullValue.getDescriptor().getFullName(),
        NullValue.getDescriptor().toProto(), fileName));
    return additionalEnums;
  }

  /**
   * TODO (guptasu): Only needed to create NullValue enum.  Check if this can be removed.
   * Create the Protobuf.Enum instance from enumDescriptorProto.
   */
  private static Enum createEnum(String enumName,
      EnumDescriptorProto enumDescriptorProto, String fileName) {

    com.google.protobuf.Enum.Builder coreEnumBuilder =
        com.google.protobuf.Enum.newBuilder().setName(enumName);

    coreEnumBuilder.setSyntax(Syntax.SYNTAX_PROTO3);

    for (EnumValueDescriptorProto value : enumDescriptorProto.getValueList()) {
      com.google.protobuf.EnumValue.Builder coreEnumValueBuilder =
          com.google.protobuf.EnumValue.newBuilder();
      coreEnumValueBuilder.setName(value.getName()).setNumber(value.getNumber());
      coreEnumBuilder.addEnumvalue(coreEnumValueBuilder.build());
    }

    coreEnumBuilder.setSourceContext(SourceContext.newBuilder().setFileName(fileName));
    return coreEnumBuilder.build();
  }

  /**
   * TODO (guptasu): only needed to create hard coded Types (Struct, ListValue, and Value). Check
   * if this can be removed. Create the Protobuf.Type instance from descriptorProto.
   */
  private static Type createType(String typeName, DescriptorProto descriptorProto,
      String fileName) {
    Type.Builder coreTypeBuilder = Type.newBuilder().setName(typeName);

    int count = 1;
    for (FieldDescriptorProto fieldProto : descriptorProto.getFieldList()) {
      Field.Kind fieldKind = Field.Kind.valueOf(fieldProto.getType().getNumber());
      Cardinality cardinality = Cardinality.CARDINALITY_OPTIONAL;
      if (fieldProto.getLabel() == Label.LABEL_REPEATED) {
        cardinality = Cardinality.CARDINALITY_REPEATED;
      }
      Field.Builder coreFieldBuilder = Field
          .newBuilder()
          .setName(fieldProto.getName())
          .setNumber(count++)
          .setKind(fieldKind)
          .setCardinality(cardinality);
      if (fieldKind == Kind.TYPE_MESSAGE || fieldKind == Kind.TYPE_ENUM) {
        String typeFullName =
            fieldProto.getTypeName().startsWith(".") ? fieldProto.getTypeName().substring(1)
                : fieldProto.getTypeName();
        coreFieldBuilder.setTypeUrl(TYPE_SERVICE_BASE_URL + typeFullName);
      }
      coreTypeBuilder.addFields(coreFieldBuilder.build());
    }
    coreTypeBuilder.setSourceContext(SourceContext.newBuilder().setFileName(fileName));
    coreTypeBuilder.setSyntax(Syntax.SYNTAX_PROTO3);
    return coreTypeBuilder.build();
  }
}